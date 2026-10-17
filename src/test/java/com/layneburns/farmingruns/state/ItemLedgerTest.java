/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ItemLedgerTest
{
	@Test
	public void countsAcrossSources()
	{
		ItemLedger ledger = ItemLedger.EMPTY
			.with(ItemSource.BANK, new StorageSnapshot(Map.of(1, 10, 2, 3), 100))
			.with(ItemSource.INVENTORY, new StorageSnapshot(Map.of(1, 2), 200));
		assertEquals(12, ledger.count(1));
		assertEquals(10, ledger.count(1, ItemSource.BANK));
		assertEquals(0, ledger.count(1, ItemSource.SEED_VAULT));
		assertEquals(15, ledger.countAny(List.of(1, 2)));
	}

	@Test
	public void updatesDoNotChangeTheOldLedger()
	{
		ItemLedger before = ItemLedger.EMPTY.with(ItemSource.BANK, new StorageSnapshot(Map.of(1, 1), 1));
		ItemLedger after = before.with(ItemSource.BANK, new StorageSnapshot(Map.of(1, 5), 2));
		assertEquals(1, before.count(1));
		assertEquals(5, after.count(1));
	}

	@Test
	public void unseenSourcesAreEmpty()
	{
		assertFalse(ItemLedger.EMPTY.snapshot(ItemSource.SEED_VAULT).isSeen());
		assertEquals(0, ItemLedger.EMPTY.count(1));
	}
}
