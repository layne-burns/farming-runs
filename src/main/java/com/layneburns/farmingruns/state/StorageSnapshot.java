/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import java.util.Map;
import lombok.Value;

/** The tracked items in one place at one moment: item id to quantity, and when it was seen. */
@Value
public class StorageSnapshot
{
	/** Nothing known yet: never seen. */
	public static final StorageSnapshot UNSEEN = new StorageSnapshot(Map.of(), 0);

	Map<Integer, Integer> items;
	/** Epoch milliseconds when this was read from the game; 0 if never. */
	long capturedAtMillis;

	public StorageSnapshot(Map<Integer, Integer> items, long capturedAtMillis)
	{
		this.items = Map.copyOf(items);
		this.capturedAtMillis = capturedAtMillis;
	}

	public int count(int itemId)
	{
		return items.getOrDefault(itemId, 0);
	}

	public boolean isSeen()
	{
		return capturedAtMillis > 0;
	}
}
