/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A colour per patch kind, shared by the patch outlines and the seeds for them in your inventory, so a herb seed
 * glows the same green as the herb patch it goes in. Kinds without their own colour, and items that aren't seeds
 * (compost, payment, tools), use {@link #DEFAULT}.
 */
@Singleton
public class PatchColours
{
	/** The run's usual highlight colour. */
	public static final Color DEFAULT = new Color(0, 220, 255);

	private static final Map<PatchImplementation, Color> BY_KIND = Map.of(
		PatchImplementation.HERB, new Color(60, 220, 90),
		PatchImplementation.ALLOTMENT, new Color(255, 160, 40),
		PatchImplementation.FLOWER, new Color(255, 105, 180),
		PatchImplementation.HOPS, new Color(240, 220, 60),
		PatchImplementation.BUSH, new Color(180, 110, 255),
		PatchImplementation.FRUIT_TREE, new Color(240, 80, 80));

	/** Seeds, saplings and watered seedlings by item id, to the kind they're planted in. */
	private final Map<Integer, PatchImplementation> kindByItem = new HashMap<>();

	@Inject
	PatchColours(GameData data)
	{
		for (Seed seed : data.seeds())
		{
			kindByItem.put(seed.getItemId(), seed.getKind());
			seed.plantItemIds().forEach(id -> kindByItem.put(id, seed.getKind()));
		}
	}

	public static Color of(PatchImplementation kind)
	{
		return BY_KIND.getOrDefault(kind, DEFAULT);
	}

	/** The colour for an item: its patch kind's for a seed, sapling or seedling; else the usual one. */
	public Color ofItem(int itemId)
	{
		PatchImplementation kind = kindByItem.get(itemId);
		return kind == null ? DEFAULT : of(kind);
	}

	/** The kind a seed, sapling or seedling is planted in; null for anything else. */
	@Nullable
	public PatchImplementation kindOf(int itemId)
	{
		return kindByItem.get(itemId);
	}
}
