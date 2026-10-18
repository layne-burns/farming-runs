/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.pathing.ShortestPathClient;
import com.layneburns.farmingruns.state.PlayerInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/**
 * Runs a farming run (SPEC §2.5–2.6): orders the stops from where you are ({@link RoutePlanner}), points Shortest
 * Path at the current stop, and ticks patches off as their varbits show them done ({@link PatchProgress}) and, where
 * the gardener is to be paid, when the game says you paid ({@link GuidanceTracker}), moving on when a stop is
 * finished. Farming varbits are shared between regions, so a patch is only read while you're in its
 * region. The thinking is in the pure classes; this is the glue. Client thread, except where noted.
 */
@Slf4j
@Singleton
public class RunController
{
	private final Client client;
	private final ClientThread clientThread;
	private final GameData data;
	private final RunSession session;
	private final ShortestPathClient shortestPath;
	private final FarmingRunsConfig config;
	private final PlayerInfo player;
	private final GuidanceTracker guidance;
	/** The region you were in last tick, to notice arriving somewhere. */
	private int lastRegion = -1;

	@Inject
	RunController(Client client, ClientThread clientThread, GameData data, RunSession session,
		ShortestPathClient shortestPath, FarmingRunsConfig config,
		PlayerInfo player, GuidanceTracker guidance)
	{
		this.guidance = guidance;
		this.client = client;
		this.clientThread = clientThread;
		this.data = data;
		this.session = session;
		this.shortestPath = shortestPath;
		this.config = config;
		this.player = player;
		// Payments are seen elsewhere; whenever the run moves on, point Shortest Path at where it's up to.
		session.addListener(() -> clientThread.invokeLater(this::point));
	}

	/** Starts a run of this plan. Any thread. */
	public void start(RunSetup setup, PlantingPlan plan, Map<String, TeleportChoice> teleports)
	{
		clientThread.invoke(() ->
		{
			Map<String, List<PlantingPlan.Assignment>> byLocation = new LinkedHashMap<>();
			plan.getAssignments().forEach(a -> byLocation.computeIfAbsent(a.getPatch().getLocationId(),
				k -> new ArrayList<>()).add(a));
			String here = locationHere(byLocation.keySet());
			List<String> order = RoutePlanner.order(here, byLocation.keySet(), data.getCosts()::cost);
			List<RunProgress.Stop> stops = new ArrayList<>();
			order.forEach(id -> stops.add(new RunProgress.Stop(id, List.copyOf(byLocation.get(id)))));
			log.debug("[farmrun] route from {}: {}", here, order);
			session.start(new RunSession.ActiveRun(setup, plan, teleports, System.currentTimeMillis(),
				RunProgress.start(stops, payFor(setup, plan), compostFor(setup, plan))));
			checkPatchesHere();
			point();
		});
	}

	/** Leaves a stop out. Any thread. */
	public void skip(String locationId)
	{
		clientThread.invoke(() ->
		{
			session.update(p -> p.skip(locationId));
			point();
		});
	}

	/** Goes to a stop next. Any thread. */
	public void goTo(String locationId)
	{
		clientThread.invoke(() ->
		{
			session.update(p -> p.goTo(locationId));
			point();
		});
	}

	/** You planted (or picked, or cleared) a patch the plugin didn't see. Any thread. */
	public void markWorked(String patchId)
	{
		clientThread.invoke(() -> session.update(p -> p.markWorked(patchId)));
	}

	/** You composted a patch, or chose not to. Any thread. */
	public void markComposted(String patchId)
	{
		clientThread.invoke(() -> guidance.markComposted(patchId));
	}

	/** You paid for a patch (or chose not to). Any thread. */
	public void markPaid(String patchId)
	{
		clientThread.invoke(() -> session.update(p -> p.markPaid(patchId)));
	}

	/** Ends the run and removes the path. Any thread. */
	public void stop()
	{
		clientThread.invoke(() ->
		{
			session.stop();
			shortestPath.clear();
		});
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		RunSession.ActiveRun run = session.active();
		if (run == null)
		{
			return;
		}
		for (RunProgress.Stop stop : run.getProgress().getStops())
		{
			for (PlantingPlan.Assignment a : stop.getPatches())
			{
				if (a.getPatch().getVarbitId() == event.getVarbitId())
				{
					check(a);
				}
			}
		}
		point();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && session.active() != null)
		{
			// Shortest Path may have dropped the path on a logout or hop.
			shortestPath.resend();
			point();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player player = client.getLocalPlayer();
		if (session.active() == null || player == null)
		{
			return;
		}
		int region = player.getWorldLocation().getRegionID();
		if (region != lastRegion)
		{
			// Arrived somewhere: patches here may already be done (varbits don't "change" on arrival).
			lastRegion = region;
			checkPatchesHere();
			point();
		}
	}

