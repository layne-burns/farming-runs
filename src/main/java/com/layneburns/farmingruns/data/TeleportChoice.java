/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.util.Set;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * A teleport as a location uses it: a method and, if it has several, the destination. Written in the data and in
 * your saved choices as "method" or "method:destination" (e.g. "skills_necklace:fishing_guild", "fairy_ring:cir").
 */
@Value
public class TeleportChoice
{
	/** The "method" or "method:destination" text this was read from. */
	String ref;
	Teleport method;
	@Nullable
	TeleportDestination destination;

	/** E.g. "Skills necklace → Fishing Guild", "Fairy ring CIR", or just "Varrock Teleport". */
	public String displayName()
	{
		if (destination == null)
		{
			return method.getName();
		}
		if (method.getType() == TeleportType.FAIRY_RING)
		{
			return method.getName() + " " + destination.getName();
		}
		return method.getName() + " → " + destination.getName();
	}

	/** The items that make this teleport work: the destination's own list if it has one, else the method's. */
	public Set<Integer> itemIds()
	{
		return destination != null && !destination.getItemIds().isEmpty() ? destination.getItemIds() : method.getItemIds();
	}

	/** "method" or "method:destination". */
	public static String ref(String methodId, @Nullable String destinationId)
	{
		return destinationId == null ? methodId : methodId + ":" + destinationId;
	}
}
