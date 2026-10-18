/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Travel and work times for planning a run, from your own times ({@link TravelSamples}) where there are at least
 * {@link #MIN_SAMPLES} to go by, and from the hand table (costs.tsv) and {@link OnTheWay#WORK_TICKS_PER_PATCH}
 * otherwise. A walk you've timed between two stops costs what it took you; any other leg costs your usual time to
 * get there, unless the table knows a cheaper link or walk from where you are. Pure: unit tested.
 */
public final class LearnedCosts implements RoutePlanner.Cost
{
	/** One time could be a fluke; two or more and the middle one is used. */
	static final int MIN_SAMPLES = 2;
	/** Game ticks are 0.6 seconds. */
	private static final double SECONDS_PER_TICK = 0.6;

	private final RoutePlanner.Cost table;
	/** Your usual times, in ticks: to get to each location, and to walk each pair of stops. */
	private final Map<String, Integer> arriveTicks;
	private final Map<String, Integer> walkTicks;
	/** Your usual seconds for each patch at a location. */
	private final Map<String, Double> workSeconds;

	/**
	 * @param table the hand-tuned costs, for anything without enough of your own times
	 * @param samples your recent times
	 */
	public LearnedCosts(RoutePlanner.Cost table, TravelSamples samples)
	{
		this.table = table;
		this.arriveTicks = inTicks(medians(samples.getTravel()));
		this.walkTicks = inTicks(medians(samples.getWalks()));
		this.workSeconds = medians(samples.getWork());
	}

	@Override
	public int cost(@Nullable String from, String to)
	{
		if (from != null)
		{
			Integer walk = walkTicks.get(TravelSamples.pairKey(from, to));
			if (walk != null)
			{
				return walk;
			}
		}
		int tableArrive = table.cost(null, to);
		int arrive = arriveTicks.getOrDefault(to, tableArrive);
		if (from == null)
		{
			return arrive;
		}
		// The table's leg is cheaper than its arrive cost only through a link or a short walk: keep that when it wins.
		int tableLeg = table.cost(from, to);
		return tableLeg < tableArrive ? Math.min(tableLeg, arrive) : arrive;
	}

	/** Ticks of work at a stop with this many patches: your usual time a patch there, or the planner's guess. */
	public int work(String stop, int patches)
	{
		Double seconds = workSeconds.get(stop);
		return seconds == null ? OnTheWay.WORK_TICKS_PER_PATCH * patches : ticks(seconds * patches);
	}

	static int ticks(double seconds)
	{
		return (int) Math.round(seconds / SECONDS_PER_TICK);
	}

	/** The median of each list with at least {@link #MIN_SAMPLES} times. */
	private static Map<String, Double> medians(Map<String, List<Integer>> samples)
	{
		Map<String, Double> medians = new HashMap<>();
		samples.forEach((key, times) ->
		{
			if (times.size() >= MIN_SAMPLES)
			{
				medians.put(key, TravelSamples.median(times));
			}
		});
		return medians;
	}

	private static Map<String, Integer> inTicks(Map<String, Double> seconds)
	{
		Map<String, Integer> ticks = new HashMap<>();
		seconds.forEach((key, value) -> ticks.put(key, ticks(value)));
		return ticks;
	}
}
