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
		return new RunSetup(Set.copyOf(types), Map.copyOf(plans), Set.copyOf(skipped), presets.get(0).getCompost());
	}

	/**
	 * One kind across presets: the presets that chose crops each get an equal share of the patches, split inside
	 * that share by their own weights. Exact counts are kept as they are. Pay if any preset pays; harvest unless
	 * every preset skips it.
	 */
	private static KindPlan combineKind(List<RunSetup> presets, PatchImplementation kind)
	{
		List<KindPlan> chosen = new ArrayList<>();
		boolean pay = false;
		boolean skipHarvest = true;
		TreeRoutine routine = null;
		for (RunSetup preset : presets)
		{
			KindPlan plan = preset.kindPlan(kind);
			pay |= plan.isPay();
			skipHarvest &= plan.isSkipHarvest();
			if (routine == null && preset.getKinds().containsKey(kind))
			{
				routine = plan.getTreeRoutine();
			}
			if (!plan.isAuto() && !plan.getCrops().isEmpty())
			{
				chosen.add(plan);
			}
		}
		routine = routine == null ? TreeRoutine.PAY_TO_REMOVE : routine;
		if (chosen.isEmpty())
		{
			return new KindPlan(true, List.of(), pay, routine, skipHarvest);
		}
		// Scale each preset's weights so every preset's total is the same: the product of all the totals.
		long common = 1;
		for (KindPlan plan : chosen)
		{
			common *= totalWeight(plan);
		}
		List<CropShare> crops = new ArrayList<>();
		for (KindPlan plan : chosen)
		{
			long scale = common / totalWeight(plan);
			for (CropShare crop : plan.getCrops())
			{
				crops.add(new CropShare(crop.getSeedItemId(), (int) (crop.getWeight() * scale), crop.getPatches()));
			}
		}
		return new KindPlan(false, List.copyOf(crops), pay, routine, skipHarvest);
	}

	private static long totalWeight(KindPlan plan)
	{
		long total = plan.getCrops().stream().mapToLong(c -> Math.max(1, c.getWeight())).sum();
		return Math.max(1, total);
	}
}
