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

	@Test(expected = DataException.class)
	public void rejectsANonNumberWithTheLine() throws IOException
	{
		CostTable.parse(new StringReader("arrive\ta\t-\tten\n"));
	}
}
