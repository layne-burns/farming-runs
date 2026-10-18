/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.TeleportChoice;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class TravelChainTest
{
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	@Test
	public void aFairyRingThroughYourHouseCountsDownEachJump()
	{
		List<String> steps = TravelChain.steps(data.teleportChoice("fairy_ring:ckq"), "Salve Graveyard");
		assertEquals(List.of("House teleport", "Salve Graveyard portal", "Fairy ring CKQ"), steps);
		assertEquals("Next: Great Conch · House teleport → Salve Graveyard portal → Fairy ring CKQ",
			Reminder.travel("Great Conch", TravelChain.remaining(steps, 0)));
		assertEquals(List.of("Fairy ring CKQ"), TravelChain.remaining(steps, 2));
		assertEquals(List.of(), TravelChain.remaining(steps, 3));
	}

	@Test
	public void housePortalsAndWalkingAndSingleTeleports()
	{
		assertEquals(List.of("House teleport", "Weiss portal"), TravelChain.steps(data.teleportChoice("house_portal:weiss"),
			null));
		assertEquals(List.of("walk"), TravelChain.remaining(TravelChain.steps(null, null), 4));
		TeleportChoice cloak = data.teleportChoice("ardougne_cloak:farm");
		assertEquals(List.of(cloak.displayName()), TravelChain.steps(cloak, "Salve Graveyard"));
	}

	@Test
	public void theWayYouWentLastTimeFillsInWhatIsntKnown()
	{
		List<String> learned = List.of("Sailors' amulet → Deepfin Point", "Charter Trader Crewmember",
			"Travel Captain Barnaby");
		TeleportChoice ring = data.teleportChoice("explorers_ring");
		assertEquals(learned, TravelChain.steps(ring, null, learned));
		// A chain the plugin knows itself beats a learned one.
		assertEquals(List.of("House teleport", "Weiss portal"),
			TravelChain.steps(data.teleportChoice("house_portal:weiss"), null, learned));
	}
}
