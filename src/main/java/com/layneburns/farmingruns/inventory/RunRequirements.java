/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import com.layneburns.farmingruns.data.TeleportChoice;
import java.util.List;
import java.util.Map;
import lombok.Value;

/** What a run needs you to bring, the teleport picked for each stop, and anything worth knowing. */
@Value
public class RunRequirements
{
	public static final RunRequirements NONE = new RunRequirements(List.of(), Map.of(), List.of());

	List<Requirement> requirements;
	/** The teleport for each location visited, by location id; a location you walk to isn't listed. */
	Map<String, TeleportChoice> teleports;
	/** E.g. "Fertile Soil needs the Lunar spellbook (you're on Normal)". */
	List<String> notes;
}
