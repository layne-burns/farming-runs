/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class RoutePlannerTest
{
	/** Random costs, different each way, like arrive costs plus a few cheap links. */
	private static RoutePlanner.Cost randomCosts(Random random, List<String> stops)
	{
		Map<String, Integer> costs = new HashMap<>();
		for (String a : stops)
		{
			costs.put("null>" + a, 10 + random.nextInt(50));
			for (String b : stops)
			{
				costs.put(a + ">" + b, random.nextInt(4) == 0 ? 1 + random.nextInt(10) : 10 + random.nextInt(50));
			}
		}
		return (from, to) -> costs.get(from + ">" + to);
	}

	private static List<String> stops(int n)
	{
		List<String> stops = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			stops.add("s" + i);
		}
		return stops;
	}

	/** The cheapest total over every order, by trying them all. */
	private static long bruteForce(String from, List<String> left, RoutePlanner.Cost cost)
	{
		if (left.isEmpty())
		{
			return 0;
		}
		long best = Long.MAX_VALUE;
		for (String next : left)
		{
			List<String> rest = new ArrayList<>(left);
			rest.remove(next);
			best = Math.min(best, cost.cost(from, next) + bruteForce(next, rest, cost));
		}
		return best;
	}

	@Test
	public void exactMatchesBruteForceOnSmallRuns()
	{
		Random random = new Random(7);
		for (int trial = 0; trial < 40; trial++)
		{
			List<String> stops = stops(2 + random.nextInt(6));
			RoutePlanner.Cost cost = randomCosts(random, stops);
			List<String> route = RoutePlanner.order(null, stops, cost);
			assertEquals(new HashSet<>(stops), new HashSet<>(route));
			assertEquals(bruteForce(null, stops, cost), RoutePlanner.total(null, route, cost));
		}
	}

	@Test
	public void bigRunsVisitEveryStopOnce()
	{
		Random random = new Random(11);
		List<String> stops = stops(30);
		RoutePlanner.Cost cost = randomCosts(random, stops);
		List<String> route = RoutePlanner.order(null, stops, cost);
		assertEquals(30, route.size());
		assertEquals(new HashSet<>(stops), new HashSet<>(route));
		// Every leg costs at most 59, so a sane order stays under that bound.
		assertTrue(RoutePlanner.total(null, route, cost) <= 30 * 59);
	}

	@Test
	public void aStopYouAreAtGoesFirst()
	{
		List<String> stops = stops(5);
		RoutePlanner.Cost cost = (from, to) -> from == null ? 50 : (from.equals("s2") ? 5 : 30);
		List<String> route = RoutePlanner.order("s3", stops, cost);
		assertEquals("s3", route.get(0));
		assertEquals(5, route.size());
	}
}
