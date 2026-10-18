/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import static org.junit.Assert.assertEquals;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class CostTableTest
{
	@Test
	public void parsesArriveAndLinkRows() throws IOException
	{
		CostTable costs = CostTable.parse(new StringReader(
			"# comment\ntype\tfrom\tto\tticks\narrive\ta\t-\t10\narrive\tb\t-\t30\nlink\tb\ta\t5\n"));
		assertEquals(10, costs.cost(null, "a"));
		assertEquals(5, costs.cost("a", "b"));
		assertEquals(30, costs.cost("c", "b"));
	}

	@Test
	public void aLinkDearerThanTheTeleportIsNotUsed() throws IOException
	{
		CostTable costs = CostTable.parse(new StringReader("arrive\ta\t-\t10\narrive\tb\t-\t40\nlink\ta\tb\t25\n"));
		assertEquals(10, costs.cost("b", "a"));
		assertEquals(25, costs.cost("a", "b"));
	}

	@Test(expected = DataException.class)
	public void rejectsANonNumberWithTheLine() throws IOException
	{
		CostTable.parse(new StringReader("arrive\ta\t-\tten\n"));
	}

	@Test
	public void closePlacesCostAWalk() throws IOException
	{
		CostTable costs = CostTable.parse(new StringReader("arrive\ta\t-\t30\narrive\tb\t-\t30\narrive\tc\t-\t30\n"))
			.withPlaces(Map.of("a", new WorldPoint(3200, 3200, 0), "b", new WorldPoint(3210, 3200, 0),
				"c", new WorldPoint(3400, 3200, 0)));
		// 10 tiles apart: a 7-tick walk beats the 30-tick teleport.
		assertEquals(7, costs.cost("a", "b"));
		// Far apart: the teleport.
		assertEquals(30, costs.cost("a", "c"));
		// From wherever you start: always the teleport.
		assertEquals(30, costs.cost(null, "b"));
	}
}
