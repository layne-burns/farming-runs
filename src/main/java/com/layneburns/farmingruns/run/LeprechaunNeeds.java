/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.ItemCatalog;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What to take from the tool leprechaun at a stop: only what this stop still needs and you aren't carrying (compost
 * for its patches not yet treated, your bottomless bucket, a spade), and only what the leprechaun holds. Every patch
 * has a leprechaun, so compost is taken as you go rather than all at the start. Pure: unit tested.
 */
public final class LeprechaunNeeds
{
	private LeprechaunNeeds()
	{
	}

	/**
	 * @param composted patch ids treated with compost this run
	 * @return e.g. ["1 × Supercompost", "a spade"]; empty when there's nothing to take
	 */
	public static List<String> atStop(RunProgress.Stop stop, RunProgress progress, CompostChoice compost,
		Set<String> composted, ItemCatalog items, ItemLedger ledger)
	{
		List<String> take = new ArrayList<>();
		long toCompost = stop.getPatches().stream()
			.filter(a -> a.getSeed() != null && !progress.getWorkedPatches().contains(a.getPatch().getId())
				&& !composted.contains(a.getPatch().getId()))
			.count();
		Set<Integer> compostItems = RunGuidance.compostItems(compost, items);
		if (toCompost > 0 && !compostItems.isEmpty())
		{
			int carried = carried(ledger, compostItems);
			int stored = count(ledger, compostItems, ItemSource.LEPRECHAUN);
			if (compost == CompostChoice.BOTTOMLESS_BUCKET)
			{
				if (carried == 0 && stored > 0)
				{
					take.add("your bottomless bucket");
				}
			}
			else if (carried < toCompost && stored > 0)
			{
				take.add(Math.min(toCompost - carried, stored) + " × " + compost.getTitle());
			}
		}
		boolean work = stop.getPatches().stream().anyMatch(a -> !progress.isPatchDone(a.getPatch().getId()));
		Set<Integer> spade = items.group("spade");
		if (work && carried(ledger, spade) == 0 && count(ledger, spade, ItemSource.LEPRECHAUN) > 0)
		{
			take.add("a spade");
		}
		return take;
	}

	/** In your inventory or worn. */
	private static int carried(ItemLedger ledger, Set<Integer> ids)
	{
		return count(ledger, ids, ItemSource.INVENTORY) + count(ledger, ids, ItemSource.EQUIPMENT);
	}

	private static int count(ItemLedger ledger, Set<Integer> ids, ItemSource source)
	{
		return ids.stream().mapToInt(id -> ledger.count(id, source)).sum();
	}
}
