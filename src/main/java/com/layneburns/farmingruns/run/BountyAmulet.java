/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.List;
import java.util.Set;

/**
 * Whether to wear an amulet of bounty for the allotments (25% chance to plant with one seed instead of three).
 * Worth it for the dearer seeds: by default, when any allotment seed planted is above the lower of strawberry and
 * watermelon in level (so watermelon and snape grass). Your choice on the allotment card overrides that. Pure.
 */
public final class BountyAmulet
{
	private static final Set<String> THRESHOLD_SEEDS = Set.of("Strawberry seed", "Watermelon seed");

	private BountyAmulet()
	{
	}

	/** Your choice if you made one, else the default for what's being planted. */
	public static boolean wanted(KindPlan plan, List<PlantingPlan.Assignment> allotments, GameData data)
	{
		return plan.getAmuletOfBounty() != null ? plan.getAmuletOfBounty() : byDefault(allotments, data);
	}

	/** On when an allotment seed planted is above the lower level of strawberry and watermelon. */
	public static boolean byDefault(List<PlantingPlan.Assignment> allotments, GameData data)
	{
		int threshold = threshold(data);
		return allotments.stream().anyMatch(a -> a.getSeed() != null && a.getSeed().getLevel() > threshold);
	}

	static int threshold(GameData data)
	{
		return data.seedsFor(PatchImplementation.ALLOTMENT).stream()
			.filter(s -> THRESHOLD_SEEDS.contains(s.getName()))
			.mapToInt(Seed::getLevel)
			.min().orElse(Integer.MAX_VALUE);
	}
}
