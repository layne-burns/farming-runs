/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.With;

/**
 * The Bank tab's filters for plantable seeds. Each one that's on must pass (they combine), and each adds its
 * status to the row's detail line, coloured green (full run) or yellow (part of one). Rows that aren't plantable
 * seeds (supplies, leprechaun) are never filtered; unpotted tree seeds are hidden while any filter is on.
 * Pure: unit tested.
 */
@Value
@With
public class BankFilters
{
	public static final BankFilters NONE = new BankFilters(false, false, false, -1);

	/** Only crops a gardener protects, for which you hold payment. */
	boolean hasProtection;
	/** Only seeds whose spare covers at least one patch. */
	boolean enoughForRun;
	/** Only seeds at or below your Farming level. */
	boolean canPlant;
	/** Your real Farming level, or -1 if unknown (then "can plant" lets everything through). */
	int farmingLevel;

	public int activeCount()
	{
		return (hasProtection ? 1 : 0) + (enoughForRun ? 1 : 0) + (canPlant ? 1 : 0);
	}

	/** The sections with the filters applied; groups and sections left empty are dropped. */
	public List<BankView.Section> apply(List<BankView.Section> sections)
	{
		if (activeCount() == 0)
		{
			return sections;
		}
		List<BankView.Section> result = new ArrayList<>();
		for (BankView.Section section : sections)
		{
			if (section.getPage() != BankView.Page.SEEDS)
			{
				result.add(section);
				continue;
			}
			List<BankView.Group> groups = new ArrayList<>();
			for (BankView.Group group : section.getGroups())
			{
				List<BankView.Row> rows = group.getRows().stream()
					.filter(this::keep)
					.map(this::describe)
					.collect(Collectors.toList());
				if (!rows.isEmpty())
				{
					groups.add(new BankView.Group(group.getTitle(), rows));
				}
			}
			if (!groups.isEmpty())
			{
				result.add(new BankView.Section(section.getPage(), section.getTitle(), groups, null));
			}
		}
		return result;
	}

	private boolean keep(BankView.Row row)
	{
		BankView.Plantable p = row.getPlantable();
		if (p == null)
		{
			return false;
		}
		if (canPlant && farmingLevel >= 0 && p.getLevel() > farmingLevel)
		{
			return false;
		}
		if (enoughForRun && p.seeds() == BankView.Coverage.NONE)
		{
			return false;
		}
		return !hasProtection || (p.isProtectable() && p.protection() != BankView.Coverage.NONE);
	}

	/** Adds each active filter's status to the detail line, coloured by the weakest of them. */
	private BankView.Row describe(BankView.Row row)
	{
		BankView.Plantable p = row.getPlantable();
		List<String> parts = new ArrayList<>();
		parts.add(row.getDetail());
		BankView.Coverage colour = null;
		StringBuilder tooltip = new StringBuilder(row.getTooltip());
		if (enoughForRun)
		{
			parts.add(p.seeds() == BankView.Coverage.FULL ? "full run" : "fills " + p.getSeededPatches() + "/" + p.getPatches());
			colour = weaker(colour, p.seeds());
			tooltip.append("\nRun: ").append(p.getPatches()).append(" patches, spare fills ")
				.append(Math.min(p.getSeededPatches(), p.getPatches()));
		}
		if (hasProtection)
		{
			parts.add(p.protection() == BankView.Coverage.FULL ? "paid" : "paid " + p.getPaidPatches() + "/" + p.protectionNeed());
			colour = weaker(colour, p.protection());
			tooltip.append("\nPayment: ").append(p.getPaymentPerPatch()).append(" per patch, enough for ")
				.append(p.getPaidPatches()).append(" of ").append(p.protectionNeed());
		}
		return new BankView.Row(row.getIconItemId(), row.getLabel(), row.getValue(), String.join(" · ", parts),
			colour, tooltip.toString(), p);
	}

	private static BankView.Coverage weaker(BankView.Coverage a, BankView.Coverage b)
	{
		return a == null || b.ordinal() > a.ordinal() ? b : a;
	}
}
