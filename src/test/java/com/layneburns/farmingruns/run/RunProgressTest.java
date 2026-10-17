/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.vendor.timetracking.CropState;
import com.layneburns.farmingruns.vendor.timetracking.PatchState;
import com.layneburns.farmingruns.vendor.timetracking.Produce;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;

public class RunProgressTest
{
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	private static RunProgress.Stop stop(String locationId, String... patchIds)
	{
		List<PlantingPlan.Assignment> patches = List.of(patchIds).stream()
			.map(id -> new PlantingPlan.Assignment(data.patch(id), data.seedByItem(5295), false))
			.collect(Collectors.toList());
		return new RunProgress.Stop(locationId, patches);
	}

	private static RunProgress run()
	{
		return RunProgress.start(List.of(
			stop("catherby", "catherby.herb", "catherby.allotment_north"),
			stop("falador", "falador.herb"),
			stop("ardougne", "ardougne.herb")));
	}

	@Test
	public void movesOnWhenEveryPatchAtTheStopIsDone()
	{
		RunProgress progress = run().markDone("catherby.herb");
		assertEquals("catherby", progress.currentStop().getLocationId());
		progress = progress.markDone("catherby.allotment_north");
		assertEquals("falador", progress.currentStop().getLocationId());
		progress = progress.markDone("falador.herb").markDone("ardougne.herb");
		assertTrue(progress.isFinished());
		assertEquals(3, progress.doneCount());
	}

	@Test
	public void skippingAndJumping()
	{
		RunProgress progress = run().skip("catherby");
		assertEquals("falador", progress.currentStop().getLocationId());
		progress = progress.goTo("ardougne").markDone("ardougne.herb");
		// After Ardougne, back round to the first stop that isn't done or skipped: Falador.
		assertEquals("falador", progress.currentStop().getLocationId());
		progress = progress.markDone("falador.herb");
		assertTrue(progress.isFinished());
		// A skipped stop can still be picked again.
		progress = progress.goTo("catherby");
		assertEquals("catherby", progress.currentStop().getLocationId());
		assertFalse(progress.isSkipped(progress.currentStop()));
	}

	@Test
	public void patchIsDoneWhenPlantedPickedOrCleared()
	{
		PlantingPlan.Assignment plant = new PlantingPlan.Assignment(data.patch("catherby.herb"), data.seedByItem(5295),
			false);
		assertTrue(PatchProgress.isDone(plant, new PatchState(Produce.RANARR, CropState.GROWING, 0)));
		assertFalse(PatchProgress.isDone(plant, new PatchState(Produce.RANARR, CropState.HARVESTABLE, 2)));
		assertFalse(PatchProgress.isDone(plant, new PatchState(Produce.WEEDS, CropState.GROWING, 3)));

		PlantingPlan.Assignment kept = new PlantingPlan.Assignment(data.patch("gnome_stronghold.fruit_tree"), null,
			false, true, null);
		assertFalse(PatchProgress.isDone(kept, new PatchState(Produce.PAPAYA, CropState.HARVESTABLE, 6)));
		assertTrue(PatchProgress.isDone(kept, new PatchState(Produce.PAPAYA, CropState.HARVESTABLE, 0)));

		PlantingPlan.Assignment nothing = new PlantingPlan.Assignment(data.patch("falador.herb"), null, false);
		assertTrue(PatchProgress.isDone(nothing, new PatchState(Produce.WEEDS, CropState.GROWING, 3)));
		assertFalse(PatchProgress.isDone(nothing, null));
	}
}
