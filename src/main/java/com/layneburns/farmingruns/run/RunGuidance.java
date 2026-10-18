/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.ItemAmount;
import com.layneburns.farmingruns.data.ItemCatalog;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * What to point out at the current stop (SPEC §2.6): the seeds still to plant, compost until a patch is treated, and
 * once a patch is planted but not yet paid for, the payment and the gardener. Each drops out as it's done. Pure: unit
 * tested.
 */
public final class RunGuidance
{
	/** Nothing to point out. */
	public static final Highlights NONE = new Highlights(Set.of(), Set.of());

	@Value
	public static class Highlights
	{
		/** Inventory items to outline. */
		Set<Integer> items;
		/** Gardeners to outline, by NPC id. */
		Set<Integer> npcs;
	}

	private RunGuidance()
	{
	}

	/**
	 * @param composted patch ids treated with compost this run
	 */
	public static Highlights at(@Nullable RunProgress.Stop stop, RunProgress progress, CompostPlan compost,
		Set<String> composted, ItemCatalog catalog)
	{
		if (stop == null)
		{
			return NONE;
		}
		Set<Integer> items = new HashSet<>();
		Set<Integer> npcs = new HashSet<>();
		for (PlantingPlan.Assignment a : stop.getPatches())
		{
			String id = a.getPatch().getId();
			if (a.getSeed() == null || progress.isPatchDone(id))
			{
				continue;
			}
			boolean planted = progress.getWorkedPatches().contains(id);
			if (!planted)
			{
				items.add(a.getSeed().plantItemId());
			}
			if (!composted.contains(id))
			{
				items.addAll(compostItems(compost.forKind(a.getPatch().getKind()), catalog));
			}
			if (planted && progress.isAwaitingPayment(id))
			{
				a.getSeed().getPayment().stream().map(ItemAmount::getItemId).forEach(items::add);
				npcs.add(a.getPatch().getGardenerNpcId());
			}
		}
		return new Highlights(Set.copyOf(items), Set.copyOf(npcs));
	}

	/** The items that apply the chosen compost; none for Fertile Soil (a spell) or no compost. */
	static Set<Integer> compostItems(CompostChoice compost, ItemCatalog catalog)
	{
		switch (compost)
		{
			case COMPOST:
				return catalog.group("compost");
			case SUPERCOMPOST:
				return catalog.group("supercompost");
			case ULTRACOMPOST:
				return catalog.group("ultracompost");
			case BOTTOMLESS_BUCKET:
				return catalog.group("bottomless_compost_bucket");
			default:
				return Set.of();
		}
	}
}
