/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * The seeds and saplings the rest of a run still needs, against what you carry (inventory, plus your seed box when
 * it's in your inventory), for the Live tab. Skipped stops aren't counted. Pure: unit tested.
 */
public final class SeedsLeft
{
	@Value
	public static class Line
	{
		String name;
		int needed;
		int carried;
	}

	private SeedsLeft()
	{
	}

	public static List<Line> of(RunProgress progress, ItemLedger ledger, Set<Integer> seedBoxIds)
	{
		Map<Seed, Integer> needed = new LinkedHashMap<>();
		for (RunProgress.Stop stop : progress.getStops())
		{
			if (progress.isSkipped(stop))
			{
				continue;
			}
			for (PlantingPlan.Assignment a : stop.getPatches())
			{
				if (a.getSeed() != null && !progress.getWorkedPatches().contains(a.getPatch().getId()))
				{
					needed.merge(a.getSeed(), a.getSeed().getPerPatch(), Integer::sum);
				}
			}
		}
		boolean boxCarried = seedBoxIds.stream().anyMatch(id -> ledger.count(id, ItemSource.INVENTORY) > 0);
		List<Line> lines = new ArrayList<>();
		needed.forEach((seed, count) ->
		{
			int id = seed.plantItemId();
			int carried = ledger.count(id, ItemSource.INVENTORY) + (boxCarried ? ledger.count(id, ItemSource.SEED_BOX) : 0);
			lines.add(new Line(seed.isPlantedAsSapling() ? seed.getSaplingName() : seed.getName(), count, carried));
		});
		return lines;
	}
}
