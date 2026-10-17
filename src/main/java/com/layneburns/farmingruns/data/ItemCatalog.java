/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * Named groups of item ids from items.json ("spade", "axe", "farmers_boots"...): any id in a group counts as
 * having that item. Also each rune's id.
 */
@RequiredArgsConstructor
public final class ItemCatalog
{
	private final Map<String, Set<Integer>> groups;
	private final Map<String, Integer> runes;

	public Set<Integer> group(String name)
	{
		Set<Integer> ids = groups.get(name);
		if (ids == null)
		{
			throw new IllegalArgumentException("no item group " + name + " in items.json");
		}
		return ids;
	}

	public Set<String> groupNames()
	{
		return groups.keySet();
	}

	public Map<String, Integer> runes()
	{
		return runes;
	}
}
