/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.data.TeleportType;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The steps of the way to a stop, for the overhead line: "House teleport → Weiss portal", or for a fairy ring reached
 * through your house "House teleport → Salve Graveyard portal → Fairy ring CKQ". Each teleport-sized jump on the way
 * takes one off the front ({@link LegTimer#jumps()}); walking stays until you're there. Where nothing more is known,
 * the way you went last time is used ({@link RouteLearner}). Pure: unit tested.
 */
public final class TravelChain
{
	public static final String WALK = "walk";

	private TravelChain()
	{
	}

	/**
	 * @param fairyRingVia for a fairy ring leg: the house portal that gets you to a ring (e.g. "Salve Graveyard"), or
	 *                     null to go to the ring straight
	 */
	public static List<String> steps(@Nullable TeleportChoice teleport, @Nullable String fairyRingVia)
	{
		return steps(teleport, fairyRingVia, null);
	}

	/**
	 * @param learned the steps you took last time with this teleport planned ({@link RouteLearner}); used when there's
	 *                nothing better known than the teleport alone
	 */
	public static List<String> steps(@Nullable TeleportChoice teleport, @Nullable String fairyRingVia,
		@Nullable List<String> learned)
	{
		List<String> known = known(teleport, fairyRingVia);
		return known.size() == 1 && learned != null && learned.size() > 1 ? learned : known;
	}

	private static List<String> known(@Nullable TeleportChoice teleport, @Nullable String fairyRingVia)
	{
		if (teleport == null || teleport.getMethod().getType() == TeleportType.WALK)
		{
			return List.of(WALK);
		}
		if (teleport.getMethod().getType() == TeleportType.HOUSE && teleport.getDestination() != null)
		{
			return List.of("House teleport", teleport.getDestination().getName() + " portal");
		}
		if (teleport.getMethod().getType() == TeleportType.FAIRY_RING && fairyRingVia != null)
		{
			return List.of("House teleport", fairyRingVia + " portal", teleport.displayName());
		}
		return List.of(teleport.displayName());
	}

	/** The steps still to take after {@code jumps} of them; empty once they're all done. Walking never runs out. */
	public static List<String> remaining(List<String> steps, int jumps)
	{
		if (steps.equals(List.of(WALK)))
		{
			return steps;
		}
		return steps.subList(Math.min(Math.max(0, jumps), steps.size()), steps.size());
	}
}
