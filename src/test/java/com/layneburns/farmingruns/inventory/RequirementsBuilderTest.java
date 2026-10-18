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
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.state.PlayerFacts;
import com.layneburns.farmingruns.state.StorageSnapshot;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.gameval.VarbitID;
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
		return RequirementsBuilder.build(setup, plan, defaults, data, NORMAL_BOOK, ItemLedger.EMPTY, p -> true, false);
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
	public void noRakeWithAutoweed()
	{
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.HERB));
		PlantingPlan herbs = plan(data.seedByItem(RANARR), "catherby.herb");
		int rake = data.getItems().group("rake").iterator().next();
		assertNotNull(find(build(setup, herbs), rake));
		assertNull(find(RequirementsBuilder.build(setup, herbs, defaults, data, NORMAL_BOOK, ItemLedger.EMPTY,
			p -> true, true), rake));
	}

	@Test
	public void aHerbSackIsRecommendedOnHerbRunsWhenYouHaveOne()
	{
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.HERB));
		PlantingPlan herbs = plan(data.seedByItem(RANARR), "catherby.herb");
		int sack = data.getItems().group("herb_sack").iterator().next();
		assertNull(find(build(setup, herbs), sack));
		ItemLedger owned = TeleportPickerTest.ledger(ItemSource.BANK, Map.of(sack, 1));
		Requirement recommended = find(RequirementsBuilder.build(setup, herbs, defaults, data, NORMAL_BOOK, owned,
			p -> true, false), sack);
		assertEquals(Requirement.Importance.RECOMMENDED, recommended.getImportance());
	}

	@Test
	public void underwaterPatchesNeedDivingGear()
	{
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.SEAWEED));
		Seed spore = data.seedsFor(PatchImplementation.SEAWEED).get(0);
		RunRequirements needs = build(setup, plan(spore, "fossil_island_seaweed.seaweed_north"));
		assertEquals(Requirement.Importance.NEED,
			find(needs, data.getItems().group("diving_apparatus").iterator().next()).getImportance());
		assertNotNull(find(needs, data.getItems().group("fishbowl_helmet").iterator().next()));
		// Not on dry land.
		assertNull(find(build(RunSetup.EMPTY.withRunTypes(Set.of(RunType.HERB)),
			plan(data.seedByItem(RANARR), "catherby.herb")), data.getItems().group("diving_apparatus").iterator().next()));
	}

	@Test
	public void saplingsToGrowAskForTheSeedsAndAPotEach()
	{
		Seed yew = data.seedByItem(5315);
		PlantingPlan plan = new PlantingPlan(List.of(new PlantingPlan.Assignment(data.patch("falador_park.tree"), yew,
			false)), List.of(), Map.of(yew, 1));
		RunRequirements needs = build(RunSetup.EMPTY.withRunTypes(Set.of(RunType.TREE)), plan);
		assertEquals(1, find(needs, 5315).getAmount());
		assertEquals(1, find(needs, data.getItems().group("filled_plant_pot").iterator().next()).getAmount());
		assertNotNull(find(needs, data.getItems().group("watering_can").iterator().next()));
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
		// The seed picker keeps the tree standing and plants nothing.
		PlantingPlan kept = new PlantingPlan(List.of(new PlantingPlan.Assignment(
			data.patch("gnome_stronghold.fruit_tree"), null, false, true, null)), List.of());
		RunRequirements needs = build(setup, kept);
		assertNull(find(needs, PINEAPPLE));
		assertNull(find(needs, data.getItems().group("coins").iterator().next()));
		assertNotNull(needs.getTeleports().get("gnome_stronghold"));
	}

	@Test
	public void aReplacedTypeOnAHarvestRunIsPaidToClearAndReplanted()
	{
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.FRUIT_TREE))
			.withKindPlan(PatchImplementation.FRUIT_TREE, KindPlan.AUTO.withTreeRoutine(TreeRoutine.LEAVE_STANDING));
		PlantingPlan plan = new PlantingPlan(List.of(
			new PlantingPlan.Assignment(data.patch("gnome_stronghold.fruit_tree"), null, false, true, null),
			new PlantingPlan.Assignment(data.patch("catherby_fruit_tree.fruit_tree"), data.seedByItem(PAPAYA_SAPLING),
				false)), List.of());
		RunRequirements needs = build(setup, plan);
		assertEquals(1, find(needs, PAPAYA_SAPLING).getAmount());
		assertEquals(200, find(needs, data.getItems().group("coins").iterator().next()).getAmount());
	}

	@Test
	public void aHousePortalNeedsACapeOrTheSpellNeverTablets()
	{
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.HERB));
		PlantingPlan weiss = plan(data.seedByItem(RANARR), "weiss.herb");
		Map<String, List<String>> refs = Map.of("weiss", List.of("house_portal:weiss"));
		int tablet = data.getItems().group("house_tablet").iterator().next();
		int cape = data.getItems().group("construction_cape").iterator().next();

		// Tablets aren't for runs: without a cape it's the spell's runes, even with tablets in the bank.
		ItemLedger tablets = TeleportPickerTest.ledger(ItemSource.BANK, Map.of(tablet, 10));
		RunRequirements withTablets = RequirementsBuilder.build(setup, weiss, refs, data, NORMAL_BOOK, tablets,
			p -> true, false);
		assertNull(find(withTablets, tablet));
		int law = data.teleport("house_portal").getRunes().get(0).getItemId();
		assertNotNull(find(withTablets, law));

		ItemLedger capeToo = tablets.with(ItemSource.EQUIPMENT, new StorageSnapshot(Map.of(cape, 1), 1));
		RunRequirements withCape = RequirementsBuilder.build(setup, weiss, refs, data, NORMAL_BOOK, capeToo,
			p -> true, false);
		assertNotNull(find(withCape, cape));
		assertNull(find(withCape, tablet));
		assertNull(find(withCape, law));
	}

	@Test
	public void hosidiusNeedsNoPaymentAfterTheEasyKourendDiary()
	{
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.ALLOTMENT))
			.withKindPlan(PatchImplementation.ALLOTMENT, KindPlan.AUTO.withPay(true));
		Seed watermelon = data.seedByItem(5321);
		int payment = watermelon.getPayment().get(0).getItemId();
		PlantingPlan hosidius = plan(watermelon, "hosidius.allotment_north_east");
		assertNotNull(find(build(setup, hosidius), payment));
		PlayerFacts diary = new PlayerFacts(true, Map.of(), Map.of(VarbitID.KOUREND_DIARY_EASY_COMPLETE, 1), Map.of(), 0);
		assertNull(find(RequirementsBuilder.build(setup, hosidius, defaults, data, diary, ItemLedger.EMPTY, p -> true,
			false), payment));
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
