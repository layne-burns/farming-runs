/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

/** A seed: the kind of patch it goes in, its level, how many go in a patch, and the gardener's payment. */
@Value
public class Seed
{
	/** Value of {@link #saplingItemId} for seeds planted directly. */
	public static final int NO_SAPLING = -1;

	String name;
	int itemId;
	PatchImplementation kind;
	int level;
	/** Seeds per patch: 3 for allotments, 4 for hops, else 1. */
	int perPatch;
	/** For trees: the sapling actually planted, else {@link #NO_SAPLING}. */
	int saplingItemId;
	/** For trees: the sapling's name, e.g. "Apple sapling"; null for seeds planted directly. */
	@Nullable
	String saplingName;
	/** The gardener's protection payment; empty when the crop can't be protected. */
	List<ItemAmount> payment;
	SeedXp xp;
	/** How long it takes to grow, e.g. "1 h 20 min"; null if unknown. */
	@Nullable
	String growth;

	public boolean isPlantedAsSapling()
	{
		return saplingItemId != NO_SAPLING;
	}

	public boolean canBeProtected()
	{
		return !payment.isEmpty();
	}
}
