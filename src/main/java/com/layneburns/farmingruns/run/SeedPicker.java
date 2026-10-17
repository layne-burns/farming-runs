/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.state.BufferSettings;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Decides what to plant in each patch of a run (SPEC §2.3). Pure: unit tested.
 * <ul>
 * <li>A farming contract's seed goes in its Farming Guild patch first; the buffer doesn't apply to it.</li>
 * <li>Auto: the highest-level seeds you can plant and spare, mixing in the next best when one runs short.</li>
 * <li>Chosen crops: patches shared by {@link ShareSplitter} (exact counts, then by weight); patches a crop can't
 * fill (not enough spare) are filled the auto way, with a warning.</li>
 * <li>"Spare" means what you hold beyond your buffer; nothing in the buffer is planted.</li>
 * </ul>
 */
public final class SeedPicker
{
	/** Your farming contract: which Farming Guild patch, and the seed it wants. */
	@Value
	public static class Contract
	{
		String patchId;
		Seed seed;
	}

	private SeedPicker()
	{
	}

	/**
	 * @param patches      the patches the run visits (usable, of a chosen kind, not skipped)
	 * @param held         seeds and saplings you hold (bank, vault, seed box, inventory), by item id
	 * @param farmingLevel your Farming level, or -1 if unknown (then no seed is ruled out by level)
	 * @param contract     your unplanted farming contract, or null
	 */
	public static PlantingPlan plan(RunSetup setup, List<Patch> patches, Map<Integer, Integer> held,
		BufferSettings buffers, int farmingLevel, GameData data, @Nullable Contract contract)
	{
		Map<Integer, Integer> left = new HashMap<>(held);
		List<PlantingPlan.Assignment> assignments = new ArrayList<>();
		List<String> warnings = new ArrayList<>();

		Map<PatchImplementation, List<Patch>> byKind = new LinkedHashMap<>();
		patches.forEach(p -> byKind.computeIfAbsent(p.getKind(), k -> new ArrayList<>()).add(p));

		for (Map.Entry<PatchImplementation, List<Patch>> entry : byKind.entrySet())
		{
			List<Patch> open = new ArrayList<>(entry.getValue());
			if (contract != null)
			{
				open.removeIf(p ->
				{
					if (!p.getId().equals(contract.getPatchId()))
					{
						return false;
					}
					Seed seed = contract.getSeed();
					if (take(left, seed, seed.getPerPatch()))
					{
						assignments.add(new PlantingPlan.Assignment(p, seed, true));
					}
					else
					{
						warnings.add("No " + seed.getName() + " for your farming contract");
						assignments.add(new PlantingPlan.Assignment(p, null, true));
					}
					return true;
				});
			}
			KindPlan plan = setup.kindPlan(entry.getKey());
			List<Seed> candidates = data.seedsFor(entry.getKey()).stream()
				.filter(s -> farmingLevel < 0 || s.getLevel() <= farmingLevel)
				.sorted(Comparator.comparingInt(Seed::getLevel).reversed())
				.collect(Collectors.toList());
			if (!plan.isAuto() && !plan.getCrops().isEmpty() && plan.getFillOrder() == FillOrder.MOST_XP)
			{
				// Your chosen seeds, highest XP first, each as far as your spare goes.
				List<Seed> chosen = plan.getCrops().stream()
					.map(c -> data.seedByItem(c.getSeedItemId()))
					.filter(s -> s != null && (farmingLevel < 0 || s.getLevel() <= farmingLevel))
					.sorted(Comparator.comparingDouble(FillOrder::xpScore).reversed())
					.collect(Collectors.toList());
				fillAuto(open, chosen, left, buffers, assignments);
				if (!open.isEmpty())
				{
					warnings.add("Not enough of your chosen seeds for " + open.size() + " patch"
						+ (open.size() == 1 ? "" : "es") + ": filled with the best you can spare");
				}
			}
			else if (!plan.isAuto() && !plan.getCrops().isEmpty())
			{
				open = fillChosen(plan, open, data, farmingLevel, left, buffers, assignments, warnings);
			}
			fillAuto(open, candidates, left, buffers, assignments);
			// Whatever is still open has no seed you can spare.
			if (!open.isEmpty())
			{
				warnings.add(open.size() + " " + kindName(entry.getKey()) + " patch" + (open.size() == 1 ? "" : "es")
					+ " without a seed you can spare");
			}
			open.forEach(p -> assignments.add(new PlantingPlan.Assignment(p, null, false)));
		}
		return new PlantingPlan(List.copyOf(assignments), List.copyOf(warnings));
	}

