/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * Your farming runs over time (saved per account): how many, the patches planted, the XP and time, the fastest full
 * run, and the most herbs you've picked from one patch. Immutable. Pure: unit tested.
 */
@Value
public class RunStats
{
	public static final RunStats NONE = new RunStats(0, 0, 0, 0, 0, 0, "");

	/** Runs finished (every stop done or skipped). */
	int runs;
	long patchesPlanted;
	long xp;
	long millis;
	/** The quickest finished run, in milliseconds; 0 before the first. */
	long fastestMillis;
	/** The most herbs picked from one patch, and which herb; 0 and "" before the first. */
	int mostHerbs;
	String mostHerbsName;

	/** These stats with a run added; the run's records are filled in (most herbs, fastest run). */
	public Added add(RunSummary run)
	{
		List<String> records = new ArrayList<>();
		int herbs = mostHerbs;
		String herbName = mostHerbsName;
		if (run.getMostHerbs() > herbs)
		{
			if (herbs > 0)
			{
				records.add("Most herbs from one patch: " + run.getMostHerbs() + " " + run.getMostHerbsName() + " (was "
					+ herbs + ")");
			}
			herbs = run.getMostHerbs();
			herbName = run.getMostHerbsName();
		}
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
			xp + run.getXpGained(), millis + run.getMillis(), fastest, herbs, herbName);
		RunSummary withRecords = new RunSummary(run.isCompleted(), run.getMillis(), run.getXpGained(),
			run.getStopsDone(), run.getStops(), run.getPatchesPlanted(), run.getHarvested(), run.getMostHerbs(),
			run.getMostHerbsName(), List.copyOf(records));
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
