/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * The runes in your rune pouch (they count as carried for spell teleports and Fertile Soil). The pouch keeps each
 * slot's rune type and amount in varbits; the type is turned into the rune's item id with the game's rune enum.
 * Client thread only.
 */
@Singleton
public class RunePouchWatcher
{
	/** Each slot's type and quantity varbits; the divine rune pouch has six slots. */
	private static final int[][] SLOTS = {
		{VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1},
		{VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_QUANTITY_2},
		{VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_QUANTITY_3},
		{VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_QUANTITY_4},
		{VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_QUANTITY_5},
		{VarbitID.RUNE_POUCH_TYPE_6, VarbitID.RUNE_POUCH_QUANTITY_6},
	};
	private static final Set<Integer> VARBITS = Set.of(
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_TYPE_2,
		VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_TYPE_5,
		VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_TYPE_6, VarbitID.RUNE_POUCH_QUANTITY_6);

	private final Client client;
	private final ItemStore store;

	@Inject
	RunePouchWatcher(Client client, ItemStore store)
	{
		this.client = client;
		this.store = store;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (VARBITS.contains(event.getVarbitId()))
		{
			refresh();
		}
	}

	/** Reads the pouch now. Client thread. */
	public void refresh()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		EnumComposition runes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		Map<Integer, Integer> items = new HashMap<>();
		for (int[] slot : SLOTS)
		{
			int type = client.getVarbitValue(slot[0]);
			int quantity = client.getVarbitValue(slot[1]);
			if (type > 0 && quantity > 0)
			{
				items.merge(runes.getIntValue(type), quantity, Integer::sum);
			}
		}
		store.update(ItemSource.RUNE_POUCH, new StorageSnapshot(items, System.currentTimeMillis()));
	}
}
