/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.GoalPreset;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a goal preset into a run setup: the run types its crops need, and for each patch kind its crops as chosen
 * crops. When a goal has several crops of one kind (Super combat: irit, kwuarm, cadantine, torstol, all herbs),
 * they share that kind's patches by how many patches one product needs of each: items needed per product divided
 * by the average harvest per patch. Crops without a harvest figure (trees for logs or XP) share evenly. Pure: tested.
 */
public final class GoalSetups
{
	/** Weights are whole numbers; this keeps three decimal places of the ratio. */
	private static final int SCALE = 1000;

	private GoalSetups()
	{
	}

	public static RunSetup toSetup(GoalPreset goal)
	{
		Set<RunType> types = EnumSet.noneOf(RunType.class);
		Map<PatchImplementation, List<CropShare>> crops = new EnumMap<>(PatchImplementation.class);
		for (GoalPreset.Crop crop : goal.getCrops())
		{
			PatchImplementation kind = crop.getSeed().getKind();
			for (RunType type : RunType.values())
			{
				if (type.covers(kind))
				{
					types.add(type);
				}
			}
			crops.computeIfAbsent(kind, k -> new ArrayList<>())
				.add(new CropShare(crop.getSeed().getItemId(), weight(crop), null));
		}
		Map<PatchImplementation, KindPlan> plans = new EnumMap<>(PatchImplementation.class);
		crops.forEach((kind, shares) -> plans.put(kind, new KindPlan(false, List.copyOf(shares), false,
			TreeRoutine.PAY_TO_REMOVE, false, FillOrder.SHARES, null, false)));
		return new RunSetup(Set.copyOf(types), Map.copyOf(plans), Set.of(), CompostChoice.SUPERCOMPOST);
	}

	/** Patches needed per product, scaled to a whole number; 1 x SCALE when there's no harvest figure. */
	static int weight(GoalPreset.Crop crop)
	{
		if (crop.getAvgYieldPerPatch() <= 0)
		{
			return SCALE;
		}
		return Math.max(1, (int) Math.round(SCALE * crop.getPerProduct() / crop.getAvgYieldPerPatch()));
	}
}
