/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Value;

/** A way of getting to a location, and what it needs in the inventory. */
@Value
public class Teleport
{
	/** Value of {@link #waivedByVarbitId} when nothing waives the items. */
	public static final int NOT_WAIVED = -1;

	String id;
	String name;
	/** Where the "Add a teleport" menu lists it, e.g. "Jewellery" or "Lunar spells". */
	String group;
	TeleportType type;
	/** For items and fairy rings: any one of these ids will do. */
	Set<Integer> itemIds;
	/** For spells: "Normal", "Lunar", "Arceuus" or "Ancient". */
	@Nullable
	String spellbook;
	/** For spells: the runes per cast. */
	List<ItemAmount> runes;
	/** A varbit that, when set, means the items aren't needed (elite Lumbridge diary for fairy rings). */
	int waivedByVarbitId;
	/** Where it can go; empty when it goes to one place, named by the method itself ("Varrock Teleport"). */
	List<TeleportDestination> destinations;

	/** The destination with this id, or null. */
	public TeleportDestination destination(String destinationId)
	{
		return destinations.stream().filter(d -> d.getId().equals(destinationId)).findFirst().orElse(null);
	}
}
