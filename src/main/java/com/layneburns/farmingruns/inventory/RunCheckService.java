/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.run.CompostBinPlanner;
import com.layneburns.farmingruns.run.CompostBins;
import com.layneburns.farmingruns.run.CompostChoice;
import com.layneburns.farmingruns.run.OnTheWay;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.run.RunPlanService;
import com.layneburns.farmingruns.run.RunSetup;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemStore;
import com.layneburns.farmingruns.state.LocationPrefs;
import com.layneburns.farmingruns.state.PatchStates;
import com.layneburns.farmingruns.state.PatchStatus;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.state.TeleportLimits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;

/**
 * Joins a run setup to what's known about you and checks your items for it (SPEC §2.4): which patches are still
 * growing, the plan for the rest, what that needs, and where each thing is. The thinking is in the pure
 * {@link RequirementsBuilder} and {@link InventoryChecker}; this gathers their inputs. Also remembers what the last
 * check said to take from the bank, for the bank highlight and the "Show in bank" tab.
 */
@Singleton
public class RunCheckService
{
	@Value
	public static class Check
	{
		PlantingPlan plan;
		RunRequirements requirements;
		List<InventoryChecker.Line> lines;

		/** Something you need is missing everywhere. */
		public boolean hasMissing()
		{
			return lines.stream().anyMatch(InventoryChecker.Line::blocksStart);
		}

		/** Something still has to come out of the bank or vault. */
		public boolean hasWithdrawals()
		{
			return lines.stream().anyMatch(l -> l.getToWithdraw() > 0);
		}
	}

	private final GameData data;
	private final RunPlanService planner;
	private final LocationPrefs prefs;
	private final PlayerInfo player;
	private final ItemStore items;
	private final PatchStates states;
	private final TeleportLimits limits;
	private final FarmingRunsConfig config;
	private final CompostBinPlanner compostBins;
	/** Reuse a {@link OnTheWay} answer for the same patches this many seconds. */
	private static final int CACHE_SECONDS = 30;
	private volatile OnTheWayCache onTheWay;
	/**
	 * Items the last check said you could take from the bank, with how many: what you'd otherwise withdraw, plus tools
	 * and compost the leprechaun also has (you may grab them from either). Read on the client thread by the bank.
	 */
	private volatile Map<Integer, Integer> bankItems = Map.of();
	/**
	 * Every item the last check said the run uses (met or not), plus your seed box and rune pouch when they'd hold
	 * what it needs: anything else in your inventory can go in the bank.
	 */
	private volatile Set<Integer> neededItems = Set.of();
	/** What the last check said to take from the tool leprechaun, e.g. "9 × Supercompost". */
	private volatile List<String> fromLeprechaun = List.of();
	private volatile List<String> saplingKit = List.of();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	@Inject
	RunCheckService(GameData data, RunPlanService planner, LocationPrefs prefs, PlayerInfo player, ItemStore items,
		PatchStates states, TeleportLimits limits, FarmingRunsConfig config, CompostBinPlanner compostBins)
	{
		this.compostBins = compostBins;
		this.config = config;
		this.limits = limits;
		this.data = data;
		this.planner = planner;
		this.prefs = prefs;
		this.player = player;
		this.items = items;
		this.states = states;
	}

	/**
	 * The run's patches that are still growing (Time Tracking says so) and left out, soonest ready first. With
	 * "Include patches ready on the way", only the ones that won't be ready by the time the run gets there.
	 */
	public List<Patch> notReady(RunSetup setup, long nowSeconds)
	{
		Set<Patch> visited = new HashSet<>(readyPatches(setup, nowSeconds));
		return planner.patches(setup).stream()
			.filter(p -> !visited.contains(p))
			.sorted((a, b) -> Long.compare(status(a, nowSeconds).getReadyAtSeconds(), status(b, nowSeconds)
				.getReadyAtSeconds()))
			.collect(Collectors.toList());
	}

	/**
	 * The run's patches to visit now: all of them except the ones still growing, plus, with "Include patches ready
	 * on the way", growing ones that will be ready by the time the route gets to them ({@link OnTheWay}).
	 */
	public List<Patch> readyPatches(RunSetup setup, long nowSeconds)
	{
		List<Patch> patches = planner.patches(setup);
		if (!config.readyOnTheWay())
		{
			return patches.stream().filter(p -> !isGrowing(p, nowSeconds)).collect(Collectors.toList());
		}
		OnTheWayCache cached = onTheWay;
		if (cached != null && cached.patches.equals(patches) && Math.abs(cached.nowSeconds - nowSeconds) < CACHE_SECONDS)
		{
			return cached.ready;
		}
		List<Patch> ready = List.copyOf(OnTheWay.patches(patches, p -> isGrowing(p, nowSeconds)
			? OnTheWay.ticks(status(p, nowSeconds).getReadyAtSeconds() - nowSeconds) : 0, data.getCosts()::cost, null));
		onTheWay = new OnTheWayCache(patches, nowSeconds, ready);
		return ready;
	}

