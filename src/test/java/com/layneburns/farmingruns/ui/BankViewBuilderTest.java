/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.state.BufferSettings;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.state.StorageSnapshot;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;

public class BankViewBuilderTest
{
	/** Kept back for every kind, so the tests don't depend on which kinds the Farming Guild has. */
	private static final BufferSettings BUFFERS = new BufferSettings(9, 2, 3, Set.of(PatchImplementation.values()));
	private static final long NOW = 10_000_000;
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	@Test
	public void herbSeedsShowTotalSpareAndWhere()
	{
		ItemLedger ledger = ItemLedger.EMPTY
			.with(ItemSource.BANK, new StorageSnapshot(Map.of(ItemID.RANARR_SEED, 12), NOW))
			.with(ItemSource.SEED_VAULT, new StorageSnapshot(Map.of(ItemID.RANARR_SEED, 2), NOW));
		BankView.Row ranarr = row(build(ledger).getSections(), "Herbs & flowers", "Herb", "Ranarr seed");
		assertEquals("14", ranarr.getValue());
		assertEquals("11 spare", ranarr.getDetail());
		assertTrue(ranarr.getTooltip(), ranarr.getTooltip().contains("Where: Bank 12, Seed vault 2"));
		assertTrue(ranarr.getTooltip(), ranarr.getTooltip().contains("Level 32"));
		assertTrue(ranarr.getTooltip(), ranarr.getTooltip().contains("XP: 27 plant, 30.5 per harvest"));
		assertTrue(ranarr.getTooltip(), ranarr.getTooltip().contains("Can't be protected"));
	}

	@Test
	public void allotmentSeedsUseTheAllotmentBuffer()
	{
		ItemLedger ledger = ItemLedger.EMPTY
			.with(ItemSource.BANK, new StorageSnapshot(Map.of(ItemID.POTATO_SEED, 5), NOW));
		assertEquals("0 spare",
			row(build(ledger).getSections(), "Allotments & hops", "Allotment", "Potato seed").getDetail());
	}

	@Test
	public void kindsWithNoBufferShowNoSpareLine()
	{
		// The real buffers: hops are never asked for in a contract, so none are kept back.
		Seed hops = data.seedsFor(PatchImplementation.HOPS).get(0);
		ItemLedger ledger = ItemLedger.EMPTY.with(ItemSource.BANK,
			new StorageSnapshot(Map.of(hops.getItemId(), 20, ItemID.POTATO_SEED, 20), NOW));
		BankView view = BankViewBuilder.build(data, ledger, new BufferSettings(9, 2, 3, data.contractKinds()),
			data.patchCountsByKind(), 0, NOW);
		assertEquals(null, row(view.getSections(), "Allotments & hops", BankViewBuilder.kindName(PatchImplementation.HOPS),
			hops.getName()).getDetail());
		assertEquals("11 spare", row(view.getSections(), "Allotments & hops", "Allotment", "Potato seed").getDetail());
	}

	@Test
	public void treesCountSaplingsAgainstTheSaplingBuffer()
	{
		ItemLedger ledger = ItemLedger.EMPTY.with(ItemSource.BANK,
			new StorageSnapshot(Map.of(ItemID.PLANTPOT_MAGIC_TREE_SAPLING, 3, ItemID.MAGIC_TREE_SEED, 5), NOW));
		List<BankView.Section> sections = build(ledger).getSections();
		BankView.Row saplings = row(sections, "Trees", "Tree saplings", "Magic sapling");
		assertEquals("3", saplings.getValue());
		assertEquals("1 spare", saplings.getDetail());
		BankView.Row seeds = row(sections, "Trees", "Tree seeds", "Magic seed");
		assertEquals("5", seeds.getValue());
		assertEquals(null, seeds.getDetail());
	}

	@Test
	public void noSeedsExplainsHowToFillTheLog()
	{
		List<BankView.Section> seeds = build(ItemLedger.EMPTY).page(BankView.Page.SEEDS);
		assertEquals(1, seeds.size());
		assertEquals("Seeds", seeds.get(0).getTitle());
		assertNotNull(seeds.get(0).getEmptyText());
	}

