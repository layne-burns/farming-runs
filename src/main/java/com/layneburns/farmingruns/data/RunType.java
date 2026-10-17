/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The kinds of run you'd go on, each covering one or more patch kinds. Used to group the Locations tab, and by the
 * run setup to choose what to plant.
 */
@Getter
@RequiredArgsConstructor
public enum RunType
{
	HERB("Herb", Set.of(PatchImplementation.HERB)),
	ALLOTMENT("Allotment", Set.of(PatchImplementation.ALLOTMENT)),
	FLOWER("Flower", Set.of(PatchImplementation.FLOWER)),
	HOPS("Hops", Set.of(PatchImplementation.HOPS)),
	BUSH("Bush", Set.of(PatchImplementation.BUSH)),
	TREE("Tree", Set.of(PatchImplementation.TREE)),
	FRUIT_TREE("Fruit tree", Set.of(PatchImplementation.FRUIT_TREE)),
	HARDWOOD("Hardwood", Set.of(PatchImplementation.HARDWOOD_TREE)),
	SPECIAL_TREES("Special trees", Set.of(PatchImplementation.CALQUAT, PatchImplementation.CELASTRUS,
		PatchImplementation.REDWOOD, PatchImplementation.SPIRIT_TREE, PatchImplementation.CRYSTAL_TREE)),
	CACTUS("Cactus", Set.of(PatchImplementation.CACTUS)),
	SPECIAL("Special", Set.of(PatchImplementation.MUSHROOM, PatchImplementation.BELLADONNA,
		PatchImplementation.GRAPES, PatchImplementation.SEAWEED, PatchImplementation.CORAL,
		PatchImplementation.HESPORI, PatchImplementation.ANIMA));

	private final String title;
	private final Set<PatchImplementation> kinds;

	public boolean covers(PatchImplementation kind)
	{
		return kinds.contains(kind);
	}
}
