/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class TravelSamplesTest
{
	@Test
	public void medianOfOddAndEvenCounts()
	{
		assertEquals(20, TravelSamples.median(List.of(30, 10, 20)), 0);
		assertEquals(15, TravelSamples.median(List.of(20, 10)), 0);
		assertEquals(0, TravelSamples.median(List.of()), 0);
	}

	@Test
	public void teleportLegsTimeTheTravelAndWalksTimeThePair()
	{
		TravelSamples samples = TravelSamples.NONE
			.record(teleport("catherby", 16_400, 90_000, 2))
			.record(walk("catherby", "catherby_fruit_tree", 12_000, 40_000, 1));
		assertEquals(List.of(16), samples.getTravel().get("catherby"));
		assertEquals(List.of(12), samples.getWalks().get(TravelSamples.pairKey("catherby_fruit_tree", "catherby")));
		assertEquals(List.of(45), samples.getWork().get("catherby"));
		assertEquals(List.of(40), samples.getWork().get("catherby_fruit_tree"));
	}

	@Test
	public void unknownTravelIsLeftOutAndSoIsItsWork()
	{
		assertSame(TravelSamples.NONE, TravelSamples.NONE.record(teleport("weiss", -1, 120_000, 2)));
	}

	@Test
	public void travelOverThreeMinutesIsABreakButTheWorkCounts()
	{
		TravelSamples samples = TravelSamples.NONE.record(teleport("weiss", 181_000, 30_000, 1));
		assertTrue(samples.getTravel().isEmpty());
		assertEquals(List.of(30), samples.getWork().get("weiss"));
	}

	@Test
	public void aWalkFromNowhereTimesOnlyTheWork()
	{
		TravelSamples samples = TravelSamples.NONE.record(walk(null, "ardougne", 5_000, 45_000, 1));
		assertTrue(samples.getTravel().isEmpty());
		assertTrue(samples.getWalks().isEmpty());
		assertEquals(List.of(45), samples.getWork().get("ardougne"));
	}

	@Test
	public void moreThanThreeTimesTheUsualIsLeftOutOnceThereAreThree()
	{
		TravelSamples samples = TravelSamples.NONE
			.record(teleport("yanille", 10_000, 0, 0))
			.record(teleport("yanille", 100_000, 0, 0));
		// Only two so far: too few to call anything an outlier.
		assertEquals(List.of(10, 100), samples.getTravel().get("yanille"));
		samples = samples.record(teleport("yanille", 12_000, 0, 0));
		// The usual is now 12 s: 37 s is too slow to be the way there, 36 s is just slow.
		assertEquals(List.of(10, 100, 12), samples.record(teleport("yanille", 37_000, 0, 0)).getTravel().get("yanille"));
		assertEquals(List.of(10, 100, 12, 36),
			samples.record(teleport("yanille", 36_000, 0, 0)).getTravel().get("yanille"));
	}

	@Test
	public void onlyTheLastSevenAreKept()
	{
		TravelSamples samples = TravelSamples.NONE;
		for (int i = 1; i <= 9; i++)
		{
			samples = samples.record(teleport("kastori", i * 1000, 0, 0));
		}
		assertEquals(List.of(3, 4, 5, 6, 7, 8, 9), samples.getTravel().get("kastori"));
	}

	static LegTimer.Leg teleport(String to, long travelMillis, long workMillis, int patches)
	{
		return new LegTimer.Leg(to, travelMillis, workMillis, null, true, null, patches);
	}

	static LegTimer.Leg walk(String from, String to, long travelMillis, long workMillis, int patches)
	{
		return new LegTimer.Leg(to, travelMillis, workMillis, null, false, from, patches);
	}
}
