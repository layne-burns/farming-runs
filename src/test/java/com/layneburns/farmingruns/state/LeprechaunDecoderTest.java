/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class LeprechaunDecoderTest
{
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final IntUnaryOperator reader = id -> varbits.getOrDefault(id, 0);

	@Test
	public void nothingStored()
	{
		assertTrue(LeprechaunDecoder.decode(reader).isEmpty());
		assertEquals(0, LeprechaunDecoder.bottomlessBucketUses(reader));
	}

	@Test
	public void toolsCountTheExtraVarbitTwice()
	{
		varbits.put(VarbitID.FARMING_TOOLS_RAKE, 1);
		varbits.put(VarbitID.FARMING_TOOLS_EXTRARAKES, 1);
		varbits.put(VarbitID.FARMING_TOOLS_SPADE, 1);
		Map<Integer, Integer> items = LeprechaunDecoder.decode(reader);
		assertEquals(3, (int) items.get(ItemID.RAKE));
		assertEquals(1, (int) items.get(ItemID.SPADE));
	}

	@Test
	public void bucketsAndCompostUseTheGamesArithmetic()
	{
		varbits.put(VarbitID.FARMING_TOOLS_BUCKETS, 5);
		varbits.put(VarbitID.FARMING_TOOLS_EXTRABUCKETS, 2);
		varbits.put(VarbitID.FARMING_TOOLS_EXTRA2BUCKETS, 1);
		varbits.put(VarbitID.FARMING_TOOLS_SUPERCOMPOST, 10);
		varbits.put(VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST, 1);
		varbits.put(VarbitID.FARMING_TOOLS_ULTRACOMPOST, 7);
		Map<Integer, Integer> items = LeprechaunDecoder.decode(reader);
		assertEquals(256 + 64 + 5, (int) items.get(ItemID.BUCKET_EMPTY));
		assertEquals(266, (int) items.get(ItemID.BUCKET_SUPERCOMPOST));
		assertEquals(7, (int) items.get(ItemID.BUCKET_ULTRACOMPOST));
	}

	@Test
	public void magicSecateursAreFlagged()
	{
		varbits.put(VarbitID.FARMING_TOOLS_SECATEURS, 1);
		varbits.put(VarbitID.FARMING_TOOLS_FAIRYSECATEURS, 1);
		Map<Integer, Integer> items = LeprechaunDecoder.decode(reader);
		assertEquals(1, (int) items.get(ItemID.FAIRY_ENCHANTED_SECATEURS));
		assertFalse(items.containsKey(ItemID.SECATEURS));
	}

	@Test
	public void bottomlessBucketEmptyOrFilled()
	{
		varbits.put(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE, 1);
		assertTrue(LeprechaunDecoder.decode(reader).containsKey(ItemID.BOTTOMLESS_COMPOST_BUCKET));
		assertEquals(0, LeprechaunDecoder.bottomlessBucketUses(reader));

		varbits.put(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE, 3);
		varbits.put(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY, 50);
		assertTrue(LeprechaunDecoder.decode(reader).containsKey(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED));
		assertEquals(50, LeprechaunDecoder.bottomlessBucketUses(reader));
	}
}
