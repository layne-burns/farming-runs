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
import com.layneburns.farmingruns.data.GoalPreset;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;

public class GoalSetupsTest
{
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	private static GoalPreset goal(String id)
	{
		return data.goals().stream().filter(g -> g.getId().equals(id)).findFirst()
			.orElseThrow(() -> new AssertionError("no goal " + id));
	}

	@Test
	public void prayerPotionPlantsRanarrAndSnapeGrass()
	{
		RunSetup setup = GoalSetups.toSetup(goal("prayer_potion"));
		assertEquals(Set.of(RunType.HERB, RunType.ALLOTMENT), setup.getRunTypes());
		assertEquals(ItemID.RANARR_SEED, setup.kindPlan(PatchImplementation.HERB).getCrops().get(0).getSeedItemId());
		assertEquals(ItemID.SNAPE_GRASS_SEED, setup.kindPlan(PatchImplementation.ALLOTMENT).getCrops().get(0).getSeedItemId());
		assertFalse(setup.kindPlan(PatchImplementation.HERB).isAuto());
	}

	@Test
	public void aGoalsHerbsShareByPatchesNeeded()
	{
		// Super combat: one torstol per potion, 4/3 of each of irit, kwuarm and cadantine (similar yields),
		// so torstol gets about three quarters of the share of each of the others.
		List<CropShare> herbs = GoalSetups.toSetup(goal("super_combat")).kindPlan(PatchImplementation.HERB).getCrops();
		assertEquals(4, herbs.size());
		CropShare torstol = herbs.stream().filter(c -> c.getSeedItemId() == ItemID.TORSTOL_SEED).findFirst().orElseThrow();
		CropShare irit = herbs.stream().filter(c -> c.getSeedItemId() == ItemID.IRIT_SEED).findFirst().orElseThrow();
		double ratio = (double) torstol.getWeight() / irit.getWeight();
		assertTrue("ratio " + ratio, ratio > 0.7 && ratio < 0.8);
	}

	@Test
	public void twoGoalsWantingHerbsSplitEvenly()
	{
		RunSetup combined = PresetCombiner.combine(List.of(
			GoalSetups.toSetup(goal("prayer_potion")), GoalSetups.toSetup(goal("super_restore"))));
		List<CropShare> herbs = combined.kindPlan(PatchImplementation.HERB).getCrops();
		assertEquals(2, herbs.size());
		assertEquals(herbs.get(0).getWeight(), herbs.get(1).getWeight());
	}

	@Test
	public void everyGoalBecomesASetup()
	{
		for (GoalPreset goal : data.goals())
		{
			RunSetup setup = GoalSetups.toSetup(goal);
			assertFalse(goal.getId() + " has no run types", setup.getRunTypes().isEmpty());
		}
		assertEquals(38, data.goals().size());
	}
}
