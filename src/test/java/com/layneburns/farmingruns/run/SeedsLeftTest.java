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

public class SeedsLeftTest
{
	@Test
	public void countsUnplantedPatchesAgainstWhatYouCarry()
	{
		GameData data = new DataLoader(new Gson()).load();
		int ranarr = 5295;
		int seedBox = data.getItems().group("seed_box").iterator().next();
		RunProgress progress = RunProgress.start(List.of(
			new RunProgress.Stop("catherby", List.of(new PlantingPlan.Assignment(data.patch("catherby.herb"),
				data.seedByItem(ranarr), false))),
			new RunProgress.Stop("falador", List.of(new PlantingPlan.Assignment(data.patch("falador.herb"),
				data.seedByItem(ranarr), false))),
			new RunProgress.Stop("ardougne", List.of(new PlantingPlan.Assignment(data.patch("ardougne.herb"),
				data.seedByItem(ranarr), false)))));
		// Catherby planted, Ardougne skipped: one more ranarr needed. One loose, two in a carried seed box.
		progress = progress.markWorked("catherby.herb").skip("ardougne");
		ItemLedger ledger = ItemLedger.EMPTY
			.with(ItemSource.INVENTORY, new StorageSnapshot(Map.of(ranarr, 1, seedBox, 1), 1))
			.with(ItemSource.SEED_BOX, new StorageSnapshot(Map.of(ranarr, 2), 1));
		assertEquals(List.of(new SeedsLeft.Line("Ranarr seed", 1, 3)), SeedsLeft.of(progress, ledger, Set.of(seedBox)));
	}
}
