/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.run.RunPlanService;
import com.layneburns.farmingruns.run.RunSetup;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemStore;
import com.layneburns.farmingruns.state.LocationPrefs;
import com.layneburns.farmingruns.state.PatchStates;
import com.layneburns.farmingruns.state.PatchStatus;
import com.layneburns.farmingruns.state.PlayerInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;

/**
 * Joins a run setup to what's known about you and checks your items for it (SPEC §2.4): which patches are still
 * growing, the plan for the rest, what that needs, and where each thing is. The thinking is in the pure
 * {@link RequirementsBuilder} and {@link InventoryChecker}; this gathers their inputs. Also remembers what the last
 * check said to withdraw, for the bank highlight.
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
	/** Items the last check said to take from the bank. Read by the bank overlay on the client thread. */
	private volatile Set<Integer> toWithdraw = Set.of();

	@Inject
	RunCheckService(GameData data, RunPlanService planner, LocationPrefs prefs, PlayerInfo player, ItemStore items,
		PatchStates states)
	{
		this.data = data;
		this.planner = planner;
		this.prefs = prefs;
		this.player = player;
		this.items = items;
		this.states = states;
	}

	/** The run's patches that are still growing (Time Tracking says so), soonest ready first. */
	public List<Patch> notReady(RunSetup setup, long nowSeconds)
	{
		return planner.patches(setup).stream()
			.filter(p -> isGrowing(p, nowSeconds))
			.sorted((a, b) -> Long.compare(status(a, nowSeconds).getReadyAtSeconds(), status(b, nowSeconds)
				.getReadyAtSeconds()))
			.collect(Collectors.toList());
	}

	/** The run's patches to visit now: all of them except the ones still growing. */
	public List<Patch> readyPatches(RunSetup setup, long nowSeconds)
	{
		return planner.patches(setup).stream().filter(p -> !isGrowing(p, nowSeconds)).collect(Collectors.toList());
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
			teleports.put(location.getId(), prefs.teleportsFor(location));
		}
		Predicate<Patch> grown = p ->
		{
			PatchStatus.Readiness readiness = status(p, nowSeconds).getReadiness();
			return readiness == PatchStatus.Readiness.READY || readiness == PatchStatus.Readiness.UNKNOWN;
		};
		RunRequirements requirements = RequirementsBuilder.build(setup, plan, teleports, data, player.getFacts(),
			ledger, grown);
		List<InventoryChecker.Line> lines = InventoryChecker.check(requirements.getRequirements(), ledger,
			data.getItems().group("seed_box"));
		toWithdraw = lines.stream().filter(l -> l.getToWithdraw() > 0)
			.flatMap(l -> l.getRequirement().getItemIds().stream()).collect(Collectors.toUnmodifiableSet());
		return new Check(plan, requirements, lines);
	}

	/** Item ids the last check said to withdraw: the bank highlights these. */
	public Set<Integer> toWithdraw()
	{
		return toWithdraw;
	}

	/** Forgets the highlight (e.g. when a run is stopped). */
	public void clearHighlight()
	{
		toWithdraw = Set.of();
	}

	/** Growing, with time still to go (a crop whose time has passed is "ready, probably", so it counts as ready). */
	private boolean isGrowing(Patch patch, long nowSeconds)
	{
		PatchStatus status = status(patch, nowSeconds);
		return status.getReadiness() == PatchStatus.Readiness.GROWING && status.getReadyAtSeconds() > nowSeconds;
	}
}
