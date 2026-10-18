/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static com.layneburns.farmingruns.run.TravelSamplesTest.teleport;
import static com.layneburns.farmingruns.run.TravelSamplesTest.walk;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LearnedCostsTest
{
	/** Every teleport is 30 ticks; a and b are linked by a 10-tick walk. */
	private static final RoutePlanner.Cost TABLE = (from, to) ->
		from != null && (from + to).matches("ab|ba") ? 10 : 30;

	@Test
	public void withNothingLearnedTheTableIsUsed()
	{
		LearnedCosts costs = new LearnedCosts(TABLE, TravelSamples.NONE);
		assertEquals(30, costs.cost(null, "a"));
		assertEquals(30, costs.cost("c", "a"));
		assertEquals(10, costs.cost("b", "a"));
		assertEquals(3 * OnTheWay.WORK_TICKS_PER_PATCH, costs.work("a", 3));
	}

	@Test
	public void oneTimeIsNotEnough()
	{
		LearnedCosts costs = new LearnedCosts(TABLE, TravelSamples.NONE.record(teleport("a", 60_000, 60_000, 1)));
		assertEquals(30, costs.cost(null, "a"));
		assertEquals(2 * OnTheWay.WORK_TICKS_PER_PATCH, costs.work("a", 2));
	}

	@Test
	public void twoOrMoreTimesGiveTheMedianInTicks()
	{
		TravelSamples samples = TravelSamples.NONE
			.record(teleport("a", 60_000, 30_000, 1))
			.record(teleport("a", 66_000, 60_000, 2))
			.record(teleport("a", 90_000, 50_000, 2));
		LearnedCosts costs = new LearnedCosts(TABLE, samples);
		// 66 s is 110 ticks, from anywhere.
		assertEquals(110, costs.cost(null, "a"));
		assertEquals(110, costs.cost("c", "a"));
		// The table's walk from b is still quicker than teleporting.
		assertEquals(10, costs.cost("b", "a"));
		// 30 s a patch (the middle of 30, 30 and 25), so three patches are 90 s: 150 ticks.
		assertEquals(150, costs.work("a", 3));
	}

	@Test
	public void aTeleportYouveTimedFasterThanTheLinkWins()
	{
		TravelSamples samples = TravelSamples.NONE
			.record(teleport("a", 3_000, 0, 0))
			.record(teleport("a", 3_000, 0, 0));
		assertEquals(5, new LearnedCosts(TABLE, samples).cost("b", "a"));
	}

	@Test
	public void aWalkYouveTimedCostsWhatItTookEitherWay()
	{
		TravelSamples samples = TravelSamples.NONE
			.record(walk("c", "d", 12_000, 0, 0))
			.record(walk("d", "c", 18_000, 0, 0));
		LearnedCosts costs = new LearnedCosts(TABLE, samples);
		// 15 s is 25 ticks.
		assertEquals(25, costs.cost("c", "d"));
		assertEquals(25, costs.cost("d", "c"));
		assertEquals(30, costs.cost(null, "d"));
	}
}
