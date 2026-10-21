/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;

/**
 * Which compost each kind of patch gets on a run: the kind's own choice if it has one, else the run's
 * ({@link RunSetup#compostFor}). So allotments can go without while herbs get supercompost.
 */
@FunctionalInterface
public interface CompostPlan
{
	CompostChoice forKind(PatchImplementation kind);

	/** The same compost for every kind. */
	static CompostPlan all(CompostChoice compost)
	{
		return kind -> compost;
	}

	/** False for patches that can't be composted: coral. */
	static boolean takesCompost(PatchImplementation kind)
	{
		return kind != PatchImplementation.CORAL;
	}

	/** True when this compost is an item used on the patch (not none, and not the Fertile Soil spell). */
	static boolean usesItems(CompostChoice compost)
	{
		return compost != CompostChoice.NONE && compost != CompostChoice.FERTILE_SOIL;
	}
}
