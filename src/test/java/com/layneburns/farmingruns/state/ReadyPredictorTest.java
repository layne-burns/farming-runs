/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import org.junit.Test;

public class ReadyPredictorTest
{
	/** A time on a 20-minute farming tick boundary (no offset), so the arithmetic is easy to follow. */
	private static final long TICK = 1_800_000_000L - 1_800_000_000L % 1200;

	@Test
	public void justPlantedGuamIsReadyFourTicksLater()
	{
		// Herb value 4: guam, growing, stage 0 of 5; herbs grow every 20 minutes.
		PatchStatus planted = predict("4:" + TICK, TICK + 60);
		assertEquals(PatchStatus.Readiness.GROWING, planted.getReadiness());
		assertEquals("Guam", planted.getCrop());
		assertEquals(TICK + 4 * 1200, planted.getReadyAtSeconds());
		assertEquals("Guam: ready in 1 h 19 min", planted.describe(TICK + 60));

		PatchStatus later = predict("4:" + TICK, TICK + 4 * 1200 + 5);
		assertEquals(PatchStatus.Readiness.READY, later.getReadiness());
		assertEquals(4, later.getStage());
	}

	@Test
	public void harvestableWeedsAndUnknown()
	{
		assertEquals(PatchStatus.Readiness.READY, predict("10:" + TICK, TICK).getReadiness());

		PatchStatus weeds = predict("0:" + TICK, TICK);
		assertEquals(PatchStatus.Readiness.EMPTY, weeds.getReadiness());
		assertNull(weeds.getCrop());

		assertEquals(PatchStatus.UNKNOWN, predict(null, TICK));
		assertEquals(PatchStatus.UNKNOWN, predict("garbage", TICK));
		assertEquals(PatchStatus.UNKNOWN, predict("4:0", TICK));
	}

	@Test
	public void tickOffsetShiftsTicksOnceMeasuredPrecisely()
	{
		// Offset 7 minutes, measured to 40-minute precision: ticks fall 7 minutes before the clock boundary.
		assertEquals(TICK - 7 * 60, ReadyPredictor.tickTime(20, 0, TICK, 40, 7));
		// Not yet measured precisely enough: no offset.
		assertEquals(TICK, ReadyPredictor.tickTime(20, 0, TICK, 10, 7));
		assertEquals(TICK + 2 * 1200, ReadyPredictor.tickTime(20, 2, TICK + 30, null, null));
	}

	@Test
	public void durationsReadNaturally()
	{
		assertEquals("under a minute", PatchStatus.duration(0));
		assertEquals("45 min", PatchStatus.duration(45 * 60));
		assertEquals("2 h", PatchStatus.duration(2 * 3600));
		assertEquals("1 h 20 min", PatchStatus.duration(80 * 60));
	}

	private static PatchStatus predict(String stored, long now)
	{
		return ReadyPredictor.predict(stored, PatchImplementation.HERB, null, null, false, now);
	}
}
