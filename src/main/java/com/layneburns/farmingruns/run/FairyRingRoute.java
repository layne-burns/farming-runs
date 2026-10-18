/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.ItemCatalog;
import com.layneburns.farmingruns.data.ItemAmount;
import com.layneburns.farmingruns.data.Teleport;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.data.TeleportType;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.state.ItemStore;
import com.layneburns.farmingruns.state.PlayerInfo;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Fairy ring legs through your house: house teleport, then a house portal (the setting, e.g. Salve Graveyard) to the
 * fairy ring there. Shortest Path may use your house's portals on these legs so it can find that way (otherwise it
 * walks to the nearest ring, often the Ardougne monastery's), and the overhead line shows the chain
 * ({@link TravelChain}). Needs a way home you carry. Client thread.
 */
@Singleton
public class FairyRingRoute
{
	private final GameData data;
	private final FarmingRunsConfig config;
	private final ItemStore items;
	private final PlayerInfo player;

	@Inject
	FairyRingRoute(GameData data, FarmingRunsConfig config, ItemStore items,
		PlayerInfo player)
	{
		this.data = data;
		this.config = config;
		this.items = items;
		this.player = player;
	}

	/**
	 * The house portal this leg's fairy ring is reached through (the setting, e.g. "Salve Graveyard"), or null when
	 * it isn't a fairy ring leg, the setting is blank, or you carry no way home.
	 */
	@Nullable
	public String via(@Nullable TeleportChoice teleport)
	{
		String portal = config.fairyRingVia() == null ? "" : config.fairyRingVia().trim();
		if (teleport == null || teleport.getMethod().getType() != TeleportType.FAIRY_RING || portal.isEmpty())
		{
			return null;
		}
		Teleport house = data.teleports().stream().filter(t -> t.getType() == TeleportType.HOUSE).findFirst()
			.orElse(null);
		return canGoHome(items.ledger(), data.getItems(), house, player.getFacts().spellbookName()) ? portal : null;
	}

	/**
	 * You carry a way into your house: a construction cape (in your inventory or worn), or the Teleport to House runes
	 * (inventory or rune pouch) on its spellbook. House tablets aren't for runs.
	 */
	static boolean canGoHome(ItemLedger ledger, ItemCatalog items, @Nullable Teleport house,
		@Nullable String spellbook)
	{
		if (carried(ledger, items.group("construction_cape")) > 0)
		{
			return true;
		}
		if (house == null || house.getRunes().isEmpty()
			|| (spellbook != null && house.getSpellbook() != null && !spellbook.equals(house.getSpellbook())))
		{
			return false;
		}
		for (ItemAmount rune : house.getRunes())
		{
			if (carried(ledger, List.of(rune.getItemId())) < rune.getAmount())
			{
				return false;
			}
		}
		return true;
	}

	private static int carried(ItemLedger ledger, Collection<Integer> ids)
	{
		int n = 0;
		for (int id : ids)
		{
			n += ledger.count(id, ItemSource.INVENTORY) + ledger.count(id, ItemSource.EQUIPMENT)
				+ ledger.count(id, ItemSource.RUNE_POUCH);
		}
		return n;
	}
}
