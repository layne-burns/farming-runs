/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.ItemAmount;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The tooltip on a kind card's "Pay the gardener" box: for each crop being planted, what the gardener wants per
 * patch and in all, and how many of it you have and where.
 */
final class PaymentTooltip
{
	private PaymentTooltip()
	{
	}

	/** @param diseaseFree whether a patch can't catch disease for you (no payment needed) */
	static String build(List<PlantingPlan.Assignment> planned, ItemLedger ledger, Predicate<Patch> diseaseFree)
	{
		Map<Seed, Integer> patches = new LinkedHashMap<>();
		int noGardener = 0;
		int free = 0;
		for (PlantingPlan.Assignment a : planned)
		{
			if (a.getSeed() == null || !a.getSeed().canBeProtected())
			{
				continue;
			}
			if (diseaseFree.test(a.getPatch()))
			{
				free++;
			}
			else if (a.getPatch().hasGardener())
			{
				patches.merge(a.getSeed(), 1, Integer::sum);
			}
			else
			{
				noGardener++;
			}
		}
		StringBuilder html = new StringBuilder("<html><b>Protection payment</b>");
		if (patches.isEmpty())
		{
			html.append("<br>Nothing being planted here can be protected.");
		}
		patches.forEach((seed, count) ->
		{
			html.append("<br>").append(seed.getName()).append(" (").append(count).append(count == 1 ? " patch)" : " patches)");
			for (ItemAmount pay : seed.getPayment())
			{
				int needed = pay.getAmount() * count;
				int have = ledger.count(pay.getItemId());
				html.append("<br>&nbsp;&nbsp;").append(pay.getAmount()).append(" × ").append(pay.getName())
					.append(" each = ").append(needed).append(": ")
					.append(have >= needed ? "<font color='" + Palette.hex(Palette.FULL) + "'>"
						: "<font color='" + Palette.hex(Palette.NONE) + "'>")
					.append("you have ").append(have).append("</font>").append(where(ledger, pay.getItemId()));
			}
		});
		if (free > 0)
		{
			html.append("<br>").append(free).append(free == 1 ? " patch is" : " patches are")
				.append(" disease-free for you, so no payment is needed.");
		}
		if (noGardener > 0)
		{
			html.append("<br>").append(noGardener).append(noGardener == 1 ? " patch has" : " patches have")
				.append(" no gardener, so can't be protected.");
		}
		return html.append("<br>Noted produce is accepted.</html>").toString();
	}

	/** " (Bank 200, Inventory 30)", or "" when you have none. */
	private static String where(ItemLedger ledger, int itemId)
	{
		List<String> places = new ArrayList<>();
		for (ItemSource source : ItemSource.values())
		{
			int count = ledger.count(itemId, source);
			if (count > 0)
			{
				places.add(source.getDisplayName() + " " + count);
			}
		}
		return places.isEmpty() ? "" : " (" + String.join(", ", places) + ")";
	}
}
