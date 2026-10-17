/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Item counts across every {@link ItemSource}. Immutable: an update returns a new ledger, so the panel (Swing
 * thread) can read one while the client thread builds the next.
 */
public final class ItemLedger
{
	public static final ItemLedger EMPTY = new ItemLedger(new EnumMap<>(ItemSource.class));

	private final Map<ItemSource, StorageSnapshot> snapshots;

	private ItemLedger(EnumMap<ItemSource, StorageSnapshot> snapshots)
	{
		this.snapshots = snapshots;
	}

	public ItemLedger with(ItemSource source, StorageSnapshot snapshot)
	{
		EnumMap<ItemSource, StorageSnapshot> copy = new EnumMap<>(ItemSource.class);
		copy.putAll(snapshots);
		copy.put(source, snapshot);
		return new ItemLedger(copy);
	}

	public StorageSnapshot snapshot(ItemSource source)
	{
		return snapshots.getOrDefault(source, StorageSnapshot.UNSEEN);
	}

	public int count(int itemId, ItemSource source)
	{
		return snapshot(source).count(itemId);
	}

	/** Total across all sources. */
	public int count(int itemId)
	{
		int total = 0;
		for (StorageSnapshot snapshot : snapshots.values())
		{
			total += snapshot.count(itemId);
		}
		return total;
	}

	/** Total of several ids (e.g. every charge of a skills necklace) across all sources. */
	public int countAny(Collection<Integer> itemIds)
	{
		int total = 0;
		for (int id : itemIds)
		{
			total += count(id);
		}
		return total;
	}
}
