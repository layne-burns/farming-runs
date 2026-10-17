/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * What the Bank tab shows, as plain data: built by {@link BankViewBuilder}, drawn by {@link BankTab}. Keeping the
 * two apart means the choices (what's listed, what counts as spare, what a search finds) are tested without Swing.
 *
 * Layout: pages (Seeds / Supplies / Leprechaun) hold sections (e.g. "Trees"), which hold groups (one per patch
 * kind, e.g. "Fruit tree"), which hold rows (one per item).
 */
@Value
public class BankView
{
	/** When the remembered places were last seen, one short entry each: "Bank: now", "Vault: 2h", "Box: never". */
	List<String> seen;
	List<Section> sections;

	@Getter
	@RequiredArgsConstructor
	public enum Page
	{
		SEEDS("Seeds"),
		SUPPLIES("Supplies"),
		LEPRECHAUN("Leprechaun");

		private final String title;
	}

	@Value
	public static class Section
	{
		Page page;
		String title;
		List<Group> groups;
		/** Shown when there are no rows; null to hide the section instead. */
		@Nullable
		String emptyText;

		public boolean isEmpty()
		{
			return groups.stream().allMatch(g -> g.getRows().isEmpty());
		}
	}

	@Value
	public static class Group
	{
		/** The patch kind, e.g. "Fruit tree"; null for a section with a single list. */
		@Nullable
		String title;
		List<Row> rows;
	}

	@Value
	public static class Row
	{
		/** Item whose icon is shown. */
		int iconItemId;
		String label;
		/** The main count, kept short so it always fits. */
		String value;
		/** A smaller second line, e.g. "11 spare"; null for none. */
		@Nullable
		String detail;
		/** How the detail line is coloured; null for the normal grey. */
		@Nullable
		Coverage detailColour;
		/** Where the items are, e.g. "Bank 12, Seed vault 2". */
		String tooltip;
		/** For seeds and saplings you can plant: what the filters look at. Null for everything else. */
		@Nullable
		Plantable plantable;

		public Row(int iconItemId, String label, String value, @Nullable String detail, String tooltip)
		{
			this(iconItemId, label, value, detail, null, tooltip, null);
		}

		public Row(int iconItemId, String label, String value, @Nullable String detail, @Nullable Coverage detailColour,
			String tooltip, @Nullable Plantable plantable)
		{
			this.iconItemId = iconItemId;
			this.label = label;
			this.value = value;
			this.detail = detail;
			this.detailColour = detailColour;
			this.tooltip = tooltip;
			this.plantable = plantable;
		}
	}

	/** How much of a full run something covers. */
	public enum Coverage
	{
		/** Enough for every patch in a full run. */
		FULL,
		/** Enough for some patches, not all. */
		PARTIAL,
		/** Enough for none. */
		NONE
	}

	/**
	 * The facts about a plantable seed (or sapling) that the filters use. A full run is every patch of the kind,
	 * or as many patches as the spare seeds fill, whichever is fewer.
	 */
	@Value
	public static class Plantable
	{
		/** Farming level needed to plant it. */
		int level;
		/** Patches of this kind. */
		int patches;
		/** Patches the spare seeds (after the buffer) can fill. */
		int seededPatches;
		/** False for crops a gardener can't protect (herbs, flowers...). */
		boolean protectable;
		/** Patches the payment you hold can protect. */
		int paidPatches;
		/** The payment for one patch, e.g. "9 x Sweetcorn"; empty if not protectable. */
		String paymentPerPatch;

		/** Patches in a full run: every patch, or what the spare seeds fill, whichever is fewer. */
		public int runPatches()
		{
			return Math.min(patches, seededPatches);
		}

		public Coverage seeds()
		{
			return coverage(seededPatches, patches);
		}

		/** Payment held, against a full run; with no spare seeds, against every patch of the kind. */
		public Coverage protection()
		{
			return coverage(paidPatches, protectionNeed());
		}

		/** Patches the payment is measured against (see {@link #protection()}). */
		public int protectionNeed()
		{
			return runPatches() > 0 ? runPatches() : patches;
		}

		private static Coverage coverage(int have, int need)
		{
			if (have <= 0 || need <= 0)
			{
				return Coverage.NONE;
			}
			return have >= need ? Coverage.FULL : Coverage.PARTIAL;
		}
	}

	/** The sections of one page, without empty ones that have nothing to say. */
	public List<Section> page(Page page)
	{
		return sections.stream()
			.filter(s -> s.getPage() == page && (!s.isEmpty() || s.getEmptyText() != null))
			.collect(Collectors.toList());
	}

	/**
	 * Every row, on any page, matching the search: each word must appear in the row's name or in its group's or
	 * section's title, so "fruit" lists all fruit tree seeds and "ranarr" just the one. Empty groups and sections
	 * are dropped.
	 */
	public List<Section> search(String query)
	{
		List<String> words = Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+"))
			.filter(w -> !w.isEmpty())
			.collect(Collectors.toList());
		List<Section> results = new ArrayList<>();
		for (Section section : sections)
		{
			List<Group> groups = new ArrayList<>();
			for (Group group : section.getGroups())
			{
				String context = (section.getTitle() + " " + (group.getTitle() == null ? "" : group.getTitle()))
					.toLowerCase(Locale.ROOT);
				List<Row> rows = group.getRows().stream()
					.filter(r -> matchesAll(words, context + " " + r.getLabel().toLowerCase(Locale.ROOT)))
					.collect(Collectors.toList());
				if (!rows.isEmpty())
				{
					groups.add(new Group(group.getTitle(), rows));
				}
			}
			if (!groups.isEmpty())
			{
				results.add(new Section(section.getPage(), section.getTitle(), groups, null));
			}
		}
		return results;
	}

	private static boolean matchesAll(List<String> words, String text)
	{
		return words.stream().allMatch(text::contains);
	}
}