	/** The last {@link OnTheWay} answer: the panel asks often, and planning takes a moment. */
	@Value
	private static class OnTheWayCache
	{
		List<Patch> patches;
		long nowSeconds;
		List<Patch> ready;
	}

	/** Which of these patches are still growing, by id: the ones a run started now waits for. */
	public Set<String> stillGrowing(List<Patch> patches, long nowSeconds)
	{
		return patches.stream().filter(p -> isGrowing(p, nowSeconds)).map(Patch::getId)
			.collect(Collectors.toUnmodifiableSet());
	}

	public PatchStatus status(Patch patch, long nowSeconds)
	{
		return states.status(patch, nowSeconds);
	}

	/** Plans the run for these patches, works out what it needs and checks your items. */
	public Check check(RunSetup setup, List<Patch> patches, long nowSeconds)
	{
		PlantingPlan plan = planner.plan(setup, patches);
		ItemLedger ledger = items.ledger();
		Map<String, List<String>> teleports = new HashMap<>();
		for (Location location : data.locations())
		{
			teleports.put(location.getId(), usable(prefs.teleportsFor(location)));
		}
		Predicate<Patch> grown = p ->
		{
			PatchStatus.Readiness readiness = status(p, nowSeconds).getReadiness();
			return readiness == PatchStatus.Readiness.READY || readiness == PatchStatus.Readiness.UNKNOWN;
		};
		RunRequirements requirements = withCompostBins(withLimitNotes(RequirementsBuilder.build(setup, plan, teleports,
			prefs::fare, data, player.getFacts(), ledger, grown, states.isAutoweedOn()), plan), setup, plan);
		List<InventoryChecker.Line> lines = InventoryChecker.check(requirements.getRequirements(), ledger,
			data.getItems().group("seed_box"));
		if (config.saplingMode())
		{
			// The bank shows the sapling kit instead of the run's items.
			List<Requirement> kit = buildSaplingKit();
			List<InventoryChecker.Line> kitLines = InventoryChecker.check(kit, ledger, data.getItems().group("seed_box"));
			setBankItems(kitLines);
			setNeededItems(kit);
			fromLeprechaun = List.of();
			saplingKit = kitLines.stream().map(l -> l.getRequirement().getAmount() + " × " + l.getRequirement().getLabel()
				+ (l.fromBank() > 0 ? " (" + l.fromBank() + " from the bank)" : "")).collect(Collectors.toUnmodifiableList());
			return new Check(plan, requirements, lines);
		}
		saplingKit = List.of();
		setBankItems(lines);
		setNeededItems(requirements.getRequirements());
		fromLeprechaun = lines.stream()
			.filter(l -> l.getFromLeprechaun() > 0 && l.getRequirement().getImportance() == Requirement.Importance.NEED)
			.map(l -> l.getFromLeprechaun() + " × " + l.getRequirement().getLabel())
			.collect(Collectors.toUnmodifiableList());
		return new Check(plan, requirements, lines);
	}

	/**
	 * Sapling making: every seed you have that's planted as a sapling (bank, vault, inventory, seed box), with a filled
	 * plant pot for each, a trowel and watering cans. Runs grow what they need on their
	 * own; this is for potting the lot now.
	 */
	private List<Requirement> buildSaplingKit()
	{
		Set<String> skipped = saplingSkipped();
		Map<Seed, Integer> seeds = new LinkedHashMap<>(saplingSeedsHeld());
		seeds.keySet().removeIf(seed -> skipped.contains(String.valueOf(seed.getItemId())));
		return RequirementsBuilder.saplingKit(seeds, data.getItems());
	}

	/** Every seed you have that's planted as a sapling, with how many (bank, vault, inventory, seed box). */
	public Map<Seed, Integer> saplingSeedsHeld()
	{
		Map<Integer, Integer> held = planner.held();
		Map<Seed, Integer> seeds = new LinkedHashMap<>();
		for (Seed seed : data.seeds())
		{
			int count = held.getOrDefault(seed.getItemId(), 0);
			if (seed.isPlantedAsSapling() && count > 0)
			{
				seeds.put(seed, count);
			}
		}
		return seeds;
	}

