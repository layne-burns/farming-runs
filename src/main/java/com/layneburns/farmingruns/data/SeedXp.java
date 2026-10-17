/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Value;

/** Farming experience for a seed, from the wiki. A value is 0 where the wiki gives none. */
@Value
public class SeedXp
{
	double plant;
	double checkHealth;
	/** Per item harvested. */
	double harvest;
	/** The wiki's total for one patch (for herbs, at an average yield). */
	double total;

	static SeedXp from(Map<String, Double> xp)
	{
		if (xp == null)
		{
			return new SeedXp(0, 0, 0, 0);
		}
		return new SeedXp(xp.getOrDefault("plant", 0.0), xp.getOrDefault("checkHealth", 0.0),
			xp.getOrDefault("harvest", 0.0), xp.getOrDefault("total", 0.0));
	}

	/** E.g. "27 plant, 30.5 per harvest", or "" if nothing is known. */
	public String describe()
	{
		List<String> parts = new ArrayList<>();
		add(parts, plant, " plant");
		add(parts, checkHealth, " check health");
		add(parts, harvest, " per harvest");
		if (parts.isEmpty())
		{
			add(parts, total, " total");
		}
		return String.join(", ", parts);
	}

	private static void add(List<String> parts, double value, String what)
	{
		if (value > 0)
		{
			parts.add((value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value)) + what);
		}
	}
}
