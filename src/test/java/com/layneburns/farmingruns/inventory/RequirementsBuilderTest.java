/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.run.CompostChoice;
import com.layneburns.farmingruns.run.KindPlan;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.run.RunSetup;
import com.layneburns.farmingruns.run.TreeRoutine;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.PlayerFacts;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;

public class RequirementsBuilderTest
{
	private static final int RANARR = 5295;
	private static final int PAPAYA_SAPLING = 5501;
	private static final int PINEAPPLE = 2114;
	private static final int LAW_RUNE = 563;
	private static final int ASTRAL_RUNE = 9075;
	private static final PlayerFacts NORMAL_BOOK = new PlayerFacts(true, Map.of(), Map.of(), Map.of(), 0);
	private static GameData data;
	private static Map<String, List<String>> defaults;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
		defaults = data.locations().stream().collect(Collectors.toMap(Location::getId, Location::getDefaultTeleports));
	}

	private static PlantingPlan plan(Seed seed, String... patchIds)
	{
		return new PlantingPlan(List.of(patchIds).stream()
			.map(id -> new PlantingPlan.Assignment(data.patch(id), seed, false)).collect(Collectors.toList()), List.of());
	}

	private static RunRequirements build(RunSetup setup, PlantingPlan plan)
	{
		return RequirementsBuilder.build(setup, plan, defaults, data, NORMAL_BOOK, ItemLedger.EMPTY, p -> true);
	}

	private static Requirement find(RunRequirements needs, int itemId)
	{
		return needs.getRequirements().stream().filter(r -> r.getItemIds().contains(itemId)).findFirst().orElse(null);
	}

	@Test
	public void aHerbRunNeedsSeedsCompostToolsAndTeleports()
	{
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.HERB));
		RunRequirements needs = build(setup, plan(data.seedByItem(RANARR), "catherby.herb", "falador.herb"));
		assertEquals(2, find(needs, RANARR).getAmount());
		Requirement compost = find(needs, data.getItems().group("supercompost").iterator().next());
		assertEquals(2, compost.getAmount());
		assertTrue(compost.isStoredByLeprechaun());
		assertEquals(Requirement.Importance.NEED, find(needs, data.getItems().group("spade").iterator().next())
			.getImportance());
		// Catherby: Camelot Teleport, first in its list. Falador: the Explorer's ring, first in its list.
		assertEquals("camelot_teleport", needs.getTeleports().get("catherby").getRef());
		assertEquals("explorers_ring", needs.getTeleports().get("falador").getRef());
		assertEquals(1, find(needs, LAW_RUNE).getAmount());
		// Herbs can't be protected, so nothing to pay; extras you don't own aren't listed.
		assertNull(find(needs, data.getItems().group("coins").iterator().next()));
		assertNull(find(needs, data.getItems().group("farmers_boots").iterator().next()));
	}

	@Test
	public void fruitTreesNeedPaymentAndCoinsToClear()
	{
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.FRUIT_TREE))
			.withKindPlan(PatchImplementation.FRUIT_TREE, KindPlan.AUTO.withPay(true));
		Seed papaya = data.seedByItem(PAPAYA_SAPLING);
		RunRequirements needs = build(setup, plan(papaya, "gnome_stronghold.fruit_tree", "catherby_fruit_tree.fruit_tree"));
		assertEquals(2, find(needs, PAPAYA_SAPLING).getAmount());
		assertEquals(2 * papaya.getPayment().get(0).getAmount(), find(needs, PINEAPPLE).getAmount());
		assertEquals(400, find(needs, data.getItems().group("coins").iterator().next()).getAmount());
	}

	@Test
	public void aHarvestRunNeedsNoSaplingsPaymentOrCoins()
	{
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.FRUIT_TREE))
			.withKindPlan(PatchImplementation.FRUIT_TREE, KindPlan.AUTO.withPay(true)
				.withTreeRoutine(TreeRoutine.LEAVE_STANDING));
		// The seed picker plants nothing on a harvest run.
		RunRequirements needs = build(setup, plan(null, "gnome_stronghold.fruit_tree"));
		assertNull(find(needs, PINEAPPLE));
		assertNull(find(needs, data.getItems().group("coins").iterator().next()));
		assertNotNull(needs.getTeleports().get("gnome_stronghold"));
	}

	@Test
	public void fertileSoilNeedsRunesPerPatchAndTheLunarSpellbook()
	{
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.HERB)).withCompost(CompostChoice.FERTILE_SOIL);
		RunRequirements needs = build(setup, plan(data.seedByItem(RANARR), "catherby.herb", "falador.herb"));
		assertEquals(6, find(needs, ASTRAL_RUNE).getAmount());
		assertTrue(needs.getNotes().stream().anyMatch(n -> n.contains("Lunar")));
		assertFalse(needs.getRequirements().stream().anyMatch(r -> r.getLabel().equals("Supercompost")));
	}
}
