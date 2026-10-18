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
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.state.StorageSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class LeprechaunNeedsTest
{
	@Test
	public void onlyWhatThisStopStillNeeds()
	{
		GameData data = new DataLoader(new Gson()).load();
		int supercompost = 6034;
		int spade = data.getItems().group("spade").iterator().next();
		RunProgress.Stop catherby = new RunProgress.Stop("catherby", List.of(
			new PlantingPlan.Assignment(data.patch("catherby.allotment_north"), data.seedByItem(5321), false),
			new PlantingPlan.Assignment(data.patch("catherby.allotment_south"), data.seedByItem(5321), false)));
		RunProgress progress = RunProgress.start(List.of(catherby));
		ItemLedger stored = ItemLedger.EMPTY.with(ItemSource.LEPRECHAUN,
			new StorageSnapshot(Map.of(supercompost, 20, spade, 1), 1));

		// Two patches to compost, one supercompost carried, no spade: take one more, and the spade.
		ItemLedger oneCarried = stored.with(ItemSource.INVENTORY, new StorageSnapshot(Map.of(supercompost, 1), 1));
		assertEquals(List.of("1 × Supercompost", "a spade"), LeprechaunNeeds.atStop(catherby, progress,
			CompostPlan.all(CompostChoice.SUPERCOMPOST), Set.of(), data.getItems(), oneCarried));

		// One patch already composted and a spade carried: nothing to take.
		ItemLedger enough = stored.with(ItemSource.INVENTORY,
			new StorageSnapshot(Map.of(supercompost, 1, spade, 1), 1));
		assertEquals(List.of(), LeprechaunNeeds.atStop(catherby, progress, CompostPlan.all(CompostChoice.SUPERCOMPOST),
			Set.of("catherby.allotment_north"), data.getItems(), enough));
	}
}
