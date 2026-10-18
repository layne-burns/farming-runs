/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.Patch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Patches still growing that will be ready by the time the run gets to them: the route is planned with every
 * candidate, each stop no sooner than its patches are ready ({@link RoutePlanner}); the growing patch you'd reach
 * longest before it's ready is left out and the rest planned again, until none would be reached early. Pure: unit
 * tested.
 */
public final class OnTheWay
{
	/**
	 * About 45 seconds at each patch (picking, planting, composting, paying), from timed runs (2026-10-17/18); used
	 * until you have your own times ({@link LearnedCosts}).
	 */
	public static final int WORK_TICKS_PER_PATCH = 75;
	/** Game ticks are 0.6 seconds. */
	private static final double SECONDS_PER_TICK = 0.6;

	/** How long you'd spend at a stop with this many patches, in ticks ({@link LearnedCosts#work}). */
	@FunctionalInterface
	public interface Work
	{
		int ticks(String stop, int patches);
	}

	private OnTheWay()
	{
	}

	/**
	 * @param readyInTicks how long until each patch is ready, in ticks (0 or less when it is)
	 * @param work how long you'd spend at each stop
	 * @param start where you'll start from, if a known location
	 * @return the patches to visit: the ready ones, and growing ones ready by the time you'd get there
	 */
	public static List<Patch> patches(List<Patch> candidates, ToIntFunction<Patch> readyInTicks, RoutePlanner.Cost cost,
		Work work, @Nullable String start)
	{
		List<Patch> kept = new ArrayList<>(candidates);
		// Quick first cut: a patch not ready until after you'd have visited every stop (without waiting) can't be.
		Map<String, List<Patch>> all = byStop(kept);
		RoutePlanner.Timing noWait = timing(all, p -> 0, work);
		long done = RoutePlanner.finish(start, RoutePlanner.order(start, all.keySet(), cost, noWait), cost, noWait);
		kept.removeIf(p -> readyInTicks.applyAsInt(p) > done);
		while (true)
		{
			Map<String, List<Patch>> byStop = byStop(kept);
			RoutePlanner.Timing timing = timing(byStop, readyInTicks, work);
			List<String> route = RoutePlanner.order(start, byStop.keySet(), cost, timing);
			List<Long> arrivals = RoutePlanner.arrivals(start, route, cost, timing);
			// The patch you'd reach longest before it's ready goes; one far off (hours) would otherwise make every
			// order look as slow, and the rest are planned again without it.
			Patch worst = null;
			long worstEarly = 0;
			for (int i = 0; i < route.size(); i++)
			{
				for (Patch patch : byStop.get(route.get(i)))
				{
					long early = readyInTicks.applyAsInt(patch) - arrivals.get(i);
					if (early > worstEarly)
					{
						worst = patch;
						worstEarly = early;
					}
				}
			}
			if (worst == null)
			{
				return kept;
			}
			kept.remove(worst);
		}
	}

	/** Each stop is ready when its last patch is; the time there is what {@code work} says for its patches. */
	public static RoutePlanner.Timing timing(Map<String, List<Patch>> byStop, ToIntFunction<Patch> readyInTicks,
		Work work)
	{
		Map<String, Integer> ready = new LinkedHashMap<>();
		byStop.forEach((stop, patches) -> ready.put(stop,
			patches.stream().mapToInt(readyInTicks).max().orElse(0)));
		return new RoutePlanner.Timing()
		{
			@Override
			public int readyAt(String stop)
			{
				return Math.max(0, ready.getOrDefault(stop, 0));
			}

			@Override
			public int work(String stop)
			{
				return work.ticks(stop, byStop.getOrDefault(stop, List.of()).size());
			}
		};
	}

	/** Seconds until ready (from Time Tracking) as ticks. */
	public static int ticks(long seconds)
	{
		return (int) Math.min(Integer.MAX_VALUE, Math.ceil(seconds / SECONDS_PER_TICK));
	}

	private static Map<String, List<Patch>> byStop(List<Patch> patches)
	{
		return patches.stream().collect(Collectors.groupingBy(Patch::getLocationId, LinkedHashMap::new,
			Collectors.toList()));
	}
}
