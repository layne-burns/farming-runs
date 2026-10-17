/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Loads the real data files. Loading itself checks that every reference resolves (DataLoader throws if not);
 * the tests below check facts across files and a few values known to be right in game.
 */
public class DataIntegrityTest
{
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	@Test
	public void everyPatchKindHasSeeds()
	{
		for (Patch patch : data.patches())
		{
			assertFalse("no seeds for " + patch.getKind() + " (" + patch.getId() + ")",
				data.seedsFor(patch.getKind()).isEmpty());
		}
	}

	@Test
	public void everyPatchKindIsInExactlyOneRunType()
	{
		for (Patch patch : data.patches())
		{
			long types = Arrays.stream(RunType.values()).filter(t -> t.covers(patch.getKind())).count();
			assertEquals(patch.getKind() + " should be in one run type", 1, types);
		}
	}

	@Test
	public void timeTrackingKeysAreUnique()
	{
		// Time Tracking saves each patch under its region and varbit; two patches sharing both would collide.
		Set<String> keys = new HashSet<>();
		for (Patch patch : data.patches())
		{
			assertTrue("shared region+varbit: " + patch.getId(),
				keys.add(patch.getRegionId() + "." + patch.getVarbitId()));
		}
	}

	@Test
	public void everyPatchHasATile()
	{
		for (Patch patch : data.patches())
		{
			assertTrue("no wiki tile for " + patch.getId(), patch.getTile() != null);
		}
	}

	@Test
	public void saplingsDifferFromTheirSeeds()
	{
		for (Seed seed : data.seeds())
		{
			if (seed.isPlantedAsSapling())
			{
				assertNotEquals(seed.getName(), seed.getItemId(), seed.getSaplingItemId());
				assertEquals(seed, data.seedByItem(seed.getSaplingItemId()));
			}
		}
	}

	@Test
	public void knownPayments()
	{
		Seed ranarr = data.seedByItem(ItemID.RANARR_SEED);
		assertEquals(PatchImplementation.HERB, ranarr.getKind());
		assertFalse("herbs can't be protected", ranarr.canBeProtected());

		Seed apple = data.seedByItem(ItemID.APPLE_TREE_SEED);
		assertEquals(1, apple.getPayment().size());
		assertEquals(ItemID.SWEETCORN, apple.getPayment().get(0).getItemId());
		assertEquals(9, apple.getPayment().get(0).getAmount());

		assertEquals(3, data.seedByItem(ItemID.POTATO_SEED).getPerPatch());
	}

	@Test
	public void containerPaymentsAreFullBasketsAndSacks()
	{
		// The wiki lists every fill level on one page; payment must be the full one, not Tomatoes(1).
		assertEquals(ItemID.BASKET_TOMATO_5, data.seedByItem(ItemID.ACORN).getPayment().get(0).getItemId());
		assertEquals(ItemID.SACK_POTATO_10, data.seedByItem(ItemID.ONION_SEED).getPayment().get(0).getItemId());
		assertEquals(ItemID.BASKET_APPLE_5, data.seedByItem(ItemID.WILLOW_SEED).getPayment().get(0).getItemId());
	}

	@Test
	public void knownPatches()
	{
		Patch catherbyHerb = data.patch("catherby.herb");
		assertEquals(PatchImplementation.HERB, catherbyHerb.getKind());
		assertEquals(VarbitID.FARMING_TRANSMIT_D, catherbyHerb.getVarbitId());
		assertEquals(11062, catherbyHerb.getRegionId());

		assertEquals("farming_guild_advanced", data.patch("farming_guild.fruit_tree").getUnlockRuleId());
		assertTrue(data.patch("falador.allotment_north_west").hasGardener());
		assertFalse(data.patch("falador.herb").hasGardener());
	}

	@Test
	public void knownTeleports()
	{
		Teleport camelot = data.teleport("camelot_teleport");
		assertEquals(TeleportType.SPELL, camelot.getType());
		assertEquals("Normal", camelot.getSpellbook());
		assertEquals(2, camelot.getRunes().size());

		Teleport fairyRing = data.teleport("fairy_ring");
		assertTrue(fairyRing.getItemIds().contains(ItemID.DRAMEN_STAFF));
		assertEquals(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, fairyRing.getWaivedByVarbitId());

		assertTrue(data.teleport("ectophial").getItemIds().contains(ItemID.ECTOPHIAL));
		assertEquals("ectophial", data.location("port_phasmatys").getDefaultTeleportId());
	}

	@Test
	public void costsUseLinksBothWays()
	{
		CostTable costs = data.getCosts();
		assertEquals(20, costs.cost("catherby", "catherby_fruit_tree"));
		assertEquals(20, costs.cost("catherby_fruit_tree", "catherby"));
		assertEquals(30, costs.cost(null, "catherby"));
		assertEquals(30, costs.cost("weiss", "catherby"));
	}

	@Test
	public void itemGroupsAreFilled()
	{
		for (String group : data.getItems().groupNames())
		{
			assertFalse("empty item group " + group, data.getItems().group(group).isEmpty());
		}
		assertTrue(data.getItems().group("spade").contains(ItemID.SPADE));
	}
}
