/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class BufferSettingsTest
{
	private static GameData data;
	private static BufferSettings buffers;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
		buffers = new BufferSettings(9, 2, 3, data.contractKinds());
	}

	private static Seed first(PatchImplementation kind)
	{
		return data.seedsFor(kind).get(0);
	}

	@Test
	public void kindsAContractCanAskForKeepTheirBuffer()
	{
		assertEquals(9, buffers.bufferFor(first(PatchImplementation.ALLOTMENT)));
		assertEquals(3, buffers.bufferFor(first(PatchImplementation.HERB)));
		assertEquals(2, buffers.bufferFor(first(PatchImplementation.TREE)));
		assertEquals(2, buffers.bufferFor(first(PatchImplementation.REDWOOD)));
	}

	@Test
	public void otherKindsKeepNone()
	{
		// No Farming Guild patch, or a Guild patch no contract asks for (spirit tree, anima, Hespori).
		for (PatchImplementation kind : List.of(PatchImplementation.MUSHROOM, PatchImplementation.HARDWOOD_TREE,
			PatchImplementation.BELLADONNA, PatchImplementation.HOPS, PatchImplementation.CALQUAT,
			PatchImplementation.SEAWEED, PatchImplementation.SPIRIT_TREE, PatchImplementation.ANIMA,
			PatchImplementation.HESPORI))
		{
			assertFalse(kind.name(), data.contractKinds().contains(kind));
			assertEquals(kind.name(), 0, buffers.bufferFor(first(kind)));
			assertEquals(kind.name(), 5, buffers.spare(first(kind), 5));
		}
	}
}
