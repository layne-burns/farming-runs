/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.Seed;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * The wording of the Bank tab's tooltips: one fact per line, joined with "\n" (the row draws each on its own line,
 * under the item's name in bold). Pure, so the text is tested and easy to change in one place.
 */
final class BankTooltips
{
	private BankTooltips()
	{
	}

	/** A seed or sapling you can plant: level, growth, XP, seeds per patch, protection, where it is. */
	static String plantable(Seed seed, String where)
	{
		List<String> lines = new ArrayList<>(about(seed));
		lines.add(protection(seed));
		lines.add("Where: " + where);
		return String.join("\n", lines);
	}

	/**
	 * A seed in the Run tab's seed choices: level, growth, XP (and roughly per patch), seeds per patch, protection,
	 * and the goals that use its crop.
	 */
	static String seedChoice(Seed seed, List<String> goals)
	{
		List<String> lines = new ArrayList<>(about(seed));
		if (seed.getXp().getTotal() > 0)
		{
			lines.add("About " + Math.round(seed.getXp().getTotal()) + " XP per patch in all");
		}
		lines.add(protection(seed));
		lines.add(goals.isEmpty() ? "Not part of any goal" : "Used for: " + String.join(", ", goals));
		return String.join("\n", lines);
	}

	/** One fact per line as a Swing tooltip, under the name in bold. */
	static String html(String name, String lines)
	{
		StringBuilder html = new StringBuilder("<html><b>").append(escape(name)).append("</b>");
		for (String line : lines.split("\n"))
		{
			html.append("<br>").append(escape(line));
		}
		return html.append("</html>").toString();
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** A tree seed not yet potted. */
	static String unpottedTreeSeed(Seed seed, String where)
	{
		List<String> lines = new ArrayList<>();
		lines.add("Pot it with a gardening trowel to grow a " + seed.getSaplingName().toLowerCase() + ".");
		lines.addAll(about(seed));
		lines.add("Where: " + where);
		return String.join("\n", lines);
	}

	/** A supply (compost, tool...): what it's for, then where it is. */
	static String supply(@Nullable String description, String where)
	{
		return description == null ? "Where: " + where : description + "\nWhere: " + where;
	}

	/** A payment item: each crop it protects and how many per patch, then where it is. */
	static String payment(List<String> protects, String where)
	{
		return "Protects (per patch):\n" + protects.stream().map(p -> "  " + p).collect(Collectors.joining("\n"))
			+ "\nWhere: " + where;
	}

	/** A teleport item: the locations it's used for, then where it is. */
	static String teleport(List<String> usedFor, String where)
	{
		String uses = usedFor.isEmpty() ? "Not used by any location" : "Used for " + String.join(", ", usedFor);
		return uses + "\nWhere: " + where;
	}

	/** Something stored with the tool leprechaun. */
	static String leprechaun(@Nullable String description)
	{
		return description == null ? "Stored with the tool leprechaun"
			: description + "\nStored with the tool leprechaun";
	}

	/** "Level 32 · grows in 1 h 20 min", "XP: 27 plant, 30.5 per harvest", "3 seeds per patch". */
	private static List<String> about(Seed seed)
	{
		List<String> lines = new ArrayList<>();
		lines.add("Level " + seed.getLevel() + (seed.getGrowth() == null ? "" : " · grows in " + seed.getGrowth()));
		String xp = seed.getXp().describe();
		if (!xp.isEmpty())
		{
			lines.add("XP: " + xp);
		}
		if (seed.getPerPatch() > 1)
		{
			lines.add(seed.getPerPatch() + " seeds per patch");
		}
		return lines;
	}

	private static String protection(Seed seed)
	{
		if (!seed.canBeProtected())
		{
			return "Can't be protected by a gardener";
		}
		return "Protection: " + seed.getPayment().stream()
			.map(p -> p.getAmount() + " × " + p.getName())
			.collect(Collectors.joining(" + ")) + " per patch";
	}
}
