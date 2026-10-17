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
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;

public class BountyAmuletTest
{
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	private static List<PlantingPlan.Assignment> planting(int seedItemId)
	{
		return List.of(new PlantingPlan.Assignment(data.patch("catherby.allotment_north"), data.seedByItem(seedItemId),
			false));
	}

	@Test
	public void theThresholdIsStrawberrysLevel()
	{
		assertEquals(31, BountyAmulet.threshold(data));
	}

	@Test
	public void onByDefaultAboveStrawberry()
	{
		assertTrue(BountyAmulet.wanted(KindPlan.AUTO, planting(ItemID.SNAPE_GRASS_SEED), data));
		assertTrue(BountyAmulet.wanted(KindPlan.AUTO, planting(ItemID.WATERMELON_SEED), data));
		assertFalse(BountyAmulet.wanted(KindPlan.AUTO, planting(ItemID.STRAWBERRY_SEED), data));
		assertFalse(BountyAmulet.wanted(KindPlan.AUTO, planting(ItemID.POTATO_SEED), data));
	}

	@Test
	public void yourChoiceWins()
	{
		assertFalse(BountyAmulet.wanted(KindPlan.AUTO.withAmuletOfBounty(false), planting(ItemID.SNAPE_GRASS_SEED), data));
		assertTrue(BountyAmulet.wanted(KindPlan.AUTO.withAmuletOfBounty(true), planting(ItemID.POTATO_SEED), data));
	}
}
