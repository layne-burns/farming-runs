/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.Compostable;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

public class CompostBinFillTest
{
	private static GameData data;
	private static Compostable pineapple;
	private static Compostable watermelon;
	private static Compostable coconut;
	private static Compostable jangerberries;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
		pineapple = item("Pineapple");
		watermelon = item("Watermelon");
		coconut = item("Coconut");
		jangerberries = item("Jangerberries");
	}

	private static Compostable item(String name)
	{
		return data.compostables().stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
	}

	private static CompostBinFill.Fill fill(Map<Integer, Integer> held, CompostBinFill.Choices choices, int needed)
	{
		return CompostBinFill.fill(data.compostables(), data.seeds(), held, choices, needed);
	}

	private static CompostBinFill.Row row(CompostBinFill.Fill fill, Compostable item)
	{
		return fill.getRanking().stream().filter(r -> r.getItem().equals(item)).findFirst().orElseThrow();
	}

	@Test
	public void shopBoughtKeepsNoneHighLevelPaymentsAreNeverUsedTheRestKeep100()
	{
		assertEquals(0, CompostBinFill.defaultKeep(pineapple, data.seeds()));
		assertEquals(100, CompostBinFill.defaultKeep(watermelon, data.seeds()));
		assertEquals(100, CompostBinFill.defaultKeep(jangerberries, data.seeds()));
		assertEquals(100, CompostBinFill.defaultKeep(item("Calquat fruit"), data.seeds()));
		for (String never : List.of("Coconut", "Papaya fruit", "Dragonfruit", "White berries", "Poison ivy berries"))
		{
			assertEquals(never, CompostBinFill.NEVER, CompostBinFill.defaultKeep(item(never), data.seeds()));
		}
	}

	@Test
	public void shopBoughtFirstThenTheLargestSpare()
	{
		CompostBinFill.Fill fill = fill(Map.of(pineapple.getItemId(), 33, watermelon.getItemId(), 464,
			coconut.getItemId(), 70, jangerberries.getItemId(), 3), CompostBinFill.Choices.NONE, 0);
		assertEquals(pineapple, fill.getRanking().get(0).getItem());
		assertEquals(watermelon, fill.getRanking().get(1).getItem());
		assertEquals("33 spare, shop-bought", row(fill, pineapple).getWhy());
		assertEquals("364 spare (keep 100)", row(fill, watermelon).getWhy());
		assertEquals("never: pays for Magic tree, Dragonfruit tree", row(fill, coconut).getWhy());
		assertEquals("none spare (keep 100)", row(fill, jangerberries).getWhy());
	}

	@Test
	public void theDefaultGoesFirstThenTheRankingTopsUp()
	{
		CompostBinFill.Choices melons = new CompostBinFill.Choices(watermelon.getItemId(), List.of(), Map.of());
		assertEquals("30 × Watermelon", fill(Map.of(pineapple.getItemId(), 33, watermelon.getItemId(), 464), melons,
			30).words());
		// Only 18 watermelons spare: pineapples, first in the ranking, make up the rest.
		CompostBinFill.Fill topped = fill(Map.of(pineapple.getItemId(), 33, watermelon.getItemId(), 118), melons, 30);
		assertEquals("18 × Watermelon + 12 × Pineapple", topped.words());
		assertEquals(0, topped.getShortBy());
	}

	@Test
	public void withoutADefaultTheRankingFillsIt()
	{
		assertEquals("30 × Pineapple", fill(Map.of(pineapple.getItemId(), 33, watermelon.getItemId(), 464),
			CompostBinFill.Choices.NONE, 30).words());
	}

	@Test
	public void shortWhenEverythingSpareIsntEnough()
	{
		CompostBinFill.Fill fill = fill(Map.of(pineapple.getItemId(), 5, watermelon.getItemId(), 110,
			coconut.getItemId(), 500), CompostBinFill.Choices.NONE, 30);
		assertEquals("5 × Pineapple + 10 × Watermelon", fill.words());
		assertEquals(15, fill.getShortBy());
	}

	@Test
	public void yourOrderAndKeepsReplaceTheDefaults()
	{
		CompostBinFill.Choices mine = new CompostBinFill.Choices(null, List.of(watermelon.getItemId()),
			Map.of(coconut.getItemId(), 20, pineapple.getItemId(), CompostBinFill.NEVER));
		CompostBinFill.Fill fill = fill(Map.of(pineapple.getItemId(), 33, watermelon.getItemId(), 464,
			coconut.getItemId(), 70), mine, 30);
		assertEquals(watermelon, fill.getRanking().get(0).getItem());
		assertEquals("50 spare (keep 20)", row(fill, coconut).getWhy());
		assertEquals("never (your choice)", row(fill, pineapple).getWhy());
		assertTrue(row(fill, coconut).isOwnKeep());
		assertEquals("30 × Watermelon", fill.words());
	}
}
