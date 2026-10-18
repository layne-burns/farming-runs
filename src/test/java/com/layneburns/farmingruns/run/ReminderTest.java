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
import com.layneburns.farmingruns.data.Seed;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class ReminderTest
{
	@Test
	public void leprechaunThenCompostThenPlantThenPay()
	{
		GameData data = new DataLoader(new Gson()).load();
		Seed watermelon = data.seedByItem(5321);
		PlantingPlan.Assignment north = new PlantingPlan.Assignment(data.patch("catherby.allotment_north"), watermelon,
			false);
		RunProgress.Stop stop = new RunProgress.Stop("catherby", List.of(north));
		RunProgress progress = RunProgress.start(List.of(stop), Set.of("catherby.allotment_north"), Set.of(), Set.of());
		String payment = watermelon.getPayment().get(0).getAmount() + " × " + watermelon.getPayment().get(0).getName();

		assertEquals("Take from the leprechaun: 9 × Supercompost", Reminder.next(stop, progress,
			CompostChoice.SUPERCOMPOST, Set.of(), a -> true, List.of("9 × Supercompost")));
		assertEquals("Compost the allotment patch, then plant Watermelon seed", Reminder.next(stop, progress,
			CompostChoice.SUPERCOMPOST, Set.of(), a -> true, List.of()));
		assertEquals("Plant Watermelon seed", Reminder.next(stop, progress, CompostChoice.SUPERCOMPOST,
			Set.of("catherby.allotment_north"), a -> true, List.of()));
		progress = progress.markWorked("catherby.allotment_north");
		assertEquals("Pay the gardener: " + payment, Reminder.next(stop, progress, CompostChoice.SUPERCOMPOST,
			Set.of("catherby.allotment_north"), a -> true, List.of()));
		progress = progress.markPaid("catherby.allotment_north");
		// Planted and paid but the stop still waits for compost (when compost is chosen).
		RunProgress waiting = RunProgress.start(List.of(stop), Set.of(), Set.of("catherby.allotment_north"), Set.of())
			.markWorked("catherby.allotment_north");
		assertEquals("Compost the allotment patch", Reminder.next(stop, waiting, CompostChoice.SUPERCOMPOST, Set.of(),
			a -> false, List.of()));
		assertNull(Reminder.next(stop, progress, CompostChoice.SUPERCOMPOST, Set.of("catherby.allotment_north"),
			a -> true, List.of()));
	}

	@Test
	public void onTheWaySaysWhereAndHow()
	{
		GameData data = new DataLoader(new Gson()).load();
		assertEquals("Next: Weiss · House portal → Weiss",
			Reminder.travel("Weiss", data.teleportChoice("house_portal:weiss")));
		assertEquals("Next: Lumbridge hops · walk", Reminder.travel("Lumbridge hops", null));
	}
}
