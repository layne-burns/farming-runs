/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Value;

/**
 * Your farming runs over time (saved per account): how many, the patches planted, the XP and time, the fastest full
 * run, and your best haul of each crop from one stop ("most herbs grabbed at once"). Immutable. Pure: unit tested.
 */
@Value
public class RunStats
{
	public static final RunStats NONE = new RunStats(0, 0, 0, 0, 0, Map.of());

	/** Runs finished (every stop done or skipped). */
	int runs;
	long patchesPlanted;
	long xp;
	long millis;
	/** The quickest finished run, in milliseconds; 0 before the first. */
	long fastestMillis;
	/** The most of each produce gathered at a single stop, by item name. */
	Map<String, Integer> bestAtOneStop;

	/** These stats with a run added; the run's records are filled in (new bests at one stop, a new fastest run). */
	public Added add(RunSummary run)
	{
		List<String> records = new ArrayList<>();
		Map<String, Integer> best = new TreeMap<>(bestAtOneStop == null ? Map.of() : bestAtOneStop);
		run.getBestAtOneStop().forEach((item, count) ->
		{
			Integer was = best.get(item);
			if (was == null || count > was)
			{
				best.put(item, count);
				if (was != null)
				{
					records.add(item + ": " + count + " at one stop (was " + was + ")");
				}
			}
		});
		long fastest = fastestMillis;
		if (run.isCompleted() && (fastest == 0 || run.getMillis() < fastest))
		{
			if (fastest != 0)
			{
				records.add("Fastest run: " + minutes(run.getMillis()) + " (was " + minutes(fastest) + ")");
			}
			fastest = run.getMillis();
		}
		RunStats next = new RunStats(runs + (run.isCompleted() ? 1 : 0), patchesPlanted + run.getPatchesPlanted(),
			xp + run.getXpGained(), millis + run.getMillis(), fastest, Map.copyOf(best));
		RunSummary withRecords = new RunSummary(run.isCompleted(), run.getMillis(), run.getXpGained(),
			run.getStopsDone(), run.getStops(), run.getPatchesPlanted(), run.getHarvested(), run.getBestAtOneStop(),
			List.copyOf(records));
		return new Added(next, withRecords);
	}

	/** "12:34" for minutes and seconds. */
	public static String minutes(long millis)
	{
		long seconds = millis / 1000;
		return seconds / 60 + ":" + String.format("%02d", seconds % 60);
	}

	/** The stats after a run, and that run with its records. */
	@Value
	public static class Added
	{
		RunStats stats;
		RunSummary run;
	}
}
