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
import com.layneburns.farmingruns.state.BufferSettings;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.state.StorageSnapshot;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;

public class BankViewBuilderTest
{
	private static final BufferSettings BUFFERS = new BufferSettings(9, 2, 3);
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
		BankView.Row ranarr = row(build(ledger), "Herb seeds", "Ranarr seed");
		assertEquals("14 (11 spare)", ranarr.getValue());
		assertEquals("Bank 12, Seed vault 2", ranarr.getTooltip());
	}

	@Test
	public void allotmentSeedsUseTheAllotmentBuffer()
	{
		ItemLedger ledger = ItemLedger.EMPTY
			.with(ItemSource.BANK, new StorageSnapshot(Map.of(ItemID.POTATO_SEED, 5), NOW));
		assertEquals("5 (0 spare)", row(build(ledger), "Allotment seeds", "Potato seed").getValue());
	}

	@Test
	public void treesCountSaplingsAgainstTheSaplingBuffer()
	{
		ItemLedger ledger = ItemLedger.EMPTY.with(ItemSource.BANK,
			new StorageSnapshot(Map.of(ItemID.PLANTPOT_MAGIC_TREE_SAPLING, 3, ItemID.MAGIC_TREE_SEED, 5), NOW));
		assertEquals("3 saplings (1 spare) · 5 seeds", row(build(ledger), "Tree seeds", "Magic seed").getValue());
	}

	@Test
	public void noSeedsExplainsHowToFillTheLog()
	{
		BankView.Section seeds = build(ItemLedger.EMPTY).getSections().get(0);
		assertEquals("Seeds", seeds.getTitle());
		assertTrue(seeds.getRows().isEmpty());
		assertNotNull(seeds.getEmptyText());
	}

	@Test
	public void paymentsSayWhatTheyProtect()
	{
		ItemLedger ledger = ItemLedger.EMPTY
			.with(ItemSource.BANK, new StorageSnapshot(Map.of(ItemID.BASKET_TOMATO_5, 4), NOW));
		BankView.Row tomatoes = row(build(ledger), "Protection payments", "Tomatoes(5)");
		assertEquals("4", tomatoes.getValue());
		assertTrue(tomatoes.getTooltip(), tomatoes.getTooltip().contains("Acorn (1)"));
	}

	@Test
	public void leprechaunToolsAreNamed()
	{
		ItemLedger ledger = ItemLedger.EMPTY.with(ItemSource.LEPRECHAUN,
			new StorageSnapshot(Map.of(ItemID.FAIRY_ENCHANTED_SECATEURS, 1, ItemID.BUCKET_ULTRACOMPOST, 30), NOW));
		BankView view = build(ledger);
		assertEquals("1", row(view, "Tool leprechaun", "Magic secateurs").getValue());
		assertEquals("30", row(view, "Tool leprechaun", "Ultracompost").getValue());
		assertEquals("30", row(view, "Compost", "Ultracompost").getValue());
	}

	@Test
	public void agesReadNaturally()
	{
		assertEquals("never seen", BankViewBuilder.age(0, NOW));
		assertEquals("just now", BankViewBuilder.age(NOW - 10_000, NOW));
		assertEquals("5 min ago", BankViewBuilder.age(NOW - 5 * 60_000, NOW));
		assertEquals("2 h ago", BankViewBuilder.age(NOW - 2 * 3_600_000, NOW));
	}

	private static BankView build(ItemLedger ledger)
	{
		return BankViewBuilder.build(data, ledger, BUFFERS, 0, NOW);
	}

	private static BankView.Row row(BankView view, String section, String label)
	{
		return view.getSections().stream()
			.filter(s -> s.getTitle().equals(section))
			.flatMap(s -> s.getRows().stream())
			.filter(r -> r.getLabel().equals(label))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no row " + label + " in " + section));
	}
}
