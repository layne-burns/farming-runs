/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class BankFiltersTest
{
	/** A herb: 9 patches, level 32, can't be protected. */
	private static final BankView.Plantable HERB = new BankView.Plantable(32, 9, 9, false, 0, "");
	/** An apple sapling: 6 patches, spare fills 4, payment held for 2. */
	private static final BankView.Plantable APPLE = new BankView.Plantable(27, 6, 4, true, 2, "9 x Sweetcorn");
	/** A magic sapling: level 75, 6 patches, spare fills 6, payment for all 6. */
	private static final BankView.Plantable MAGIC = new BankView.Plantable(75, 6, 6, true, 6, "25 x Coconut");
	/** An oak sapling with no spare (all in the buffer). */
	private static final BankView.Plantable OAK = new BankView.Plantable(15, 6, 0, true, 3, "1 x Tomatoes(5)");

	private static final List<BankView.Section> SECTIONS = List.of(
		new BankView.Section(BankView.Page.SEEDS, "Seeds", List.of(new BankView.Group("Mixed", List.of(
			row("Ranarr seed", HERB), row("Apple sapling", APPLE), row("Magic sapling", MAGIC),
			row("Oak sapling", OAK), new BankView.Row(1, "Acorn", "5", null, "unpotted")))), null),
		new BankView.Section(BankView.Page.SUPPLIES, "Compost", List.of(new BankView.Group(null, List.of(
			new BankView.Row(2, "Ultracompost", "30", null, "where")))), "none"));

	@Test
	public void noFiltersChangesNothing()
	{
		assertEquals(SECTIONS, BankFilters.NONE.apply(SECTIONS));
	}

	@Test
	public void hasProtectionKeepsPaidProtectableCropsAndColoursThem()
	{
		List<BankView.Row> rows = seedRows(BankFilters.NONE.withHasProtection(true));
		assertEquals(List.of("Apple sapling", "Magic sapling", "Oak sapling"), labels(rows));
		// Apple: spare fills 4 patches, payment covers 2 of them.
		assertEquals(BankView.Coverage.PARTIAL, rows.get(0).getDetailColour());
		assertEquals("4 spare · paid 2/4", rows.get(0).getDetail());
		assertEquals(BankView.Coverage.FULL, rows.get(1).getDetailColour());
	}

	@Test
	public void enoughForRunDropsSeedsWithNoSpare()
	{
		List<BankView.Row> rows = seedRows(BankFilters.NONE.withEnoughForRun(true));
		assertEquals(List.of("Ranarr seed", "Apple sapling", "Magic sapling"), labels(rows));
		assertEquals("9 spare · full run", rows.get(0).getDetail());
		assertEquals(BankView.Coverage.PARTIAL, rows.get(1).getDetailColour());
	}

	@Test
	public void canPlantUsesTheFarmingLevel()
	{
		assertEquals(List.of("Ranarr seed", "Apple sapling", "Oak sapling"),
			labels(seedRows(BankFilters.NONE.withCanPlant(true).withFarmingLevel(60))));
		// Level unknown (logged out): nothing is hidden for level.
		assertEquals(4, seedRows(BankFilters.NONE.withCanPlant(true)).size());
	}

	@Test
	public void filtersCombineAndTakeTheWeakerColour()
	{
		List<BankView.Row> rows = seedRows(BankFilters.NONE.withHasProtection(true).withEnoughForRun(true));
		assertEquals(List.of("Apple sapling", "Magic sapling"), labels(rows));
		assertEquals(BankView.Coverage.PARTIAL, rows.get(0).getDetailColour());
		assertEquals(BankView.Coverage.FULL, rows.get(1).getDetailColour());
		assertTrue(rows.get(0).getTooltip().contains("Payment: 9 x Sweetcorn per patch, enough for 2 of 4"));
	}

	@Test
	public void suppliesAreNeverFiltered()
	{
		List<BankView.Section> filtered = BankFilters.NONE.withHasProtection(true).apply(SECTIONS);
		assertEquals("Compost", filtered.get(filtered.size() - 1).getTitle());
	}

	private static BankView.Row row(String label, BankView.Plantable plantable)
	{
		return new BankView.Row(1, label, "1", plantable.getSeededPatches() + " spare", null, "where", plantable);
	}

	private static List<BankView.Row> seedRows(BankFilters filters)
	{
		return filters.apply(SECTIONS).stream()
			.filter(s -> s.getPage() == BankView.Page.SEEDS)
			.flatMap(s -> s.getGroups().stream())
			.flatMap(g -> g.getRows().stream())
			.collect(Collectors.toList());
	}

	private static List<String> labels(List<BankView.Row> rows)
	{
		return rows.stream().map(BankView.Row::getLabel).collect(Collectors.toList());
	}
}
