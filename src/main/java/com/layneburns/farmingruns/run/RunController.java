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
import com.layneburns.farmingruns.data.TeleportType;
import com.layneburns.farmingruns.pathing.ShortestPathClient;
import com.layneburns.farmingruns.state.PatchStates;
import com.layneburns.farmingruns.state.PatchStatus;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.vendor.timetracking.PatchState;
import java.util.ArrayList;
import java.util.HashMap;
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
	private final PatchStates states;
	/** Each run patch's last reading in the region you're in, to tick patches on changes. Client thread. */
	private final Map<String, PatchState> seen = new HashMap<>();
	/** The stop whose fairy ring leg goes through your house ({@link FairyRingRoute}), while on it. Client thread. */
	@Nullable
	private String heldForHouse;
	private final FairyRingRoute fairyRings;
	private final LegTimer legs;
	private final CompostBinPlanner compostBins;
	/** The current stop already checked for being reached too early ({@link GrowingWait}). Client thread. */
	@Nullable
	private String earlyCheckedFor;
	private final GrowingWait growingWait;
	/** The region you were in last tick, to notice arriving somewhere. */
	private int lastRegion = -1;

	@Inject
	RunController(Client client, ClientThread clientThread, GameData data, RunSession session,
		ShortestPathClient shortestPath, FarmingRunsConfig config,
		PlayerInfo player, GuidanceTracker guidance, PatchStates states, PatchClicks clicks, GrowingWait growingWait,
		FairyRingRoute fairyRings, LegTimer legs, CompostBinPlanner compostBins)
	{
		this.compostBins = compostBins;
		this.fairyRings = fairyRings;
		this.legs = legs;
		this.growingWait = growingWait;
		this.states = states;
		clicks.addListener(this::follow);
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

	/**
	 * Starts a run of this plan. Any thread.
	 *
	 * @param waitFor patches still growing that the run should get to only once they're ready, by id
	 */
	public void start(RunSetup setup, PlantingPlan plan, Map<String, TeleportChoice> teleports, Set<String> waitFor)
	{
		clientThread.invoke(() ->
		{
			Map<String, List<PlantingPlan.Assignment>> byLocation = new LinkedHashMap<>();
			plan.getAssignments().forEach(a -> byLocation.computeIfAbsent(a.getPatch().getLocationId(),
				k -> new ArrayList<>()).add(a));
			// Compost bins at these stops that need collecting or filling join their stop.
			Set<String> harvest = new HashSet<>(harvestFirst(plan));
			compostBins.work(byLocation.keySet()).forEach((bin, work) ->
			{
				byLocation.get(bin.getLocationId()).add(new PlantingPlan.Assignment(bin, null, false));
				if (work.getCollect() > 0)
				{
					harvest.add(bin.getId());
				}
			});
			String here = locationHere(byLocation.keySet());
			Map<String, List<Patch>> patches = new LinkedHashMap<>();
			byLocation.forEach((id, list) -> patches.put(id, list.stream().map(PlantingPlan.Assignment::getPatch)
				.collect(Collectors.toList())));
			List<String> order = RoutePlanner.order(here, byLocation.keySet(), data.getCosts()::cost,
				growingWait.timing(patches, waitFor));
			List<RunProgress.Stop> stops = new ArrayList<>();
			order.forEach(id -> stops.add(new RunProgress.Stop(id, List.copyOf(byLocation.get(id)))));
			log.debug("[farmrun] route from {}: {}", here, order);
			earlyCheckedFor = null;
			session.start(new RunSession.ActiveRun(setup, plan, teleports, System.currentTimeMillis(),
				RunProgress.start(stops, payFor(setup, plan), compostFor(setup, plan), harvest),
				Set.copyOf(waitFor)));
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
			// Your choice: don't move it later even if it isn't ready yet.
			earlyCheckedFor = locationId;
			session.update(p -> p.goTo(locationId));
			point();
		});
	}

	/** You planted (or picked, or cleared) a patch the plugin didn't see. Any thread. */
	public void markWorked(String patchId)
	{
		clientThread.invoke(() -> session.update(p -> p.markWorked(patchId)));
	}

	/** You harvested a patch's crop (or chose not to). Any thread. */
	public void markHarvested(String patchId)
	{
		clientThread.invoke(() -> session.update(p -> p.markHarvested(patchId)));
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

	/**
	 * You started on a stop out of order (your first click on its patch, gardener or leprechaun): it becomes the
	 * current stop, and the stops still to do are re-ordered from there. Client thread.
	 */
	private void follow(PatchClicks.Click click)
	{
		RunSession.ActiveRun run = session.active();
		if (run == null)
		{
			return;
		}
		RunProgress progress = run.getProgress();
		RunProgress.Stop current = progress.currentStop();
		RunProgress.Stop clicked = progress.getStops().stream()
			.filter(s -> s.getLocationId().equals(click.getLocationId())).findFirst().orElse(null);
		if (clicked == null || clicked == current || progress.isDone(clicked))
		{
			return;
		}
		List<String> rest = progress.getStops().stream()
			.filter(s -> !progress.isDone(s) && !progress.isSkipped(s) && s != clicked)
			.map(RunProgress.Stop::getLocationId).collect(Collectors.toList());
		rest.add(0, clicked.getLocationId());
		List<String> order = RoutePlanner.order(clicked.getLocationId(), rest, data.getCosts()::cost);
		log.debug("[farmrun] you went to {} first; route now {}", clicked.getLocationId(), order);
		earlyCheckedFor = clicked.getLocationId();
		session.update(p -> p.reroute(order));
		point();
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
					check(a, false);
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
			if (heldForHouse != null && legs.hasLeft())
			{
				// On a fairy ring leg through your house: path afresh from wherever the ring set you down.
				shortestPath.clear();
			}
			point();
		}
		else if (heldForHouse != null && legs.hasLeft() && shortestPath.lastTarget() == null)
		{
			// You've teleported home: Shortest Path takes over.
			point();
		}
		RunProgress.Stop current = session.active().getProgress().currentStop();
		if (heldForHouse != null && (current == null || !current.getLocationId().equals(heldForHouse)))
		{
			heldForHouse = null;
		}
	}

	/**
	 * Arrived somewhere: notes how each run patch here looks now, as the starting point for changes. Nothing is ticked
	 * from this first look, because nearby patches share varbits and the game may still be sending the last place's
	 * value (Lumbridge hops read as planted from the Champions' Guild bush just planted).
	 */
	private void checkPatchesHere()
	{
		RunSession.ActiveRun run = session.active();
		seen.clear();
		if (run == null)
		{
			return;
		}
		run.getProgress().getStops().forEach(stop -> stop.getPatches().forEach(a -> check(a, true)));
	}

	/**
	 * Reads a patch you're in the region of. A change from the last reading ticks it: harvested (it was grown and
	 * isn't), and done (planted, picked bare or cleared) when it wasn't before.
	 */
	private void check(PlantingPlan.Assignment a, boolean firstLook)
	{
		Patch patch = a.getPatch();
		if (!isHere(patch))
		{
			return;
		}
		PatchState now = patch.getKind().forVarbitValue(client.getVarbitValue(patch.getVarbitId()));
		PatchState before = seen.put(patch.getId(), now);
		if (firstLook || before == null)
		{
			return;
		}
		if (PatchProgress.harvested(before, now))
		{
			session.update(p -> p.markHarvested(patch.getId()));
		}
		if (!PatchProgress.isDone(a, before) && PatchProgress.isDone(a, now))
		{
			session.update(p -> p.markWorked(patch.getId()));
		}
	}

	/** Patches with a grown crop to harvest before planting (Time Tracking says ready), not trees or kept plants. */
	private Set<String> harvestFirst(PlantingPlan plan)
	{
		long now = System.currentTimeMillis() / 1000;
		return plan.getAssignments().stream()
			.filter(a -> a.getSeed() != null && !a.getSeed().isPlantedAsSapling() && !a.isKeptStanding())
			.filter(a -> states.status(a.getPatch(), now).getReadiness() == PatchStatus.Readiness.READY)
			.map(a -> a.getPatch().getId())
			.collect(Collectors.toSet());
	}

	/** Points Shortest Path at the current stop, or clears it and says so when the run is finished. */
	private void point()
	{
		RunSession.ActiveRun run = session.active();
		if (run == null)
		{
			return;
		}
		if (waitIfEarly(run))
		{
			return;
		}
		RunProgress.Stop stop = run.getProgress().currentStop();
		if (stop != null)
		{
			TeleportChoice teleport = run.getTeleports().get(stop.getLocationId());
			if (fairyRings.viaHouse(teleport))
			{
				// Home to the house's fairy ring first (the overhead line says so); the path waits until you've gone.
				heldForHouse = stop.getLocationId();
				if (shortestPath.lastTarget() != null)
				{
					shortestPath.clear();
				}
				return;
			}
			shortestPath.target(targets(stop, run.getProgress()), config.pathCarriedOnly(),
				teleport != null && teleport.getMethod().getType() == TeleportType.HOUSE);
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
		Location location = data.location(stop.getLocationId());
		Player player = client.getLocalPlayer();
		boolean there = player != null && (location.getRegionId() == player.getWorldLocation().getRegionID()
			|| location.getExtraRegionIds().contains(player.getWorldLocation().getRegionID()));
		if (location.getApproach() != null && !there)
		{
			// The patches can't be walked to (underwater): head for where you go in.
			return Set.of(location.getApproach());
		}
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

	/**
	 * Once per new current stop: when you'd get there before its patches are ready, goes somewhere else first if
	 * that's quicker. True when the route changed (the session then points again).
	 */
	private boolean waitIfEarly(RunSession.ActiveRun run)
	{
		RunProgress.Stop current = run.getProgress().currentStop();
		if (current == null || current.getLocationId().equals(earlyCheckedFor))
		{
			return false;
		}
		earlyCheckedFor = current.getLocationId();
		List<String> order = growingWait.betterOrder(run, locationHere(run.getProgress().getStops().stream()
			.map(RunProgress.Stop::getLocationId).collect(Collectors.toList())));
		if (order == null)
		{
			return false;
		}
		log.debug("[farmrun] {} isn't ready yet; route now {}", current.getLocationId(), order);
		earlyCheckedFor = order.get(0);
		session.update(p -> p.reroute(order));
		return true;
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
