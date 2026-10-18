/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Combines several presets into one setup (SPEC §2.3): all their run types; for a patch kind that more than one
 * preset chose crops for, their crops share the patches evenly per preset (two presets: 50:50, whatever each one's
 * own split); skipped patches from any of them; compost from the first. Pure: unit tested.
 */
public final class PresetCombiner
{
	/** Each combined preset's shares add up to this, so presets weigh the same (and numbers stay small). */
	private static final int PRESET_TOTAL = 1000;

	private PresetCombiner()
	{
	}

	public static RunSetup combine(List<RunSetup> presets)
	{
		if (presets.isEmpty())
		{
			return RunSetup.EMPTY;
		}
		if (presets.size() == 1)
		{
			return presets.get(0);
		}
		Set<RunType> types = new HashSet<>();
		Set<String> skipped = new HashSet<>();
		Set<PatchImplementation> kinds = new HashSet<>();
		for (RunSetup preset : presets)
		{
			types.addAll(preset.getRunTypes());
			skipped.addAll(preset.getSkippedPatches());
			kinds.addAll(preset.getKinds().keySet());
		}
		Map<PatchImplementation, KindPlan> plans = new EnumMap<>(PatchImplementation.class);
		for (PatchImplementation kind : kinds)
		{
			plans.put(kind, combineKind(presets, kind));
		}
		// A kind stays left out only when every preset that covers it leaves it out.
		Set<PatchImplementation> leftOut = new HashSet<>();
		for (PatchImplementation kind : PatchImplementation.values())
		{
			boolean covered = false;
			boolean kept = false;
			for (RunSetup preset : presets)
			{
				if (preset.getRunTypes().stream().anyMatch(t -> t.covers(kind)))
				{
					covered = true;
					kept |= !preset.getLeftOutKinds().contains(kind);
				}
			}
			if (covered && !kept)
			{
				leftOut.add(kind);
			}
		}
		return new RunSetup(Set.copyOf(types), Map.copyOf(plans), Set.copyOf(skipped), presets.get(0).getCompost(),
			Set.copyOf(leftOut));
	}

	/**
	 * A preset (or combination) applied over your current setup: it sets the run types, skips and compost, and the
	 * plans for the patch kinds it covers; every other kind keeps the plan you had (a fruit tree routine and its keep
	 * ticks survive switching to a herb preset and back).
	 */
	public static RunSetup applyOver(RunSetup current, RunSetup preset)
	{
		Map<PatchImplementation, KindPlan> kinds = new EnumMap<>(PatchImplementation.class);
		kinds.putAll(current.getKinds());
		kinds.putAll(preset.getKinds());
		return preset.withKinds(Map.copyOf(kinds));
	}

	/**
	 * One kind across presets: the presets that chose crops each get an equal share of the patches, split inside
	 * that share by their own weights. Exact counts are kept as they are. Pay if any preset pays; harvest unless
	 * every preset skips it; grow saplings from seed if any preset does; replace on a harvest run what any replaces. The tree routine, fill order and amulet choice come from the first preset that set this
	 * kind.
	 */
	private static KindPlan combineKind(List<RunSetup> presets, PatchImplementation kind)
	{
		List<KindPlan> chosen = new ArrayList<>();
		boolean pay = false;
		boolean skipHarvest = true;
		TreeRoutine routine = null;
		FillOrder order = null;
		Boolean amulet = null;
		boolean grow = false;
		Set<Integer> replace = new HashSet<>();
		boolean payableOnly = false;
		boolean replantSame = false;
		for (RunSetup preset : presets)
		{
			KindPlan plan = preset.kindPlan(kind);
			pay |= plan.isPay();
			skipHarvest &= plan.isSkipHarvest();
			grow |= plan.isGrowSaplings();
			replace.addAll(plan.getReplaceStanding());
			payableOnly |= plan.isPayableOnly();
			replantSame |= plan.isReplantSame();
			if (routine == null && preset.getKinds().containsKey(kind))
			{
				routine = plan.getTreeRoutine();
				order = plan.getFillOrder();
				amulet = plan.getAmuletOfBounty();
			}
			if (!plan.isAuto() && !plan.getCrops().isEmpty())
			{
				chosen.add(plan);
			}
		}
		routine = routine == null ? TreeRoutine.PAY_TO_REMOVE : routine;
		order = order == null ? FillOrder.SHARES : order;
		if (chosen.isEmpty())
		{
			return new KindPlan(true, List.of(), pay, routine, skipHarvest, order, amulet, grow, Set.copyOf(replace), payableOnly,
				replantSame);
		}
		// Scale each preset's weights so every preset's total is the same (PRESET_TOTAL), keeping its own split.
		List<CropShare> crops = new ArrayList<>();
		for (KindPlan plan : chosen)
		{
			long total = totalWeight(plan);
			for (CropShare crop : plan.getCrops())
			{
				int weight = (int) Math.max(1, Math.round((double) PRESET_TOTAL * Math.max(1, crop.getWeight()) / total));
				crops.add(new CropShare(crop.getSeedItemId(), weight, crop.getPatches()));
			}
		}
		return new KindPlan(false, List.copyOf(crops), pay, routine, skipHarvest, order, amulet, grow, Set.copyOf(replace),
			payableOnly, replantSame);
	}

	private static long totalWeight(KindPlan plan)
	{
		long total = plan.getCrops().stream().mapToLong(c -> Math.max(1, c.getWeight())).sum();
		return Math.max(1, total);
	}
}
