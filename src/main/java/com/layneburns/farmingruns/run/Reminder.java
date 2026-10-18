/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.ItemAmount;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.data.TeleportChoice;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * The overhead reminder at a stop (SPEC §2.6): the one next thing to do. First anything to take from the tool
 * leprechaun, then for the first patch not done: its tree step, compost, planting, or the gardener's payment. Pure:
 * unit tested.
 */
public final class Reminder
{
	private Reminder()
	{
	}

	/**
	 * @param composted      patch ids treated with compost this run
	 * @param pays           whether a patch is to be paid for
	 * @param fromLeprechaun what to take from the tool leprechaun here ({@link LeprechaunNeeds}), e.g. "1 × Supercompost"
	 * @return the reminder, or null when there's nothing left to do here
	 */
	@Nullable
	public static String next(RunProgress.Stop stop, RunProgress progress, CompostPlan plan, Set<String> composted,
		Predicate<PlantingPlan.Assignment> pays, List<String> fromLeprechaun)
	{
		if (!fromLeprechaun.isEmpty())
		{
			return "Take from the leprechaun: " + String.join(", ", fromLeprechaun);
		}
		for (PlantingPlan.Assignment a : stop.getPatches())
		{
			String id = a.getPatch().getId();
			if (progress.isPatchDone(id))
			{
				continue;
			}
			String kind = a.getPatch().getKind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
			boolean worked = progress.getWorkedPatches().contains(id);
			CompostChoice compost = plan.forKind(a.getPatch().getKind());
			if (a.getPatch().isCompostBin())
			{
				return progress.hasHarvestFirst(id) && !progress.isHarvested(id)
					? "Collect the compost from the " + kind + " bin"
					: "Fill the " + kind + " bin with pineapples, then close it";
			}
			if (a.isKeptStanding())
			{
				return "Pick the " + kind + ", leave it standing";
			}
			Seed seed = a.getSeed();
			if (seed == null)
			{
				return "Harvest and clear the " + kind + " patch";
			}
			if (!worked && progress.hasHarvestFirst(id) && !progress.isHarvested(id))
			{
				return "Harvest the " + kind + " patch";
			}
			if (!worked)
			{
				boolean needsCompost = compost != CompostChoice.NONE && !composted.contains(id);
				String plant = "plant " + (seed.isPlantedAsSapling() ? seed.getSaplingName() : seed.getName());
				if (seed.isPlantedAsSapling())
				{
					return "Check health, clear the " + kind + ", then " + plant;
				}
				if (needsCompost)
				{
					return compost == CompostChoice.FERTILE_SOIL ? "Cast Fertile Soil on the " + kind + " patch"
						: "Compost the " + kind + " patch, then " + plant;
				}
				return Character.toUpperCase(plant.charAt(0)) + plant.substring(1);
			}
			if (progress.isAwaitingCompost(id) && !composted.contains(id))
			{
				// Planted first: the stop still waits for the compost.
				return "Compost the " + kind + " patch";
			}
			if (pays.test(a) && progress.isAwaitingPayment(id))
			{
				return "Pay the gardener: " + amounts(seed.getPayment());
			}
		}
		return null;
	}

	/**
	 * Away from the current stop: where you're going and how, e.g. "Next: Weiss · House portal → Weiss" or
	 * "Next: Lumbridge hops · walk".
	 */
	public static String travel(String locationName, @Nullable TeleportChoice teleport)
	{
		return travel(locationName, TravelChain.steps(teleport, null));
	}

	/** The steps still to take ({@link TravelChain}): "Next: Great Conch · Salve Graveyard portal → Fairy ring CKQ". */
	public static String travel(String locationName, List<String> steps)
	{
		return "Next: " + locationName + " · " + String.join(" → ", steps);
	}

	/** "8 × Mushroom" for each item, for the payment wording. */
	static String amounts(List<ItemAmount> items)
	{
		return items.stream().map(p -> p.getAmount() + " × " + p.getName()).collect(Collectors.joining(" + "));
	}
}
