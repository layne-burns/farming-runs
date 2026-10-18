/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Your recent times on runs, in whole seconds, from the timed legs ({@link LegTimer}): how long it took to teleport to
 * each location, to walk between two stops, and to work each patch at a location. Only the last {@link #KEEP} of each
 * are kept, so it follows how you run now. Times that can't be right are left out: legs whose arrival wasn't seen,
 * travel over {@link #MAX_TRAVEL_SECONDS} (a break or a detour), and anything over {@link #OUTLIER_FACTOR} times the
 * usual once there are a few to go by. Saved per account ({@code TravelLog}). Pure: unit tested.
 */
@Value
public class TravelSamples
{
	public static final TravelSamples NONE = new TravelSamples(Map.of(), Map.of(), Map.of());

	/** How many of each to keep: the latest week of runs or so. */
	static final int KEEP = 7;
	/** Travel longer than three minutes was a break or a detour, not the way there. */
	static final int MAX_TRAVEL_SECONDS = 180;
	/** A time this many times the usual one (the median) is left out... */
	static final int OUTLIER_FACTOR = 3;
	/** ...once there are at least this many to go by. */
	static final int OUTLIER_MIN_SAMPLES = 3;

	/** Seconds to get to each location by teleport (from the first teleport to the first click there), by id. */
	Map<String, List<Integer>> travel;
	/** Seconds to walk from one stop to the next, by {@link #pairKey} (either way round). */
	Map<String, List<Integer>> walks;
	/** Seconds of work for each patch at a location (the stop's work over its patches), by location id. */
	Map<String, List<Integer>> work;

	/** These samples with a finished leg's times added, where they're believable. */
	public TravelSamples record(LegTimer.Leg leg)
	{
		if (leg.getTravelMillis() < 0)
		{
			// The arrival wasn't seen, so the work time has the travel in it too: neither is any use.
			return this;
		}
		Map<String, List<Integer>> newTravel = travel;
		Map<String, List<Integer>> newWalks = walks;
		int travelSeconds = seconds(leg.getTravelMillis());
		if (travelSeconds <= MAX_TRAVEL_SECONDS)
		{
			if (leg.isTeleported())
			{
				newTravel = add(travel, leg.getLocationId(), travelSeconds);
			}
			else if (leg.getWalkedFrom() != null)
			{
				newWalks = add(walks, pairKey(leg.getWalkedFrom(), leg.getLocationId()), travelSeconds);
			}
		}
		Map<String, List<Integer>> newWork = work;
		if (leg.getPatches() > 0)
		{
			newWork = add(work, leg.getLocationId(), seconds(leg.getWorkMillis() / leg.getPatches()));
		}
		return new TravelSamples(newTravel, newWalks, newWork);
	}

	/** The key for a walk between two stops, the same either way round. */
	public static String pairKey(String a, String b)
	{
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	/** The middle time (the mean of the middle two for an even count); 0 for none. */
	public static double median(List<Integer> samples)
	{
		if (samples.isEmpty())
		{
			return 0;
		}
		List<Integer> sorted = new ArrayList<>(samples);
		sorted.sort(null);
		int middle = sorted.size() / 2;
		return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
	}

	/**
	 * The samples with this one added under {@code key}, dropping the oldest past {@link #KEEP}; as they were when it's
	 * an outlier.
	 */
	static Map<String, List<Integer>> add(Map<String, List<Integer>> samples, String key, int seconds)
	{
		List<Integer> kept = samples.getOrDefault(key, List.of());
		if (kept.size() >= OUTLIER_MIN_SAMPLES && seconds > OUTLIER_FACTOR * median(kept))
		{
			return samples;
		}
		List<Integer> updated = new ArrayList<>(kept);
		updated.add(seconds);
		Map<String, List<Integer>> copy = new HashMap<>(samples);
		copy.put(key, List.copyOf(updated.subList(Math.max(0, updated.size() - KEEP), updated.size())));
		return Map.copyOf(copy);
	}

	private static int seconds(long millis)
	{
		return (int) Math.round(millis / 1000.0);
	}
}
