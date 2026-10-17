/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Orders a run's stops to keep the total travel cost low (SPEC §2.5): from where you start, visiting every stop once,
 * ending wherever is cheapest. Exact (Held–Karp) for up to {@link #EXACT_LIMIT} stops; above that, nearest-next
 * then 2-opt, which is close to best and fast. Pure: unit tested.
 */
public final class RoutePlanner
{
	/** Held–Karp keeps 2^n × n numbers: 16 stops is about a million, a few megabytes. */
	static final int EXACT_LIMIT = 16;

	/** The travel cost of one leg; {@code from} is null for the first leg (from wherever you are). */
	@FunctionalInterface
	public interface Cost
	{
		int cost(@Nullable String from, String to);
	}

	private RoutePlanner()
	{
	}

	/**
	 * @param start where you are, if it's a known location (it goes first when it's a stop); null otherwise
	 * @return the stops in visiting order
	 */
	public static List<String> order(@Nullable String start, Collection<String> stops, Cost cost)
	{
		List<String> rest = new ArrayList<>(new LinkedHashSet<>(stops));
		List<String> route = new ArrayList<>();
		String from = start;
		if (start != null && rest.remove(start))
		{
			route.add(start);
		}
		if (rest.isEmpty())
		{
			return route;
		}
		route.addAll(rest.size() <= EXACT_LIMIT ? exact(from, rest, cost) : nearestThenTwoOpt(from, rest, cost));
		return route;
	}

	/** Total cost of visiting {@code route} in order, starting from {@code from}. */
	public static long total(@Nullable String from, List<String> route, Cost cost)
	{
		long sum = 0;
		String at = from;
		for (String stop : route)
		{
			sum += cost.cost(at, stop);
			at = stop;
		}
		return sum;
	}

	/** Held–Karp with a free end: best[mask][last] is the cheapest way to visit {@code mask}, ending at last. */
	private static List<String> exact(@Nullable String from, List<String> stops, Cost cost)
	{
		int n = stops.size();
		int[][] legs = new int[n][n];
		int[] first = new int[n];
		for (int i = 0; i < n; i++)
		{
			first[i] = cost.cost(from, stops.get(i));
			for (int j = 0; j < n; j++)
			{
				legs[i][j] = i == j ? 0 : cost.cost(stops.get(i), stops.get(j));
			}
		}
		int full = (1 << n) - 1;
		long[][] best = new long[1 << n][n];
		int[][] previous = new int[1 << n][n];
		for (long[] row : best)
		{
			Arrays.fill(row, Long.MAX_VALUE);
		}
		for (int i = 0; i < n; i++)
		{
			best[1 << i][i] = first[i];
			previous[1 << i][i] = -1;
		}
		for (int mask = 1; mask <= full; mask++)
		{
			for (int last = 0; last < n; last++)
			{
				long here = best[mask][last];
				if (here == Long.MAX_VALUE)
				{
					continue;
				}
				for (int next = 0; next < n; next++)
				{
					if ((mask & (1 << next)) != 0)
					{
						continue;
					}
					int nextMask = mask | (1 << next);
					long via = here + legs[last][next];
					if (via < best[nextMask][next])
					{
						best[nextMask][next] = via;
						previous[nextMask][next] = last;
					}
				}
			}
		}
		int last = 0;
		for (int i = 1; i < n; i++)
		{
			if (best[full][i] < best[full][last])
			{
				last = i;
			}
		}
		List<String> route = new ArrayList<>();
		int mask = full;
		while (last != -1)
		{
			route.add(stops.get(last));
			int before = previous[mask][last];
			mask &= ~(1 << last);
			last = before;
		}
		Collections.reverse(route);
		return route;
	}

	/** Nearest next stop each time, then reversing stretches of the route while that makes it cheaper. */
	private static List<String> nearestThenTwoOpt(@Nullable String from, List<String> stops, Cost cost)
	{
		List<String> left = new ArrayList<>(stops);
		List<String> route = new ArrayList<>();
		String at = from;
		while (!left.isEmpty())
		{
			String nearest = left.get(0);
			for (String stop : left)
			{
				if (cost.cost(at, stop) < cost.cost(at, nearest))
				{
					nearest = stop;
				}
			}
			route.add(nearest);
			left.remove(nearest);
			at = nearest;
		}
		long bestTotal = total(from, route, cost);
		boolean improved = true;
		while (improved)
		{
			improved = false;
			for (int i = 0; i < route.size() - 1; i++)
			{
				for (int k = i + 1; k < route.size(); k++)
				{
					Collections.reverse(route.subList(i, k + 1));
					long candidate = total(from, route, cost);
					if (candidate < bestTotal)
					{
						bestTotal = candidate;
						improved = true;
					}
					else
					{
						Collections.reverse(route.subList(i, k + 1));
					}
				}
			}
		}
		return route;
	}
}
