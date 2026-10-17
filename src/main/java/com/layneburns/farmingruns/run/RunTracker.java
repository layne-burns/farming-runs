/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.state.StatsStore;
import com.layneburns.farmingruns.vendor.timetracking.Produce;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * Keeps score during a run: Farming XP gained since Start, and the produce you gather at each stop (for "most at one
 * stop" records). When the run finishes, or is stopped part way, it makes a {@link RunSummary}, adds it to your saved
 * {@link RunStats} and keeps it for the Live tab. Client thread.
 */
@Slf4j
@Singleton
public class RunTracker
{
	/** Every crop's produce item (herbs come out grimy), so other items you pick up aren't counted. */
	private static final Set<Integer> PRODUCE = Arrays.stream(Produce.values())
		.filter(p -> p != Produce.WEEDS && p != Produce.SCARECROW)
		.map(Produce::getItemID)
		.collect(Collectors.toUnmodifiableSet());

	private final Client client;
	private final ItemManager itemManager;
	private final RunSession session;
	private final StatsStore stats;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	/** The run being tracked (its start time), 0 for none; and the last one finished, so it isn't counted twice. */
	private long tracking;
	private long finished;
	@Nullable
	private RunSession.ActiveRun lastSeen;
	private int startXp;
	private volatile int xpGained;
	@Nullable
	private String stopId;
	private final Map<Integer, Integer> atThisStop = new HashMap<>();
	private final Map<Integer, Integer> harvested = new HashMap<>();
	private final Map<Integer, Integer> bestAtOneStop = new HashMap<>();
	private Map<Integer, Integer> lastInventory = Map.of();
	@Nullable
	private volatile RunSummary lastSummary;

	@Inject
	RunTracker(Client client, ItemManager itemManager, RunSession session, StatsStore stats)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.session = session;
		this.stats = stats;
		session.addListener(this::sessionChanged);
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	/** Farming XP gained in the run being tracked. */
	public int xpGained()
	{
		return xpGained;
	}

	/** The last run's summary, with any records it set; null before the first. */
	@Nullable
	public RunSummary lastSummary()
	{
		return lastSummary;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (tracking != 0 && event.getSkill() == Skill.FARMING)
		{
			xpGained = event.getXp() - startXp;
			changed();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV || tracking == 0)
		{
			return;
		}
		Map<Integer, Integer> now = inventory();
		now.forEach((id, count) ->
		{
			int gained = count - lastInventory.getOrDefault(id, 0);
			if (gained > 0 && PRODUCE.contains(id))
			{
				atThisStop.merge(id, gained, Integer::sum);
				harvested.merge(id, gained, Integer::sum);
			}
		});
		lastInventory = now;
	}

	/** Called by the session (client thread) whenever the run starts, moves on, finishes or stops. */
	private void sessionChanged()
	{
		RunSession.ActiveRun run = session.active();
		if (run != null && run.getStartedAtMillis() != tracking && run.getStartedAtMillis() != finished)
		{
			begin(run);
		}
		if (run != null && tracking == run.getStartedAtMillis())
		{
			lastSeen = run;
			RunProgress.Stop stop = run.getProgress().currentStop();
			String id = stop == null ? null : stop.getLocationId();
			if (!Objects.equals(id, stopId))
			{
				closeStop();
				stopId = id;
			}
			if (run.getProgress().isFinished())
			{
				finish(true);
			}
		}
		else if (run == null && tracking != 0)
		{
			finish(false);
		}
	}

	private void begin(RunSession.ActiveRun run)
	{
		tracking = run.getStartedAtMillis();
		lastSeen = run;
		startXp = client.getSkillExperience(Skill.FARMING);
		xpGained = 0;
		atThisStop.clear();
		harvested.clear();
		bestAtOneStop.clear();
		lastInventory = inventory();
		RunProgress.Stop stop = run.getProgress().currentStop();
		stopId = stop == null ? null : stop.getLocationId();
		changed();
	}

	/** The stop is over: its haul counts towards the best at one stop. */
	private void closeStop()
	{
		atThisStop.forEach((id, count) -> bestAtOneStop.merge(id, count, Math::max));
		atThisStop.clear();
	}

	private void finish(boolean completed)
	{
		closeStop();
		RunSession.ActiveRun run = lastSeen;
		finished = tracking;
		tracking = 0;
		if (run == null)
		{
			return;
		}
		RunProgress progress = run.getProgress();
		int planted = (int) progress.getStops().stream().flatMap(s -> s.getPatches().stream())
			.filter(a -> a.getSeed() != null && progress.getWorkedPatches().contains(a.getPatch().getId())).count();
		if (!completed && planted == 0 && harvested.isEmpty() && xpGained <= 0)
		{
			// Started and stopped without doing anything: not worth a summary or a stats entry.
			changed();
			return;
		}
		RunSummary summary = new RunSummary(completed, System.currentTimeMillis() - run.getStartedAtMillis(), xpGained,
			(int) progress.doneCount(), progress.getStops().size(), planted, named(harvested), named(bestAtOneStop),
			List.of());
		RunStats.Added added = stats.stats().add(summary);
		stats.save(added.getStats());
		lastSummary = added.getRun();
		log.debug("[farmrun] run summary: {}", lastSummary);
		changed();
	}

	/** Item id to count, as item name to count (sorted by name). */
	private Map<String, Integer> named(Map<Integer, Integer> counts)
	{
		Map<String, Integer> names = new TreeMap<>();
		counts.forEach((id, count) -> names.merge(itemManager.getItemComposition(id).getName(), count, Integer::sum));
		return Map.copyOf(names);
	}

	/** Your inventory now, noted items counted as unnoted. */
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

	private void changed()
	{
		listeners.forEach(Runnable::run);
	}
}
