/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.data.SeedXp;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** How your chosen seeds for a patch kind are given patches. */
@Getter
@RequiredArgsConstructor
public enum FillOrder
{
	/** Patches split by each seed's share or exact number (SPEC §2.3). */
	SHARES("By share"),
	/** The highest-XP seed fills patches first, as far as your spare goes, then the next. */
	MOST_XP("Most XP first");

	private final String title;

	@Override
	public String toString()
	{
		return title;
	}

	/**
	 * How much XP a patch of this seed gives, for ordering: the wiki's total per patch if known, else planting plus
	 * checking health; the level breaks ties (and stands in when nothing is known).
	 */
	public static double xpScore(Seed seed)
	{
		SeedXp xp = seed.getXp();
		double score = xp.getTotal() > 0 ? xp.getTotal() : xp.getPlant() + xp.getCheckHealth();
		return score + seed.getLevel() / 1000.0;
	}
}
