/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RunStatsTest
{
	private static RunSummary run(boolean completed, long millis, int herbs)
	{
		return new RunSummary(completed, millis, 5000, 9, 9, 9, Map.of("Grimy ranarr weed", 40), herbs, "Ranarr",
			List.of());
	}

	@Test
	public void firstRunSetsBestsWithoutCallingThemRecords()
	{
		RunStats.Added first = RunStats.NONE.add(run(true, 600_000, 9));
		assertEquals(1, first.getStats().getRuns());
		assertEquals(600_000, first.getStats().getFastestMillis());
		assertEquals(9, first.getStats().getMostHerbs());
		assertTrue(first.getRun().getRecords().isEmpty());
	}

	@Test
	public void moreHerbsAndFasterRunsAreRecords()
	{
		RunStats stats = RunStats.NONE.add(run(true, 600_000, 9)).getStats();
		RunStats.Added second = stats.add(run(true, 540_000, 14));
		assertEquals(List.of("Most herbs from one patch: 14 Ranarr (was 9)", "Fastest run: 9:00 (was 10:00)"),
			second.getRun().getRecords());
		assertEquals(2, second.getStats().getRuns());
		assertEquals(10_000, second.getStats().getXp());
	}

	@Test
	public void aStoppedRunAddsItsWorkButIsntCountedAsARun()
	{
		RunStats.Added stopped = RunStats.NONE.add(run(false, 100_000, 3));
		assertEquals(0, stopped.getStats().getRuns());
		assertEquals(0, stopped.getStats().getFastestMillis());
		assertEquals(9, stopped.getStats().getPatchesPlanted());
	}
}
