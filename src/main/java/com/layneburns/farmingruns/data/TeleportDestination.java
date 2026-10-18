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
import net.runelite.api.coords.WorldPoint;

/** One place a teleport method goes, e.g. the skills necklace's "Fishing Guild" or fairy ring "CIR". */
@Value
public class TeleportDestination
{
	String id;
	/** E.g. "Fishing Guild", or a fairy ring's code ("CIR"). */
	String name;
	/** More about where it is, e.g. a fairy ring's "Kebos Lowlands: South of Mount Karuulm"; null if none. */
	@Nullable
	String detail;
	/** Items that reach this destination when only some of the method's do (Ardougne cloak 2+ for the farm); empty to use the method's. */
	Set<Integer> itemIds;
	/** Where it lands, when known (from Shortest Path's table): tells apart where a "Rub" took you. */
	@Nullable
	WorldPoint landing;
}
