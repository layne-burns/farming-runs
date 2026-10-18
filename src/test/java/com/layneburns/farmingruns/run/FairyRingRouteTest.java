/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Teleport;
import com.layneburns.farmingruns.data.TeleportType;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.state.StorageSnapshot;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

public class FairyRingRouteTest
{
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	@Test
	public void aWayHomeMustBeCarried()
	{
		Teleport house = data.teleports().stream().filter(t -> t.getType() == TeleportType.HOUSE).findFirst()
			.orElseThrow();
		int cape = data.getItems().group("construction_cape").iterator().next();
		int tablet = data.getItems().group("house_tablet").iterator().next();
		ItemLedger banked = ItemLedger.EMPTY.with(ItemSource.BANK, new StorageSnapshot(Map.of(cape, 1), 1));
		assertFalse(FairyRingRoute.canGoHome(banked, data.getItems(), house, "Normal"));
		// House tablets don't count: they aren't for runs.
		ItemLedger tablets = banked.with(ItemSource.INVENTORY, new StorageSnapshot(Map.of(tablet, 5), 1));
		assertFalse(FairyRingRoute.canGoHome(tablets, data.getItems(), house, "Normal"));
		ItemLedger worn = banked.with(ItemSource.EQUIPMENT, new StorageSnapshot(Map.of(cape, 1), 1));
		assertTrue(FairyRingRoute.canGoHome(worn, data.getItems(), house, "Normal"));
	}

	@Test
	public void theOverheadLineSaysViaHouse()
	{
		assertEquals("Next: Great Conch · Fairy ring CKQ via house teleport",
			Reminder.travel("Great Conch", data.teleportChoice("fairy_ring:ckq"), true));
	}
}