	/** Reads every run patch in the region you're in. */
	private void checkPatchesHere()
	{
		RunSession.ActiveRun run = session.active();
		if (run == null)
		{
			return;
		}
		run.getProgress().getStops().forEach(stop -> stop.getPatches().forEach(this::check));
	}

	/** Marks a patch done if you're in its region and its varbit says so. */
	private void check(PlantingPlan.Assignment a)
	{
		Patch patch = a.getPatch();
		if (!isHere(patch))
		{
			return;
		}
		int value = client.getVarbitValue(patch.getVarbitId());
		if (PatchProgress.isDone(a, patch.getKind().forVarbitValue(value)))
		{
			session.update(p -> p.markWorked(patch.getId()));
		}
	}

	/** Points Shortest Path at the current stop, or clears it and says so when the run is finished. */
	private void point()
	{
		RunSession.ActiveRun run = session.active();
		if (run == null)
		{
			return;
		}
		RunProgress.Stop stop = run.getProgress().currentStop();
		if (stop != null)
		{
			shortestPath.target(targets(stop, run.getProgress()), config.pathCarriedOnly());
			return;
		}
		if (shortestPath.lastTarget() != null)
		{
			shortestPath.clear();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Farming run complete.", null);
			log.debug("[farmrun] finished");
		}
	}

	/**
	 * Where to head at a stop: the patches there still to do (Shortest Path picks the nearest), so a big place like the
	 * Farming Guild leads to the right patch. Each patch's stand tile, beside it, since the patch itself can't be walked
	 * on. The location's own target when no patch has one.
	 */
	private Set<WorldPoint> targets(RunProgress.Stop stop, RunProgress progress)
	{
		Set<WorldPoint> tiles = new HashSet<>();
		for (PlantingPlan.Assignment a : stop.getPatches())
		{
			if (!progress.isPatchDone(a.getPatch().getId()) && a.getPatch().getStand() != null)
			{
				tiles.add(a.getPatch().getStand());
			}
		}
		return tiles.isEmpty() ? Set.of(data.location(stop.getLocationId()).getTarget()) : tiles;
	}

	/**
	 * Patches to be treated with compost before the stop counts as done: those being planted, when the compost is one
	 * the game reports ("You treat the ... with supercompost"). Fertile Soil can't be seen, so it never holds a stop up.
	 */
	private static Set<String> compostFor(RunSetup setup, PlantingPlan plan)
	{
		CompostChoice compost = setup.getCompost();
		if (compost == CompostChoice.NONE || compost == CompostChoice.FERTILE_SOIL)
		{
			return Set.of();
		}
		return plan.getAssignments().stream().filter(a -> a.getSeed() != null).map(a -> a.getPatch().getId())
			.collect(Collectors.toSet());
	}

	/** Patches the gardener is to be paid to protect ({@link Protection}). */
	private Set<String> payFor(RunSetup setup, PlantingPlan plan)
	{
		return plan.getAssignments().stream()
			.filter(a -> Protection.shouldPay(a, setup, data, player.getFacts()))
			.map(a -> a.getPatch().getId())
			.collect(Collectors.toSet());
	}

	/** True when you're in the patch's region (or one its location also covers). */
	private boolean isHere(Patch patch)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return false;
		}
		int region = player.getWorldLocation().getRegionID();
		return region == patch.getRegionId() || data.location(patch.getLocationId()).getExtraRegionIds().contains(region);
	}

	/** The run location you're standing at, if any (so it goes first); null when you're elsewhere. */
	@Nullable
	private String locationHere(Iterable<String> stops)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		int region = player.getWorldLocation().getRegionID();
		for (String id : stops)
		{
			Location location = data.location(id);
			if (location.getRegionId() == region || location.getExtraRegionIds().contains(region))
			{
				return id;
			}
		}
		return null;
	}
}
