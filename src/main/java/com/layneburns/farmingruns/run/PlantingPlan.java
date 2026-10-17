/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.Seed;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Value;

/** What goes in each patch of a run, as worked out by {@link SeedPicker}, and anything worth warning about. */
@Value
public class PlantingPlan
{
	@Value
	public static class Assignment
	{
		Patch patch;
		/** The seed to plant, or null when there's nothing you can spare for it. */
		@Nullable
		Seed seed;
		/** True for the Farming Guild patch your farming contract needs. */
		boolean contract;
	}

	List<Assignment> assignments;
	/** E.g. "Only 4 Ranarr seed spare for 6 patches: 2 filled with Toadflax seed". */
	List<String> warnings;

	/** Items to plant, by item id (saplings for trees): seeds per patch times patches. */
	public Map<Integer, Integer> itemsToPlant()
	{
		Map<Integer, Integer> items = new LinkedHashMap<>();
		for (Assignment a : assignments)
		{
			if (a.getSeed() != null)
			{
				items.merge(a.getSeed().plantItemId(), a.getSeed().getPerPatch(), Integer::sum);
			}
		}
		return items;
	}

	/** Patches that get a seed. */
	public long plantedCount()
	{
		return assignments.stream().filter(a -> a.getSeed() != null).count();
	}
}
