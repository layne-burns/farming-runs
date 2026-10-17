/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.state.BufferSettings;
import com.layneburns.farmingruns.state.ContractService;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.state.ItemStore;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.state.UnlockService;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Joins a run setup to what's known about you and works out the planting plan: which patches the run visits
 * (unlocked, of a chosen kind, not skipped), the seeds you hold, your buffers, level and contract. The thinking is in
 * the pure {@link SeedPicker}; this only gathers its inputs.
 */
@Singleton
public class RunPlanService
{
	/** Places seeds are counted from (as in the Bank tab). */
	private static final List<ItemSource> SEED_SOURCES = List.of(
		ItemSource.BANK, ItemSource.SEED_VAULT, ItemSource.SEED_BOX, ItemSource.INVENTORY);

	private final GameData data;
	private final UnlockService unlocks;
	private final ItemStore items;
	private final PlayerInfo player;
	private final ContractService contract;
	private final FarmingRunsConfig config;

	@Inject
	RunPlanService(GameData data, UnlockService unlocks, ItemStore items, PlayerInfo player, ContractService contract,
		FarmingRunsConfig config)
	{
		this.data = data;
		this.unlocks = unlocks;
		this.items = items;
		this.player = player;
		this.contract = contract;
		this.config = config;
	}

	public PlantingPlan plan(RunSetup setup)
	{
		return plan(setup, patches(setup));
	}

	/** The plan for just these patches (e.g. leaving out the ones still growing). */
	public PlantingPlan plan(RunSetup setup, List<Patch> patches)
	{
		return SeedPicker.plan(setup, patches, held(), BufferSettings.from(config), player.getFarmingLevel(), data,
			contract.unplanted());
	}

	/** The patches the run visits: usable, of a kind a chosen run type covers, and not skipped. */
	public List<Patch> patches(RunSetup setup)
	{
		return data.locations().stream()
			.flatMap(l -> l.getPatches().stream())
			.filter(p -> setup.includes(p.getKind()) && !setup.getSkippedPatches().contains(p.getId()) && unlocks.isUsable(p))
			.collect(Collectors.toList());
	}

	/** Every usable patch of a kind (skipped or not), for the "skip patches" list. */
	public List<Patch> usablePatches(PatchImplementation kind)
	{
		return data.patches().stream().filter(p -> p.getKind() == kind && unlocks.isUsable(p)).collect(Collectors.toList());
	}

	/** Seeds and saplings you hold, by item id, from the places seeds are counted. */
	public Map<Integer, Integer> held()
	{
		ItemLedger ledger = items.ledger();
		Map<Integer, Integer> held = new HashMap<>();
		for (Seed seed : data.seeds())
		{
			for (int id : new int[]{seed.getItemId(), seed.plantItemId()})
			{
				int count = SEED_SOURCES.stream().mapToInt(s -> ledger.count(id, s)).sum();
				if (count > 0)
				{
					held.put(id, count);
				}
			}
		}
		return held;
	}

	/** "Ranarr seed in the Farming Guild herb patch", or null when there's no unplanted contract. */
	public String contractNote()
	{
		SeedPicker.Contract c = contract.unplanted();
		return c == null ? null : c.getSeed().getName() + " in the Farming Guild " + data.patch(c.getPatchId()).getKind()
			.name().toLowerCase().replace('_', ' ') + " patch";
	}
}
