/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Collects requirements, merging ones for the same items: amounts of things used up (seeds, runes, payment) add
 * up, while a kept item (a necklace used for two stops) is still needed once.
 */
final class RequirementList
{
	private final Map<Key, Requirement> merged = new LinkedHashMap<>();

	/**
	 * @param usedUp true when each use takes {@code amount} more (runes, seeds, tablets); false for kept items
	 */
	void add(String label, Set<Integer> itemIds, int amount, Requirement.Importance importance, boolean leprechaun,
		String use, boolean usedUp)
	{
		if (itemIds.isEmpty() || amount <= 0)
		{
			return;
		}
		Key key = new Key(itemIds, importance);
		Requirement old = merged.get(key);
		if (old == null)
		{
			merged.put(key, new Requirement(label, Set.copyOf(itemIds), amount, importance, leprechaun, List.of(use)));
			return;
		}
		List<String> uses = new ArrayList<>(old.getUses());
		if (!uses.contains(use))
		{
			uses.add(use);
		}
		int total = usedUp ? old.getAmount() + amount : Math.max(old.getAmount(), amount);
		merged.put(key, new Requirement(old.getLabel(), old.getItemIds(), total, importance,
			old.isStoredByLeprechaun() || leprechaun, List.copyOf(uses)));
	}

	List<Requirement> toList()
	{
		return List.copyOf(merged.values());
	}

	/**
	 * Both lists as one: a requirement in {@code more} for the same items as one in {@code base} is merged into it,
	 * amounts added, since everything in {@code more} is used up (the compost bin's produce and buckets).
	 */
	static List<Requirement> combine(List<Requirement> base, List<Requirement> more)
	{
		Map<Key, Requirement> all = new LinkedHashMap<>();
		base.forEach(r -> all.put(new Key(r.getItemIds(), r.getImportance()), r));
		for (Requirement r : more)
		{
			all.merge(new Key(r.getItemIds(), r.getImportance()), r, (old, added) ->
			{
				List<String> uses = new ArrayList<>(old.getUses());
				added.getUses().stream().filter(u -> !uses.contains(u)).forEach(uses::add);
				return new Requirement(old.getLabel(), old.getItemIds(), old.getAmount() + added.getAmount(),
					old.getImportance(), old.isStoredByLeprechaun() || added.isStoredByLeprechaun(), List.copyOf(uses));
			});
		}
		return List.copyOf(all.values());
	}

	private static final class Key
	{
		private final Set<Integer> itemIds;
		private final Requirement.Importance importance;

		Key(Set<Integer> itemIds, Requirement.Importance importance)
		{
			this.itemIds = Set.copyOf(itemIds);
			this.importance = importance;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Key && ((Key) o).itemIds.equals(itemIds) && ((Key) o).importance == importance;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(itemIds, importance);
		}
	}
}
