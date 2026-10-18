/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.ArrayList;
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
		/** True when the tree there is picked and left standing (harvest runs, "keep standing" types). */
		boolean keptStanding;
		/** The tree left standing, if known. */
		@Nullable
		Seed standing;
		/** Planted without protection: there wasn't enough payment ("only plant what I can pay for"). */
		boolean unprotected;

		public Assignment(Patch patch, @Nullable Seed seed, boolean contract, boolean keptStanding,
			@Nullable Seed standing, boolean unprotected)
		{
			this.patch = patch;
			this.seed = seed;
			this.contract = contract;
			this.keptStanding = keptStanding;
			this.standing = standing;
			this.unprotected = unprotected;
		}

		public Assignment(Patch patch, @Nullable Seed seed, boolean contract, boolean keptStanding,
			@Nullable Seed standing)
		{
			this(patch, seed, contract, keptStanding, standing, false);
		}

		public Assignment(Patch patch, @Nullable Seed seed, boolean contract)
		{
			this(patch, seed, contract, false, null);
		}

		/** A tree picked and left standing. */
		static Assignment kept(Patch patch, @Nullable Seed standing)
		{
			return new Assignment(patch, null, false, true, standing);
		}
	}

	List<Assignment> assignments;
	/** E.g. "Only 4 Ranarr seed spare for 6 patches: 2 filled with Toadflax seed". */
	List<String> warnings;
	/** Trees planned from seed: saplings to grow before the run, by seed. Empty unless "grow saplings" is on. */
	Map<Seed, Integer> saplingsToGrow;

	public PlantingPlan(List<Assignment> assignments, List<String> warnings, Map<Seed, Integer> saplingsToGrow)
	{
		this.assignments = assignments;
		this.warnings = warnings;
		this.saplingsToGrow = saplingsToGrow;
	}

	/** A plan with nothing to grow first. */
	public PlantingPlan(List<Assignment> assignments, List<String> warnings)
	{
		this(assignments, warnings, Map.of());
	}

	/** E.g. "2 × Magic sapling (from Magic seed)" for each sapling to grow; empty when there's none. */
	public List<String> growNotes(PatchImplementation kind)
	{
		List<String> notes = new ArrayList<>();
		saplingsToGrow.forEach((seed, count) ->
		{
			if (seed.getKind() == kind)
			{
				notes.add(count + " × " + seed.getSaplingName() + " (from " + seed.getName() + ")");
			}
		});
		return notes;
	}

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

	/** Trees picked and left standing. */
	public long keptCount()
	{
		return assignments.stream().filter(Assignment::isKeptStanding).count();
	}

	/** Patches that get a seed. */
	public long plantedCount()
	{
		return assignments.stream().filter(a -> a.getSeed() != null).count();
	}
}
