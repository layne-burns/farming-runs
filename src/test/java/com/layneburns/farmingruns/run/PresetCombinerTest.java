/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class PresetCombinerTest
{
	private static RunSetup herbsOf(int... seeds)
	{
		List<CropShare> crops = new ArrayList<>();
		for (int seed : seeds)
		{
			crops.add(CropShare.even(seed));
		}
		return RunSetup.EMPTY.withRunTypes(Set.of(RunType.HERB))
			.withKindPlan(PatchImplementation.HERB, KindPlan.AUTO.withAuto(false).withCrops(List.copyOf(crops)));
	}

	@Test
	public void twoPresetsWantingHerbsSplitFiftyFifty()
	{
		// Prayer potion: ranarr. Super restore: snapdragon. Each preset gets half the herb patches.
		RunSetup combined = PresetCombiner.combine(List.of(herbsOf(ItemID.RANARR_SEED), herbsOf(ItemID.SNAPDRAGON_SEED)));
		KindPlan herbs = combined.kindPlan(PatchImplementation.HERB);
		assertEquals(2, herbs.getCrops().size());
		assertEquals(herbs.getCrops().get(0).getWeight(), herbs.getCrops().get(1).getWeight());
	}

	@Test
	public void eachPresetGetsAnEqualShareWhateverItsOwnSplit()
	{
		// One preset splits herbs between two crops, the other has one: 25 / 25 / 50.
		RunSetup combined = PresetCombiner.combine(List.of(
			herbsOf(ItemID.RANARR_SEED, ItemID.TOADFLAX_SEED), herbsOf(ItemID.SNAPDRAGON_SEED)));
		List<CropShare> crops = combined.kindPlan(PatchImplementation.HERB).getCrops();
		assertEquals(crops.get(0).getWeight(), crops.get(1).getWeight());
		assertEquals(crops.get(0).getWeight() * 2, crops.get(2).getWeight());
	}

	@Test
	public void manyPresetsKeepSmallNumbers()
	{
		// Six presets each sharing herbs between several crops once overflowed the share numbers.
		List<RunSetup> presets = new ArrayList<>();
		for (int i = 0; i < 6; i++)
		{
			presets.add(herbsOf(ItemID.RANARR_SEED, ItemID.TOADFLAX_SEED, ItemID.SNAPDRAGON_SEED));
		}
		for (CropShare crop : PresetCombiner.combine(presets).kindPlan(PatchImplementation.HERB).getCrops())
		{
			assertTrue("weight " + crop.getWeight(), crop.getWeight() > 0 && crop.getWeight() <= 1000);
		}
	}

	@Test
	public void runTypesAndSkipsAreJoinedAndAutoKindsStayAuto()
	{
		RunSetup trees = RunSetup.EMPTY.withRunTypes(Set.of(RunType.TREE))
			.withKindPlan(PatchImplementation.TREE, KindPlan.AUTO.withPay(true)).withSkipped("varrock.tree", true);
		RunSetup combined = PresetCombiner.combine(List.of(herbsOf(ItemID.RANARR_SEED), trees));
		assertEquals(Set.of(RunType.HERB, RunType.TREE), combined.getRunTypes());
		assertTrue(combined.getSkippedPatches().contains("varrock.tree"));
		assertTrue(combined.kindPlan(PatchImplementation.TREE).isAuto());
		assertTrue(combined.kindPlan(PatchImplementation.TREE).isPay());
	}

	@Test
	public void applyingAPresetKeepsYourPlansForKindsItDoesntCover()
	{
		KindPlan pickOnly = KindPlan.AUTO.withTreeRoutine(TreeRoutine.LEAVE_STANDING);
		RunSetup current = RunSetup.EMPTY.withRunTypes(Set.of(RunType.FRUIT_TREE))
			.withKindPlan(PatchImplementation.FRUIT_TREE, pickOnly);
		RunSetup herbs = RunSetup.EMPTY.withRunTypes(Set.of(RunType.HERB))
			.withKindPlan(PatchImplementation.HERB, KindPlan.AUTO.withPay(true));
		RunSetup applied = PresetCombiner.applyOver(current, herbs);
		assertEquals(Set.of(RunType.HERB), applied.getRunTypes());
		assertEquals(pickOnly, applied.kindPlan(PatchImplementation.FRUIT_TREE));
		assertEquals(KindPlan.AUTO.withPay(true), applied.kindPlan(PatchImplementation.HERB));
	}
}
