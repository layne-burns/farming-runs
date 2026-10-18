/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import com.layneburns.farmingruns.vendor.timetracking.Produce;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What to do with a grown plant before replanting (SPEC §2.3), set per kind. Clearing is for trees; "pick, leave it
 * standing" is for every plant whose crop grows back (fruit trees, bushes, cacti).
 */
@Getter
@RequiredArgsConstructor
public enum TreeRoutine
{
	/** Check health, then pay the gardener 200 coins to clear it. */
	PAY_TO_REMOVE("Check health, pay to remove"),
	/** Check health, chop it down and dig up the roots (needs an axe). */
	CHOP_AND_DIG("Check health, chop and dig"),
	/** Plants whose crop grows back only: pick it and leave the plant to grow more, planting nothing (harvest runs). */
	LEAVE_STANDING("Pick, leave it standing");

	/** Trees with a crop to pick before clearing: fruit, calquat fruit, celastrus bark. */
	private static final Set<PatchImplementation> HARVESTED = Set.of(
		PatchImplementation.FRUIT_TREE, PatchImplementation.CALQUAT, PatchImplementation.CELASTRUS);

	/** Kinds whose crop grows back after picking, from RuneLite's crop data (a regrow time): bushes, fruit trees, cacti. */
	private static final Set<PatchImplementation> REGROWING = Arrays.stream(Produce.values())
		.filter(p -> p.getRegrowTickrate() > 0 && p.getPatchImplementation() != null)
		.map(Produce::getPatchImplementation)
		.collect(Collectors.toUnmodifiableSet());

	private final String title;

	/** True for tree kinds whose crop is picked before the tree is cleared. */
	public static boolean hasHarvest(PatchImplementation kind)
	{
		return HARVESTED.contains(kind);
	}

	/** True for kinds whose crop grows back, so they can be picked and left standing. */
	public static boolean regrows(PatchImplementation kind)
	{
		return REGROWING.contains(kind);
	}

	@Override
	public String toString()
	{
		return title;
	}
}
