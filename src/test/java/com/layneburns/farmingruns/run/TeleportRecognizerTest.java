/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class TeleportRecognizerTest
{
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	private static int anyItem(String method)
	{
		return data.teleport(method).getItemIds().iterator().next();
	}

	@Test
	public void itemsWithTheDestinationInTheOption()
	{
		assertEquals("skills_necklace:farming_guild",
			TeleportRecognizer.recognise("Farming Guild", "Skills necklace(4)", anyItem("skills_necklace"), data));
		assertEquals("ardougne_cloak:farm",
			TeleportRecognizer.recognise("Farm Teleport", "Ardougne cloak 3", anyItem("ardougne_cloak"), data));
	}

	@Test
	public void dialogueItemsAreResolvedFromTheLocationsList()
	{
		String rubbed = TeleportRecognizer.recognise("Rub", "Xeric's talisman", anyItem("xerics_talisman"), data);
		assertEquals("xerics_talisman", rubbed);
		assertEquals("xerics_talisman:glade", TeleportRecognizer.resolve(rubbed,
			List.of("book_of_the_dead:hosidius", "xerics_talisman:glade"), data));
		assertNull(TeleportRecognizer.resolve(rubbed, List.of("kourend_castle_teleport"), data));
	}

	@Test
	public void spellsFairyRingsAndPortals()
	{
		assertEquals("varrock_teleport", TeleportRecognizer.recognise("Cast", "Varrock Teleport", -1, data));
		assertEquals("fairy_ring:cir", TeleportRecognizer.recognise("Last-destination (CIR)", "Fairy ring", -1, data));
		assertEquals("house_portal:weiss", TeleportRecognizer.recognise("Enter", "Weiss Portal", -1, data));
		assertEquals("house_portal:troll_stronghold",
			TeleportRecognizer.recognise("Troll Stronghold", "Portal Nexus", -1, data));
	}

	@Test
	public void ordinaryClicksAreIgnored()
	{
		assertNull(TeleportRecognizer.recognise("Wear", "Skills necklace(4)", anyItem("skills_necklace"), data));
		assertNull(TeleportRecognizer.recognise("Walk here", "", -1, data));
		assertNull(TeleportRecognizer.recognise("Cast", "Wind Strike", -1, data));
	}
}