	/** Shares patches between your chosen crops; returns the patches still open (short of seeds). */
	private static List<Patch> fillChosen(KindPlan plan, List<Patch> open, GameData data, int farmingLevel,
		Map<Integer, Integer> left, BufferSettings buffers, List<PlantingPlan.Assignment> assignments,
		List<String> warnings)
	{
		List<Seed> seeds = new ArrayList<>();
		List<ShareSplitter.Share> shares = new ArrayList<>();
		for (CropShare crop : plan.getCrops())
		{
			Seed seed = data.seedByItem(crop.getSeedItemId());
			if (seed == null)
			{
				continue;
			}
			if (farmingLevel >= 0 && seed.getLevel() > farmingLevel)
			{
				warnings.add(seed.getName() + " needs Farming " + seed.getLevel());
				continue;
			}
			seeds.add(seed);
			shares.add(new ShareSplitter.Share(crop.getWeight(), crop.getPatches(), capacity(seed, left, buffers)));
		}
		int[] split = ShareSplitter.split(open.size(), shares);
		int next = 0;
		for (int i = 0; i < seeds.size(); i++)
		{
			Seed seed = seeds.get(i);
			for (int n = 0; n < split[i]; n++)
			{
				take(left, seed, seed.getPerPatch());
				assignments.add(new PlantingPlan.Assignment(open.get(next++), seed, false));
			}
			Integer wanted = shares.get(i).getExact();
			if (wanted != null && split[i] < wanted)
			{
				warnings.add("Only enough " + seed.getName() + " to spare for " + split[i] + " of " + wanted + " patches");
			}
		}
		List<Patch> rest = new ArrayList<>(open.subList(next, open.size()));
		if (!rest.isEmpty())
		{
			warnings.add("Not enough of your chosen seeds for " + rest.size() + " patch"
				+ (rest.size() == 1 ? "" : "es") + ": filled with the best you can spare");
		}
		return rest;
	}

	/** Fills open patches with the best seeds you can spare, highest level first; filled ones leave {@code open}. */
	private static void fillAuto(List<Patch> open, List<Seed> candidates, Map<Integer, Integer> left,
		BufferSettings buffers, List<PlantingPlan.Assignment> assignments)
	{
		int next = 0;
		for (Seed seed : candidates)
		{
			int capacity = capacity(seed, left, buffers);
			for (int n = 0; n < capacity && next < open.size(); n++)
			{
				take(left, seed, seed.getPerPatch());
				assignments.add(new PlantingPlan.Assignment(open.get(next++), seed, false));
			}
		}
		open.subList(0, next).clear();
	}

	/** Patches this seed can still fill from what's spare (held beyond the buffer, less what's already used). */
	private static int capacity(Seed seed, Map<Integer, Integer> left, BufferSettings buffers)
	{
		int spare = buffers.spare(seed, left.getOrDefault(seed.plantItemId(), 0));
		return spare / seed.getPerPatch();
	}

	private static boolean take(Map<Integer, Integer> left, Seed seed, int amount)
	{
		int have = left.getOrDefault(seed.plantItemId(), 0);
		if (have < amount)
		{
			return false;
		}
		left.put(seed.plantItemId(), have - amount);
		return true;
	}

	private static String kindName(PatchImplementation kind)
	{
		return kind.name().toLowerCase().replace('_', ' ');
	}
}
