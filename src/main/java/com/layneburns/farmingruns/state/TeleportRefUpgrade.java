/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.layneburns.farmingruns.data.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Turns teleport names saved by older versions into today's "method:destination" references, so a saved order keeps
 * every entry after a data change. Pure: unit tested.
 */
final class TeleportRefUpgrade
{
	/** Old ids that were one teleport but are now a method with destinations, or were renamed. */
	private static final Map<String, String> RENAMED = Map.of(
		"ardougne_cloak_farm", "ardougne_cloak:farm",
		"ardougne_cloak_monastery", "ardougne_cloak:monastery",
		"none", "walk",
		"skills_necklace", "skills_necklace:farming_guild",
		"xerics_talisman", "xerics_talisman:glade",
		"teleport_crystal", "teleport_crystal:lletya",
		"digsite_pendant", "digsite_pendant:fossil_island",
		"ring_of_dueling", "ring_of_dueling:emirs_arena",
		"combat_bracelet", "combat_bracelet:champions_guild");

	private TeleportRefUpgrade()
	{
	}

	/**
	 * Today's reference for a saved one: unchanged if still valid; else the location's own default with that method
	 * (old "fairy_ring" at Ardougne is "fairy_ring:blr"); else the renamed id; else null (dropped).
	 */
	@Nullable
	static String upgrade(String saved, Location location, Predicate<String> valid)
	{
		if (valid.test(saved))
		{
			return saved;
		}
		for (String ref : location.getDefaultTeleports())
		{
			if (ref.startsWith(saved + ":"))
			{
				return ref;
			}
		}
		String renamed = RENAMED.get(saved);
		return renamed != null && valid.test(renamed) ? renamed : null;
	}

	/** A whole saved order, upgraded, without duplicates or entries that can't be upgraded. */
	static List<String> upgradeAll(List<String> saved, Location location, Predicate<String> valid)
	{
		List<String> upgraded = new ArrayList<>();
		for (String ref : saved)
		{
			String now = upgrade(ref, location, valid);
			if (now != null && !upgraded.contains(now))
			{
				upgraded.add(now);
			}
		}
		return upgraded;
	}
}
