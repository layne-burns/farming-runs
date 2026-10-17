/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** What to do with a grown tree before replanting (SPEC §2.3), set per tree kind. */
@Getter
@RequiredArgsConstructor
public enum TreeRoutine
{
	/** Check health, then pay the gardener 200 coins to clear it. */
	PAY_TO_REMOVE("Check health, pay to remove"),
	/** Check health, chop it down and dig up the roots (needs an axe). */
	CHOP_AND_DIG("Check health, chop and dig");

	/** Trees with a crop to pick before clearing: fruit, calquat fruit, celastrus bark. */
	private static final Set<PatchImplementation> HARVESTED = Set.of(
		PatchImplementation.FRUIT_TREE, PatchImplementation.CALQUAT, PatchImplementation.CELASTRUS);

	private final String title;

	/** True for tree kinds whose crop is picked before the tree is cleared. */
	public static boolean hasHarvest(PatchImplementation kind)
	{
		return HARVESTED.contains(kind);
	}

	@Override
	public String toString()
	{
		return title;
	}
}
