/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class PatchColoursTest
{
	@Test
	public void seedsTakeTheirPatchKindsColourAndOtherItemsTheUsualOne()
	{
		GameData data = new DataLoader(new Gson()).load();
		PatchColours colours = new PatchColours(data);
		assertEquals(PatchColours.of(PatchImplementation.HERB), colours.ofItem(ItemID.RANARR_SEED));
		assertEquals(PatchColours.of(PatchImplementation.ALLOTMENT), colours.ofItem(5321));
		assertNotEquals(colours.ofItem(ItemID.RANARR_SEED), colours.ofItem(5321));
		assertEquals(PatchColours.DEFAULT, colours.ofItem(ItemID.BUCKET_SUPERCOMPOST));
	}
}
