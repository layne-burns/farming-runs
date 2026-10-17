/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.ItemAmount;
import com.layneburns.farmingruns.data.Teleport;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.data.TeleportType;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.PlayerFacts;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Picks the teleport a run uses for a location from your ranked list (Locations tab): the first one you own (carried
 * or stored anywhere), skipping spells from another spellbook. When you own none, the first usable one, so the check
 * says what's missing. Charges and daily limits aren't known, so they're not counted. Pure: unit tested.
 */
public final class TeleportPicker
{
	private TeleportPicker()
	{
	}

	/** The teleport to use, or null when none of the refs can be used (then you walk). */
	@Nullable
	public static TeleportChoice pick(List<String> refs, GameData data, PlayerFacts facts, ItemLedger ledger)
	{
		TeleportChoice firstUsable = null;
		for (String ref : refs)
		{
			TeleportChoice choice = data.teleportChoice(ref);
			if (choice == null || !usable(choice.getMethod(), facts))
			{
				continue;
			}
			if (owned(choice, data, facts, ledger))
			{
				return choice;
			}
			if (firstUsable == null)
			{
				firstUsable = choice;
			}
		}
		return firstUsable;
	}

	/**
	 * False only for a spell on a spellbook you're not on (when the spellbook is known). A house portal is always
	 * usable: the spell is only one way into the house.
	 */
	static boolean usable(Teleport method, PlayerFacts facts)
	{
		return method.getType() == TeleportType.HOUSE || usableSpell(method, facts);
	}

	/** Runes for one cast, anywhere. */
	static boolean hasRunes(Teleport method, ItemLedger ledger)
	{
		for (ItemAmount rune : method.getRunes())
		{
			if (ledger.count(rune.getItemId()) < rune.getAmount())
			{
				return false;
			}
		}
		return true;
	}

	/** The method's spell can be cast on your spellbook (or the spellbook isn't known). */
	static boolean usableSpell(Teleport method, PlayerFacts facts)
	{
		String book = facts.spellbookName();
		return method.getSpellbook() == null || book == null || book.equals(method.getSpellbook());
	}

	/** True when its items are waived by a diary you've done (fairy rings after the elite Lumbridge diary). */
	public static boolean waived(Teleport method, PlayerFacts facts)
	{
		return method.getWaivedByVarbitId() != Teleport.NOT_WAIVED && facts.varbit(method.getWaivedByVarbitId()) > 0;
	}

	/** You have what it takes somewhere: the item, a cast's runes, a staff (or the diary), or nothing is needed. */
	static boolean owned(TeleportChoice choice, GameData data, PlayerFacts facts, ItemLedger ledger)
	{
		switch (choice.getMethod().getType())
		{
			case ITEM:
				return ledger.countAny(choice.itemIds()) > 0;
			case SPELL:
				return hasRunes(choice.getMethod(), ledger);
			case HOUSE:
				return ledger.countAny(data.getItems().group("construction_cape")) > 0
					|| ledger.countAny(data.getItems().group("house_tablet")) > 0
					|| (usableSpell(choice.getMethod(), facts) && hasRunes(choice.getMethod(), ledger));
			case FAIRY_RING:
				return waived(choice.getMethod(), facts) || ledger.countAny(choice.itemIds()) > 0;
			default:
				return true;
		}
	}
}
