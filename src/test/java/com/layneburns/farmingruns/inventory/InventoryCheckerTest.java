/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.layneburns.farmingruns.inventory.InventoryChecker.Line;
import com.layneburns.farmingruns.inventory.InventoryChecker.Status;
import com.layneburns.farmingruns.inventory.Requirement.Importance;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.state.StorageSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class InventoryCheckerTest
{
	private static final int SUPERCOMPOST = 6034;
	private static final int RANARR = 5295;
	private static final int SEED_BOX = 24482;

	private static Requirement need(int itemId, int amount, boolean leprechaun)
	{
		return new Requirement("item " + itemId, Set.of(itemId), amount, Importance.NEED, leprechaun, List.of("test"));
	}

	private static ItemLedger ledger(Object... sourceThenItems)
	{
		ItemLedger ledger = ItemLedger.EMPTY;
		for (int i = 0; i < sourceThenItems.length; i += 2)
		{
			@SuppressWarnings("unchecked")
			Map<Integer, Integer> items = (Map<Integer, Integer>) sourceThenItems[i + 1];
			ledger = ledger.with((ItemSource) sourceThenItems[i], new StorageSnapshot(items, 1));
		}
		return ledger;
	}

	private static Line only(Requirement r, ItemLedger ledger)
	{
		return InventoryChecker.check(List.of(r), ledger, Set.of(SEED_BOX)).get(0);
	}

	@Test
	public void carriedFirstThenTheLeprechaunThenTheBank()
	{
		ItemLedger ledger = ledger(ItemSource.INVENTORY, Map.of(SUPERCOMPOST, 2),
			ItemSource.LEPRECHAUN, Map.of(SUPERCOMPOST, 2), ItemSource.BANK, Map.of(SUPERCOMPOST, 10));
		Line line = only(need(SUPERCOMPOST, 5, true), ledger);
		assertEquals(Status.WITHDRAW, line.getStatus());
		assertEquals(2, line.getCarried());
		assertEquals(2, line.getFromLeprechaun());
		assertEquals(1, line.getToWithdraw());
		assertEquals(0, line.getShortBy());

		assertEquals(Status.FROM_LEPRECHAUN, only(need(SUPERCOMPOST, 4, true), ledger).getStatus());
		assertEquals(Status.OK, only(need(SUPERCOMPOST, 2, true), ledger).getStatus());
	}

	@Test
	public void theLeprechaunOnlyCountsForWhatItStores()
	{
		ItemLedger ledger = ledger(ItemSource.LEPRECHAUN, Map.of(SUPERCOMPOST, 5));
		Line line = only(need(SUPERCOMPOST, 5, false), ledger);
		assertEquals(Status.MISSING, line.getStatus());
		assertEquals(5, line.getShortBy());
		assertTrue(line.blocksStart());
	}

	@Test
	public void seedsInTheSeedBoxAreCarriedOnlyWhenTheBoxIs()
	{
		ItemLedger boxInBank = ledger(ItemSource.SEED_BOX, Map.of(RANARR, 6), ItemSource.BANK, Map.of(SEED_BOX, 1));
		assertEquals(Status.WITHDRAW, only(need(RANARR, 6, false), boxInBank).getStatus());

		ItemLedger boxCarried = boxInBank.with(ItemSource.INVENTORY, new StorageSnapshot(Map.of(SEED_BOX, 1), 1));
		assertEquals(Status.OK, only(need(RANARR, 6, false), boxCarried).getStatus());
	}

	@Test
	public void missingExtrasDontBlockTheStart()
	{
		Requirement rake = new Requirement("Rake", Set.of(5341), 1, Importance.RECOMMENDED, true, List.of("weeds"));
		Line line = only(rake, ItemLedger.EMPTY);
		assertEquals(Status.MISSING, line.getStatus());
		assertFalse(line.blocksStart());
	}

	@Test
	public void aWateredSeedlingCountsAsItsSapling()
	{
		// Yew sapling 5373, watered yew seedling 5367: a requirement for the sapling accepts either.
		Requirement saplings = new Requirement("Yew sapling", Set.of(5373, 5367), 2, Importance.NEED, false,
			List.of("2 tree patches"));
		ItemLedger ledger = ledger(ItemSource.INVENTORY, Map.of(5373, 1, 5367, 1));
		assertEquals(Status.OK, only(saplings, ledger).getStatus());
	}
}
