/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.util.List;
import lombok.Value;

/**
 * A built-in goal preset (SPEC §2.3): something you want to make, e.g. Prayer potion, and the farmable crops it
 * needs. From goal_presets.json.
 */
@Value
public class GoalPreset
{
	@Value
	public static class Crop
	{
		Seed seed;
		/** Harvested items one product needs, e.g. 1 ranarr per prayer potion. */
		double perProduct;
		/** Average harvest per patch; 0 when not meaningful (trees grown for logs or XP). */
		double avgYieldPerPatch;
	}

	String id;
	String name;
	/** "Potions", "Cooking", "Crafting & other", "Farming XP" or "Payment supplies". */
	String category;
	/** Why it's worth having, shown as a tooltip. */
	String why;
	List<Crop> crops;
	/** Ingredients that don't come from farming, e.g. red spiders' eggs. */
	List<String> notFarmed;
	int minFarmingLevel;
}