	/** Seed item ids (as text) left out of sapling making, from the Saplings tab. */
	public Set<String> saplingSkipped()
	{
		String saved = config.saplingSkipped();
		return saved == null || saved.isBlank() ? Set.of()
			: Arrays.stream(saved.split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet());
	}

	/** With sapling making on, the kit from the last check, e.g. "6 × Magic seed (6 from the bank)"; else empty. */
	public List<String> saplingKit()
	{
		return saplingKit;
	}

	/** A location's teleports without the ones used up for now ({@link TeleportLimits}). */
	public List<String> usable(List<String> refs)
	{
		return refs.stream().filter(r -> !limits.isUsedUp(r)).collect(Collectors.toList());
	}

	/** Adds what the compost bins at the run's stops need: pineapples to fill, buckets to collect. */
	private RunRequirements withCompostBins(RunRequirements requirements, RunSetup setup, PlantingPlan plan)
	{
		Set<String> visited = plan.getAssignments().stream().map(a -> a.getPatch().getLocationId())
			.collect(Collectors.toSet());
		Map<Patch, CompostBins.Work> bins = compostBins.work(visited);
		if (bins.isEmpty())
		{
			return requirements;
		}
		int fill = bins.values().stream().mapToInt(CompostBins.Work::getFill).sum();
		int collect = setup.getCompost() == CompostChoice.BOTTOMLESS_BUCKET ? 0
			: bins.values().stream().mapToInt(CompostBins.Work::getCollect).sum();
		List<Requirement> all = new ArrayList<>(requirements.getRequirements());
		all.addAll(RequirementsBuilder.compostBinKit(fill, collect, data.getItems()));
		return new RunRequirements(all, requirements.getTeleports(), requirements.getNotes());
	}

	/** Says which first-choice teleports are used up for now, so the next one is being used. */
	private RunRequirements withLimitNotes(RunRequirements requirements, PlantingPlan plan)
	{
		Set<String> usedUp = new LinkedHashSet<>();
		plan.getAssignments().stream().map(a -> a.getPatch().getLocationId()).distinct().forEach(id ->
		{
			List<String> refs = prefs.teleportsFor(data.location(id));
			TeleportChoice first = refs.isEmpty() ? null : data.teleportChoice(refs.get(0));
			if (first != null && limits.isUsedUp(first.getRef()))
			{
				usedUp.add(first.displayName());
			}
		});
		if (usedUp.isEmpty())
		{
			return requirements;
		}
		List<String> notes = new ArrayList<>(requirements.getNotes());
		usedUp.forEach(name -> notes.add(name + " is used up for now: using the next teleport"));
		return new RunRequirements(requirements.getRequirements(), requirements.getTeleports(), notes);
	}

	/** Item id to how many to take, from the last check: the bank highlights these and the run tab shows them. */
	public Map<Integer, Integer> bankItems()
	{
		return bankItems;
	}

	/** What to take from the tool leprechaun, e.g. "9 × Supercompost"; empty when nothing. */
	public List<String> fromLeprechaun()
	{
		return fromLeprechaun;
	}

	/** Items the run uses; empty when there's no run set up. */
	public Set<Integer> neededItems()
	{
		return neededItems;
	}

	private void setNeededItems(List<Requirement> requirements)
	{
		Set<Integer> needed = new HashSet<>();
		requirements.forEach(r -> needed.addAll(r.getItemIds()));
		if (needed.stream().anyMatch(id -> data.seedByItem(id) != null))
		{
			needed.addAll(data.getItems().group("seed_box"));
		}
		if (needed.stream().anyMatch(data.getItems().runes().values()::contains))
		{
			needed.addAll(data.getItems().group("rune_pouch"));
		}
		neededItems = Set.copyOf(needed);
	}

	/** Called when {@link #bankItems()} changes. */
	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	private void setBankItems(List<InventoryChecker.Line> lines)
	{
		Map<Integer, Integer> items = new HashMap<>();
		for (InventoryChecker.Line line : lines)
		{
			int count = line.fromBank();
			if (count > 0)
			{
				line.getRequirement().getItemIds().forEach(id -> items.merge(id, count, Math::max));
			}
		}
		if (!items.equals(bankItems))
		{
			bankItems = Map.copyOf(items);
			listeners.forEach(Runnable::run);
		}
	}

	/** Growing, with time still to go (a crop whose time has passed is "ready, probably", so it counts as ready). */
	private boolean isGrowing(Patch patch, long nowSeconds)
	{
		PatchStatus status = status(patch, nowSeconds);
		return status.getReadiness() == PatchStatus.Readiness.GROWING && status.getReadyAtSeconds() > nowSeconds;
	}
}
