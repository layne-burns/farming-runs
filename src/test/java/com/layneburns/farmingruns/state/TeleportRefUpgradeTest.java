/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import java.util.List;
import java.util.function.Predicate;
import org.junit.BeforeClass;
import org.junit.Test;

public class TeleportRefUpgradeTest
{
	private static GameData data;
	private static Predicate<String> valid;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
		valid = ref -> data.teleportChoice(ref) != null;
	}

	@Test
	public void anOldArdougneOrderKeepsTheCloakFirst()
	{
		// Saved before teleports had destinations: cloak first, then the spell, then the fairy ring.
		List<String> saved = List.of("ardougne_cloak_farm", "ardougne_teleport", "fairy_ring");
		assertEquals(List.of("ardougne_cloak:farm", "ardougne_teleport", "fairy_ring:blr"),
			TeleportRefUpgrade.upgradeAll(saved, data.location("ardougne"), valid));
	}

	@Test
	public void methodsTakeTheLocationsOwnDestination()
	{
		assertEquals("spirit_tree:brimhaven",
			TeleportRefUpgrade.upgrade("spirit_tree", data.location("brimhaven"), valid));
		assertEquals("quetzal_whistle:kastori",
			TeleportRefUpgrade.upgrade("quetzal_whistle", data.location("kastori"), valid));
		assertEquals("walk", TeleportRefUpgrade.upgrade("none", data.location("entrana"), valid));
	}

	@Test
	public void currentReferencesAreKeptAndUnknownOnesDropped()
	{
		assertEquals("skills_necklace:fishing_guild",
			TeleportRefUpgrade.upgrade("skills_necklace:fishing_guild", data.location("ardougne"), valid));
		assertNull(TeleportRefUpgrade.upgrade("no_such_teleport", data.location("ardougne"), valid));
	}
}
