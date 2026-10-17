/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.state.PlayerFacts;
import com.layneburns.farmingruns.state.StorageSnapshot;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;
import org.junit.BeforeClass;
import org.junit.Test;

public class TeleportPickerTest
{
	private static final int CAMELOT_TABLET = 8010;
	private static final int LAW_RUNE = 563;
	private static final int AIR_RUNE = 556;
	private static final List<String> CATHERBY = List.of("camelot_teleport", "catherby_teleport", "camelot_tablet");
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	static PlayerFacts facts(int spellbook, Map<Integer, Integer> varbits)
	{
		return new PlayerFacts(true, Map.of(), varbits, Map.of(), spellbook);
	}

	static ItemLedger ledger(ItemSource source, Map<Integer, Integer> items)
	{
		return ItemLedger.EMPTY.with(source, new StorageSnapshot(items, 1));
	}

	@Test
	public void theFirstOneYouOwnWins()
	{
		// No runes for Camelot Teleport, but a tablet in the bank: the tablet.
		ItemLedger tablet = ledger(ItemSource.BANK, Map.of(CAMELOT_TABLET, 5));
		assertEquals("camelot_tablet", TeleportPicker.pick(CATHERBY, data, facts(0, Map.of()), tablet).getRef());

		// The runes for a cast anywhere: the spell, ranked first.
		ItemLedger runes = tablet.with(ItemSource.RUNE_POUCH, new StorageSnapshot(
			Map.of(LAW_RUNE, 10, AIR_RUNE, 100), 1));
		assertEquals("camelot_teleport", TeleportPicker.pick(CATHERBY, data, facts(0, Map.of()), runes).getRef());
	}

	@Test
	public void spellsFromAnotherSpellbookAreSkipped()
	{
		// On Lunar with nothing owned: Camelot Teleport can't be cast, so the first usable one is Catherby Teleport.
		assertEquals("catherby_teleport", TeleportPicker.pick(CATHERBY, data, facts(2, Map.of()), ItemLedger.EMPTY)
			.getRef());
	}

	@Test
	public void fairyRingsNeedNoStaffAfterTheEliteLumbridgeDiary()
	{
		List<String> guild = List.of("skills_necklace:farming_guild", "farming_cape", "fairy_ring:cir");
		PlayerFacts diary = facts(0, Map.of(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 1));
		assertEquals("fairy_ring:cir", TeleportPicker.pick(guild, data, diary, ItemLedger.EMPTY).getRef());
		// Without it and with nothing owned, the first in your list.
		assertEquals("skills_necklace:farming_guild",
			TeleportPicker.pick(guild, data, facts(0, Map.of()), ItemLedger.EMPTY).getRef());
	}

	@Test
	public void unknownRefsGiveNothing()
	{
		assertNull(TeleportPicker.pick(List.of("no_such_teleport"), data, facts(0, Map.of()), ItemLedger.EMPTY));
	}
}