	@Test
	public void emptySeedSectionsAreHidden()
	{
		ItemLedger ledger = ItemLedger.EMPTY
			.with(ItemSource.BANK, new StorageSnapshot(Map.of(ItemID.RANARR_SEED, 1), NOW));
		List<String> titles = build(ledger).page(BankView.Page.SEEDS).stream()
			.map(BankView.Section::getTitle).collect(Collectors.toList());
		assertEquals(List.of("Herbs & flowers"), titles);
	}

	@Test
	public void paymentsSayWhatTheyProtect()
	{
		ItemLedger ledger = ItemLedger.EMPTY
			.with(ItemSource.BANK, new StorageSnapshot(Map.of(ItemID.BASKET_TOMATO_5, 4), NOW));
		BankView.Row tomatoes = row(build(ledger).getSections(), "Protection payments", null, "Tomatoes(5)");
		assertEquals("4", tomatoes.getValue());
		assertTrue(tomatoes.getTooltip(), tomatoes.getTooltip().contains("Acorn: 1"));
	}

	@Test
	public void leprechaunToolsAreNamed()
	{
		ItemLedger ledger = ItemLedger.EMPTY.with(ItemSource.LEPRECHAUN,
			new StorageSnapshot(Map.of(ItemID.FAIRY_ENCHANTED_SECATEURS, 1, ItemID.BUCKET_ULTRACOMPOST, 30), NOW));
		List<BankView.Section> sections = build(ledger).getSections();
		assertEquals("1", row(sections, "Tool leprechaun", null, "Magic secateurs").getValue());
		BankView.Row ultra = row(sections, "Compost", null, "Ultracompost");
		assertEquals("30", ultra.getValue());
		assertEquals("30 in leprechaun", ultra.getDetail());
	}

	@Test
	public void searchMatchesNamesAndKinds()
	{
		ItemLedger ledger = ItemLedger.EMPTY.with(ItemSource.BANK, new StorageSnapshot(Map.of(
			ItemID.RANARR_SEED, 5, ItemID.SNAPDRAGON_SEED, 4,
			ItemID.PLANTPOT_APPLE_SAPLING, 2, ItemID.PLANTPOT_MAGIC_TREE_SAPLING, 3), NOW));
		BankView view = build(ledger);

		List<BankView.Section> ranarr = view.search("ranarr");
		assertEquals(1, ranarr.size());
		assertEquals("Ranarr seed", ranarr.get(0).getGroups().get(0).getRows().get(0).getLabel());

		// A kind name finds every seed of that kind, and nothing else.
		List<BankView.Section> fruit = view.search("fruit");
		assertEquals(1, fruit.size());
		assertEquals("Fruit tree saplings", fruit.get(0).getGroups().get(0).getTitle());
		assertEquals(1, fruit.get(0).getGroups().get(0).getRows().size());

		// Every word must match.
		assertTrue(view.search("ranarr magic").isEmpty());
		assertEquals(2, view.search("herb").get(0).getGroups().get(0).getRows().size());
	}

	@Test
	public void agesAreShort()
	{
		assertEquals("never", BankViewBuilder.age(0, NOW));
		assertEquals("now", BankViewBuilder.age(NOW - 10_000, NOW));
		assertEquals("5m", BankViewBuilder.age(NOW - 5 * 60_000, NOW));
		assertEquals("2h", BankViewBuilder.age(NOW - 2 * 3_600_000, NOW));
	}

	private static BankView build(ItemLedger ledger)
	{
		return BankViewBuilder.build(data, ledger, BUFFERS, data.patchCountsByKind(), 0, NOW);
	}

	private static BankView.Row row(List<BankView.Section> sections, String section, String group, String label)
	{
		return sections.stream()
			.filter(s -> s.getTitle().equals(section))
			.flatMap(s -> s.getGroups().stream())
			.filter(g -> group == null ? g.getTitle() == null : group.equals(g.getTitle()))
			.flatMap(g -> g.getRows().stream())
			.filter(r -> r.getLabel().equals(label))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no row " + label + " in " + section + " / " + group));
	}
}
