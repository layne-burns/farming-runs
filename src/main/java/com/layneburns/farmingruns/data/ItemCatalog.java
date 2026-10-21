/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * Named groups of item ids from items.json ("spade", "axe", "farmers_boots"...): any id in a group counts as
 * having that item. Also each rune's id, the spells a run casts (Fertile Soil), and the payments you pack yourself.
 */
@RequiredArgsConstructor
public final class ItemCatalog
{
	private final Map<String, Set<Integer>> groups;
	/** What each group is for, by group name. */
	private final Map<String, String> descriptions;
	private final Map<String, Integer> runes;
	/** Non-teleport spells a run can cast (Fertile Soil), by id. */
	private final Map<String, Spell> spells;
	/** Baskets and sacks of produce, in the order the Making tab lists them. */
	private final List<Pack> packs;

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

	public List<Pack> packs()
	{
		return packs;
	}

	public Spell spell(String id)
	{
		Spell spell = spells.get(id);
		if (spell == null)
		{
			throw new IllegalArgumentException("no spell " + id + " in items.json");
		}
		return spell;
	}

	/**
	 * A readable name from the group an item belongs to ("magic_secateurs" becomes "Magic secateurs"), or null if
	 * it's in no group. Lets the panel name tools without asking the game client.
	 */
	public String displayName(int itemId)
	{
		String group = groupOf(itemId);
		if (group == null)
		{
			return null;
		}
		String words = group.replace('_', ' ');
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	/** What the item is for (from items.in.json), or null if it's in no described group. */
	public String description(int itemId)
	{
		String group = groupOf(itemId);
		return group == null ? null : descriptions.get(group);
	}

	private String groupOf(int itemId)
	{
		for (Map.Entry<String, Set<Integer>> group : groups.entrySet())
		{
			if (group.getValue().contains(itemId))
			{
				return group.getKey();
			}
		}
		return null;
	}
}
