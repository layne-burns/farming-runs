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
import java.util.HashMap;
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
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * Runs a farming run (SPEC §2.5–2.6): orders the stops from where you are ({@link RoutePlanner}), points Shortest
 * Path at the current stop, and ticks patches off as their varbits show them done ({@link PatchProgress}) and, where
 * the gardener is to be paid, as the payment leaves your inventory ({@link PaymentMatcher}), moving on when a stop is
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
	private final ItemManager itemManager;
	private final FarmingRunsConfig config;
	private final PlayerInfo player;
	/** The region you were in last tick, to notice arriving somewhere. */
	private int lastRegion = -1;
	/** Your inventory last time it changed (noted items as unnoted), to see what a payment took. */
	private Map<Integer, Integer> lastInventory = Map.of();

	@Inject
	RunController(Client client, ClientThread clientThread, GameData data, RunSession session,
		ShortestPathClient shortestPath, ItemManager itemManager, FarmingRunsConfig config,
		PlayerInfo player)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.data = data;
		this.session = session;
		this.shortestPath = shortestPath;
		this.itemManager = itemManager;
		this.config = config;
		this.player = player;
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
				RunProgress.start(stops, payFor(setup, plan))));
			lastInventory = inventory();
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
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		Map<Integer, Integer> now = inventory();
		Map<Integer, Integer> spent = PaymentMatcher.spent(lastInventory, now);
		lastInventory = now;
		RunSession.ActiveRun run = session.active();
		if (run == null || spent.isEmpty())
		{
			return;
		}
		List<PlantingPlan.Assignment> awaiting = new ArrayList<>();
		run.getProgress().getStops().forEach(stop -> stop.getPatches().stream()
			.filter(a -> run.getProgress().isAwaitingPayment(a.getPatch().getId()) && isHere(a.getPatch()))
			.forEach(awaiting::add));
		for (String patchId : PaymentMatcher.paid(spent, awaiting))
		{
			log.debug("[farmrun] paid for {}", patchId);
			session.update(p -> p.markPaid(patchId));
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
			shortestPath.target(data.location(stop.getLocationId()).getTarget(), config.pathCarriedOnly());
			return;
		}
		if (shortestPath.lastTarget() != null)
		{
			shortestPath.clear();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Farming run complete.", null);
			log.debug("[farmrun] finished");
		}
	}

	/** Patches the gardener is to be paid to protect ({@link Protection}). */
	private Set<String> payFor(RunSetup setup, PlantingPlan plan)
	{
		return plan.getAssignments().stream()
			.filter(a -> Protection.shouldPay(a, setup, data, player.getFacts()))
			.map(a -> a.getPatch().getId())
			.collect(Collectors.toSet());
	}

	/** Your inventory now, item id to count, noted items counted as unnoted. */
	private Map<Integer, Integer> inventory()
	{
		Map<Integer, Integer> counts = new HashMap<>();
		ItemContainer container = client.getItemContainer(InventoryID.INV);
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > 0 && item.getQuantity() > 0)
				{
					counts.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
				}
			}
		}
		return counts;
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
