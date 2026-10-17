/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Turns the tool leprechaun's varbits into item counts. The arithmetic is the game's own, from its
 * farming_tools_getstored client script (runelite/cs2-scripts): for example the stored rakes are
 * 2 x FARMING_TOOLS_EXTRARAKES + FARMING_TOOLS_RAKE, and buckets are 256 x EXTRA2 + 32 x EXTRA + BUCKETS.
 */
public final class LeprechaunDecoder
{
	/** Every varbit read here: a change to any of them means the leprechaun's contents changed. */
	public static final int[] VARBITS = {
		VarbitID.FARMING_TOOLS_RAKE, VarbitID.FARMING_TOOLS_EXTRARAKES,
		VarbitID.FARMING_TOOLS_DIBBER, VarbitID.FARMING_TOOLS_EXTRADIBBERS,
		VarbitID.FARMING_TOOLS_SPADE, VarbitID.FARMING_TOOLS_EXTRASPADES,
		VarbitID.FARMING_TOOLS_SECATEURS, VarbitID.FARMING_TOOLS_EXTRASECATEURS,
		VarbitID.FARMING_TOOLS_FAIRYSECATEURS,
		VarbitID.FARMING_TOOLS_TROWEL, VarbitID.FARMING_TOOLS_EXTRATROWELS,
		VarbitID.FARMING_TOOLS_PLANTCURE,
		VarbitID.FARMING_TOOLS_BUCKETS, VarbitID.FARMING_TOOLS_EXTRABUCKETS, VarbitID.FARMING_TOOLS_EXTRA2BUCKETS,
		VarbitID.FARMING_TOOLS_COMPOST, VarbitID.FARMING_TOOLS_EXTRACOMPOST,
		VarbitID.FARMING_TOOLS_SUPERCOMPOST, VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST,
		VarbitID.FARMING_TOOLS_ULTRACOMPOST,
		VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE, VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY,
	};

	private LeprechaunDecoder()
	{
	}

	/**
	 * Item id to count for everything stored.
	 *
	 * @param varbit reads a varbit's value (the client's getVarbitValue, or a fake in tests)
	 */
	public static Map<Integer, Integer> decode(IntUnaryOperator varbit)
	{
		Map<Integer, Integer> items = new HashMap<>();
		put(items, ItemID.RAKE, pair(varbit, VarbitID.FARMING_TOOLS_EXTRARAKES, VarbitID.FARMING_TOOLS_RAKE));
		put(items, ItemID.DIBBER, pair(varbit, VarbitID.FARMING_TOOLS_EXTRADIBBERS, VarbitID.FARMING_TOOLS_DIBBER));
		put(items, ItemID.SPADE, pair(varbit, VarbitID.FARMING_TOOLS_EXTRASPADES, VarbitID.FARMING_TOOLS_SPADE));
		int secateurs = varbit.applyAsInt(VarbitID.FARMING_TOOLS_FAIRYSECATEURS) == 1
			? ItemID.FAIRY_ENCHANTED_SECATEURS : ItemID.SECATEURS;
		put(items, secateurs, pair(varbit, VarbitID.FARMING_TOOLS_EXTRASECATEURS, VarbitID.FARMING_TOOLS_SECATEURS));
		put(items, ItemID.GARDENING_TROWEL,
			pair(varbit, VarbitID.FARMING_TOOLS_EXTRATROWELS, VarbitID.FARMING_TOOLS_TROWEL));
		put(items, ItemID.PLANT_CURE, varbit.applyAsInt(VarbitID.FARMING_TOOLS_PLANTCURE));
		put(items, ItemID.BUCKET_EMPTY, 256 * varbit.applyAsInt(VarbitID.FARMING_TOOLS_EXTRA2BUCKETS)
			+ 32 * varbit.applyAsInt(VarbitID.FARMING_TOOLS_EXTRABUCKETS)
			+ varbit.applyAsInt(VarbitID.FARMING_TOOLS_BUCKETS));
		put(items, ItemID.BUCKET_COMPOST, 256 * varbit.applyAsInt(VarbitID.FARMING_TOOLS_EXTRACOMPOST)
			+ varbit.applyAsInt(VarbitID.FARMING_TOOLS_COMPOST));
		put(items, ItemID.BUCKET_SUPERCOMPOST, 256 * varbit.applyAsInt(VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST)
			+ varbit.applyAsInt(VarbitID.FARMING_TOOLS_SUPERCOMPOST));
		put(items, ItemID.BUCKET_ULTRACOMPOST, varbit.applyAsInt(VarbitID.FARMING_TOOLS_ULTRACOMPOST));
		int bucketType = varbit.applyAsInt(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE);
		if (bucketType == 1)
		{
			put(items, ItemID.BOTTOMLESS_COMPOST_BUCKET, 1);
		}
		else if (bucketType > 1)
		{
			put(items, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 1);
		}
		return items;
	}

	/** Uses left in a stored, filled bottomless bucket (0 if none is stored). */
	public static int bottomlessBucketUses(IntUnaryOperator varbit)
	{
		return varbit.applyAsInt(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE) > 1
			? varbit.applyAsInt(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY) : 0;
	}

	private static int pair(IntUnaryOperator varbit, int extra, int base)
	{
		return 2 * varbit.applyAsInt(extra) + varbit.applyAsInt(base);
	}

	private static void put(Map<Integer, Integer> items, int itemId, int count)
	{
		if (count > 0)
		{
			items.put(itemId, count);
		}
	}
}
