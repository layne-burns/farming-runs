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
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.state.BufferSettings;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;

public class SeedPickerTest
{
	private static final BufferSettings BUFFERS = new BufferSettings(9, 2, 3);
	private static GameData data;
	private static List<Patch> herbs;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
		herbs = data.patches().stream().filter(p -> p.getKind() == PatchImplementation.HERB)
			.sorted((a, b) -> a.getId().compareTo(b.getId())).collect(Collectors.toList());
	}

	@Test
	public void autoPicksTheBestYouCanSpareAndMixesWhenShort()
	{
		// Level 70: lantadyme (73) is out. Snapdragon: 4 held, 3 kept back, so 1 spare. Ranarr: 9 spare.
		Map<Integer, Integer> held = Map.of(ItemID.SNAPDRAGON_SEED, 4, ItemID.RANARR_SEED, 12,
			ItemID.GUAM_SEED, 100, ItemID.LANTADYME_SEED, 50);
		PlantingPlan plan = SeedPicker.plan(herbRun(), herbs, held, BUFFERS, 70, data, null);
		Map<String, Long> counts = countsBySeed(plan);
		assertEquals(1L, (long) counts.get("Snapdragon seed"));
		assertEquals(Math.min(9, herbs.size() - 1), (long) counts.get("Ranarr seed"));
		assertFalse(counts.containsKey("Lantadyme seed"));
		assertEquals(herbs.size(), plan.plantedCount());
	}

	@Test
	public void chosenCropsShareEvenlyAndShortfallsAreFilledAutomatically()
	{
		RunSetup setup = herbRun().withKindPlan(PatchImplementation.HERB, KindPlan.AUTO.withAuto(false)
			.withCrops(List.of(CropShare.even(ItemID.RANARR_SEED), CropShare.even(ItemID.TOADFLAX_SEED))));
		Map<Integer, Integer> held = Map.of(ItemID.RANARR_SEED, 100, ItemID.TOADFLAX_SEED, 4, ItemID.GUAM_SEED, 100);
		PlantingPlan plan = SeedPicker.plan(setup, herbs, held, BUFFERS, 99, data, null);
		Map<String, Long> counts = countsBySeed(plan);
		// Toadflax has 1 spare, so ranarr takes the rest of the patches.
		assertEquals(1L, (long) counts.get("Toadflax seed"));
		assertEquals(herbs.size() - 1, (long) counts.get("Ranarr seed"));
	}

	@Test
	public void mostXpFirstFillsFromTheTopOfYourList()
	{
		// Chosen: ranarr, snapdragon, avantoe. Most XP first: snapdragon (2 spare), then avantoe (2), then ranarr.
		RunSetup setup = herbRun().withKindPlan(PatchImplementation.HERB, KindPlan.AUTO.withAuto(false)
			.withFillOrder(FillOrder.MOST_XP)
			.withCrops(List.of(CropShare.even(ItemID.RANARR_SEED), CropShare.even(ItemID.SNAPDRAGON_SEED),
				CropShare.even(ItemID.AVANTOE_SEED))));
		Map<Integer, Integer> held = Map.of(ItemID.RANARR_SEED, 100, ItemID.SNAPDRAGON_SEED, 5, ItemID.AVANTOE_SEED, 5);
		PlantingPlan plan = SeedPicker.plan(setup, herbs, held, BUFFERS, 99, data, null);
		Map<String, Long> counts = countsBySeed(plan);
		assertEquals(2L, (long) counts.get("Snapdragon seed"));
		assertEquals(2L, (long) counts.get("Avantoe seed"));
		assertEquals(herbs.size() - 4, (long) counts.get("Ranarr seed"));
		// The first patches get the highest-XP seed.
		assertEquals("Snapdragon seed", plan.getAssignments().get(0).getSeed().getName());
	}

	@Test
	public void aHarvestRunPlantsNothingAndWarnsOfNothing()
	{
		List<Patch> fruitTrees = data.patches().stream().filter(p -> p.getKind() == PatchImplementation.FRUIT_TREE)
			.collect(Collectors.toList());
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.FRUIT_TREE)).withKindPlan(
			PatchImplementation.FRUIT_TREE, KindPlan.AUTO.withTreeRoutine(TreeRoutine.LEAVE_STANDING));
		// 20 papaya saplings (5501) held, but a harvest run plants none.
		PlantingPlan plan = SeedPicker.plan(setup, fruitTrees, Map.of(5501, 20), BUFFERS, 99,
			data, null);
		assertEquals(fruitTrees.size(), plan.getAssignments().size());
		assertEquals(0, plan.plantedCount());
		assertTrue(plan.getWarnings().isEmpty());
	}

	@Test
	public void treeSeedsCountOnlyWhenGrowingSaplingsIsOn()
	{
		List<Patch> fruitTrees = data.patches().stream().filter(p -> p.getKind() == PatchImplementation.FRUIT_TREE)
			.limit(4).collect(Collectors.toList());
		// 1 papaya sapling (5501) and 5 papaya seeds (5288); 2 saplings are kept back.
		Map<Integer, Integer> held = Map.of(5501, 1, 5288, 5);
		RunSetup saplingsOnly = RunSetup.EMPTY.withRunTypes(Set.of(RunType.FRUIT_TREE));
		PlantingPlan without = SeedPicker.plan(saplingsOnly, fruitTrees, held, BUFFERS, 99, data, null);
		assertEquals(0, without.plantedCount());
		assertTrue(without.getSaplingsToGrow().isEmpty());

		RunSetup grow = saplingsOnly.withKindPlan(PatchImplementation.FRUIT_TREE, KindPlan.AUTO.withGrowSaplings(true));
		PlantingPlan with = SeedPicker.plan(grow, fruitTrees, held, BUFFERS, 99, data, null);
		// Two of each are kept back: no spare sapling, 3 spare seeds, so 3 patches, all grown from seed.
		assertEquals(3, with.plantedCount());
		assertEquals(List.of("3 × Papaya sapling (from Papaya tree seed)"),
			with.growNotes(PatchImplementation.FRUIT_TREE));

		// 4 saplings and 5 seeds: 2 spare saplings are used first, then 2 of the 3 spare seeds.
		PlantingPlan both = SeedPicker.plan(grow, fruitTrees, Map.of(5501, 4, 5288, 5), BUFFERS, 99, data, null);
		assertEquals(4, both.plantedCount());
		assertEquals(2, (int) both.getSaplingsToGrow().get(data.seedByItem(5288)));
	}

	@Test
	public void treesOfAKeptTypeAreLeftStandingAndTheRestReplanted()
	{
		List<Patch> fruitTrees = data.patches().stream().filter(p -> p.getKind() == PatchImplementation.FRUIT_TREE)
			.sorted((a, b) -> a.getId().compareTo(b.getId())).limit(4).collect(Collectors.toList());
		Seed papaya = data.seedByItem(5288);
		Seed apple = data.seedForCrop(PatchImplementation.FRUIT_TREE, "Apple");
		// Growing: papaya, papaya, apple, unknown. Keep papaya standing.
		Map<Patch, Seed> growing = new HashMap<>();
		growing.put(fruitTrees.get(0), papaya);
		growing.put(fruitTrees.get(1), papaya);
		growing.put(fruitTrees.get(2), apple);
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.FRUIT_TREE)).withKindPlan(
			PatchImplementation.FRUIT_TREE, KindPlan.AUTO.withKeepStanding(Set.of(papaya.getItemId())));
		PlantingPlan plan = SeedPicker.plan(setup, fruitTrees, Map.of(5501, 20), BUFFERS, 99, data, null,
			growing::get);
		assertEquals(2, plan.keptCount());
		assertEquals(2, plan.plantedCount());
		assertTrue(plan.getAssignments().stream().filter(PlantingPlan.Assignment::isKeptStanding)
			.allMatch(a -> a.getStanding() == papaya));
		assertTrue(plan.getWarnings().isEmpty());
	}

	@Test
	public void theContractGetsItsPatchEvenFromTheBuffer()
	{
		Patch guildHerb = data.patch("farming_guild.herb");
		SeedPicker.Contract contract = new SeedPicker.Contract(guildHerb.getId(), data.seedByItem(ItemID.RANARR_SEED));
		// 3 ranarr held, all inside the buffer of 3: none spare, but the contract may still use one.
		Map<Integer, Integer> held = Map.of(ItemID.RANARR_SEED, 3, ItemID.GUAM_SEED, 100);
		PlantingPlan plan = SeedPicker.plan(herbRun(), herbs, held, BUFFERS, 99, data, contract);
		PlantingPlan.Assignment guild = plan.getAssignments().stream()
			.filter(a -> a.getPatch() == guildHerb).findFirst().orElseThrow();
		assertTrue(guild.isContract());
		assertEquals("Ranarr seed", guild.getSeed().getName());
		assertEquals(1L, (long) countsBySeed(plan).get("Ranarr seed"));
	}

	@Test
	public void treesPlantSaplingsAndKeepTheSaplingBuffer()
	{
		List<Patch> trees = data.patches().stream().filter(p -> p.getKind() == PatchImplementation.TREE)
			.collect(Collectors.toList());
		RunSetup setup = RunSetup.EMPTY.withRunTypes(Set.of(RunType.TREE));
		Map<Integer, Integer> held = Map.of(ItemID.PLANTPOT_MAGIC_TREE_SAPLING, 4, ItemID.MAGIC_TREE_SEED, 50);
		PlantingPlan plan = SeedPicker.plan(setup, trees, held, BUFFERS, 99, data, null);
		assertEquals(2L, plan.plantedCount());
		assertEquals(Map.of(ItemID.PLANTPOT_MAGIC_TREE_SAPLING, 2), plan.itemsToPlant());
		assertTrue(plan.getWarnings().stream().anyMatch(w -> w.contains("without a seed you can spare")));
	}

	@Test
	public void allotmentsUseThreeSeedsPerPatch()
	{
		List<Patch> allotments = data.patches().stream().filter(p -> p.getKind() == PatchImplementation.ALLOTMENT)
			.limit(2).collect(Collectors.toList());
		// 15 held, 9 kept back: 6 spare = 2 patches.
		PlantingPlan plan = SeedPicker.plan(RunSetup.EMPTY.withRunTypes(Set.of(RunType.ALLOTMENT)), allotments,
			Map.of(ItemID.POTATO_SEED, 15), BUFFERS, 99, data, null);
		assertEquals(Map.of(ItemID.POTATO_SEED, 6), plan.itemsToPlant());
	}

	private static RunSetup herbRun()
	{
		return RunSetup.EMPTY.withRunTypes(Set.of(RunType.HERB));
	}

	private static Map<String, Long> countsBySeed(PlantingPlan plan)
	{
		return plan.getAssignments().stream().filter(a -> a.getSeed() != null)
			.collect(Collectors.groupingBy(a -> a.getSeed().getName(), Collectors.counting()));
	}
}
