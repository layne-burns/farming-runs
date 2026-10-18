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
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.vendor.timetracking.CropState;
import com.layneburns.farmingruns.vendor.timetracking.PatchState;
import com.layneburns.farmingruns.vendor.timetracking.Produce;
import java.util.List;
import java.util.Set;
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
		RunProgress progress = run().markWorked("catherby.herb");
		assertEquals("catherby", progress.currentStop().getLocationId());
		progress = progress.markWorked("catherby.allotment_north");
		assertEquals("falador", progress.currentStop().getLocationId());
		progress = progress.markWorked("falador.herb").markWorked("ardougne.herb");
		assertTrue(progress.isFinished());
		assertEquals(3, progress.doneCount());
	}

	@Test
	public void skippingAndJumping()
	{
		RunProgress progress = run().skip("catherby");
		assertEquals("falador", progress.currentStop().getLocationId());
		progress = progress.goTo("ardougne").markWorked("ardougne.herb");
		// After Ardougne, back round to the first stop that isn't done or skipped: Falador.
		assertEquals("falador", progress.currentStop().getLocationId());
		progress = progress.markWorked("falador.herb");
		assertTrue(progress.isFinished());
		// A skipped stop can still be picked again.
		progress = progress.goTo("catherby");
		assertEquals("catherby", progress.currentStop().getLocationId());
		assertFalse(progress.isSkipped(progress.currentStop()));
	}

	@Test
	public void aPaidPatchWaitsForItsPayment()
	{
		RunProgress progress = RunProgress.start(List.of(stop("catherby", "catherby.allotment_north"),
			stop("falador", "falador.herb")), Set.of("catherby.allotment_north"), Set.of());
		progress = progress.markWorked("catherby.allotment_north");
		assertEquals("catherby", progress.currentStop().getLocationId());
		assertTrue(progress.isAwaitingPayment("catherby.allotment_north"));
		progress = progress.markPaid("catherby.allotment_north");
		assertEquals("falador", progress.currentStop().getLocationId());
	}

	@Test
	public void aStopWaitsForCompostOnlyWhenCompostIsChosen()
	{
		List<RunProgress.Stop> stops = List.of(stop("catherby", "catherby.herb"), stop("falador", "falador.herb"));
		// Compost chosen: planted isn't enough until the game says it was treated.
		RunProgress composting = RunProgress.start(stops, Set.of(), Set.of("catherby.herb"))
			.markWorked("catherby.herb");
		assertEquals("catherby", composting.currentStop().getLocationId());
		assertEquals("falador", composting.markComposted("catherby.herb").currentStop().getLocationId());
		// No compost: planting finishes it.
		RunProgress none = RunProgress.start(stops, Set.of(), Set.of()).markWorked("catherby.herb");
		assertEquals("falador", none.currentStop().getLocationId());
	}

	@Test
	public void guidancePointsOutSeedsCompostThenThePayment()
	{
		Seed watermelon = data.seedByItem(5321);
		PlantingPlan.Assignment north = new PlantingPlan.Assignment(data.patch("catherby.allotment_north"), watermelon,
			false);
		RunProgress.Stop catherby = new RunProgress.Stop("catherby", List.of(north));
		RunProgress progress = RunProgress.start(List.of(catherby), Set.of("catherby.allotment_north"), Set.of());
		Set<Integer> supercompost = data.getItems().group("supercompost");
		int payment = watermelon.getPayment().get(0).getItemId();

		RunGuidance.Highlights before = RunGuidance.at(catherby, progress, CompostChoice.SUPERCOMPOST, Set.of(),
			data.getItems());
		assertTrue(before.getItems().contains(watermelon.plantItemId()));
		assertTrue(before.getItems().containsAll(supercompost));
		assertFalse(before.getItems().contains(payment));
		assertTrue(before.getNpcs().isEmpty());

		// Composted and planted: now the payment and the gardener.
		progress = progress.markWorked("catherby.allotment_north");
		RunGuidance.Highlights planted = RunGuidance.at(catherby, progress, CompostChoice.SUPERCOMPOST,
			Set.of("catherby.allotment_north"), data.getItems());
		assertEquals(Set.of(payment), planted.getItems());
		assertEquals(Set.of(north.getPatch().getGardenerNpcId()), planted.getNpcs());

		// Paid: nothing left to point out.
		progress = progress.markPaid("catherby.allotment_north");
		assertEquals(RunGuidance.NONE, RunGuidance.at(progress.currentStop(), progress, CompostChoice.SUPERCOMPOST,
			Set.of("catherby.allotment_north"), data.getItems()));
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
