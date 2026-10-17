/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.run.RunStats;
import com.layneburns.farmingruns.run.RunSummary;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JLabel;

/** The Live tab's end-of-run summary and your lifetime stats. */
final class RunSummaryView
{
	/** How many produce lines to show before "and more". */
	private static final int TOP = 6;

	private RunSummaryView()
	{
	}

	static void summary(SectionPanel section, RunSummary run)
	{
		section.addRow(TabPanel.text(run.isCompleted()
			? "Finished in " + RunStats.minutes(run.getMillis()) + "."
			: "Stopped after " + RunStats.minutes(run.getMillis()) + " (" + run.getStopsDone() + " of " + run.getStops()
			+ " stops)."));
		section.addRow(TabPanel.text(String.format("Farming XP: %,d · Patches planted: %d", run.getXpGained(),
			run.getPatchesPlanted())));
		if (!run.getHarvested().isEmpty())
		{
			section.addRow(TabPanel.text("Gathered: " + top(run.getHarvested())));
		}
		for (String record : run.getRecords())
		{
			JLabel label = TabPanel.text("★ New best: " + record);
			label.setForeground(Palette.ACCENT);
			section.addRow(label);
		}
	}

	static void stats(SectionPanel section, RunStats stats)
	{
		if (stats.getRuns() == 0 && stats.getPatchesPlanted() == 0)
		{
			section.addRow(TabPanel.text("Your stats appear here after your first run."));
			return;
		}
		section.addRow(TabPanel.text(String.format("Runs finished: %d · Patches planted: %,d", stats.getRuns(),
			stats.getPatchesPlanted())));
		section.addRow(TabPanel.text(String.format("Farming XP from runs: %,d", stats.getXp())));
		section.addRow(TabPanel.text("Time on runs: " + hours(stats.getMillis())
			+ (stats.getFastestMillis() > 0 ? " · Fastest: " + RunStats.minutes(stats.getFastestMillis()) : "")));
		if (!stats.getBestAtOneStop().isEmpty())
		{
			section.addRow(TabPanel.text("Most at one stop: " + top(stats.getBestAtOneStop())));
		}
	}

	/** "14 Grimy ranarr weed, 9 Potato, ..." largest first, the first few. */
	private static String top(Map<String, Integer> counts)
	{
		String shown = counts.entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
			.limit(TOP)
			.map(e -> e.getValue() + " " + e.getKey())
			.collect(Collectors.joining(", "));
		return counts.size() > TOP ? shown + " and more" : shown;
	}

	/** "3 h 10 min" or "42 min". */
	private static String hours(long millis)
	{
		long minutes = millis / 60_000;
		return minutes >= 60 ? minutes / 60 + " h " + minutes % 60 + " min" : minutes + " min";
	}
}
