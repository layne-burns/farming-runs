/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import com.layneburns.farmingruns.vendor.timetracking.PatchState;
import org.junit.Test;

public class CompostBinsTest
{
	private static final PatchImplementation BIG = PatchImplementation.BIG_COMPOST;

	private static PatchState big(int varbit)
	{
		return BIG.forVarbitValue(varbit);
	}

	@Test
	public void anEmptyBinTakesThirtyPineapples()
	{
		assertEquals(new CompostBins.Work(0, 30, true), CompostBins.work(big(0), BIG, false));
	}

	@Test
	public void aPartFilledBinTakesTheRest()
	{
		// Varbit 5: 5 items in (filling).
		assertEquals(new CompostBins.Work(0, 25, true), CompostBins.work(big(5), BIG, false));
	}

	@Test
	public void aReadyBinIsCollectedThenRefilled()
	{
		// Varbit 52: supercompost ready, 5 left to take.
		assertEquals(new CompostBins.Work(5, 30, true), CompostBins.work(big(52), BIG, false));
	}

	@Test
	public void aRottingBinNeedsNothingUntilItsDone()
	{
		// Varbit 93: closed, rotting.
		assertFalse(CompostBins.work(big(93), BIG, false).isNeeded());
		assertEquals(new CompostBins.Work(30, 30, true), CompostBins.work(big(93), BIG, true));
		assertTrue(CompostBins.isDone(big(93)));
		assertFalse(CompostBins.isDone(big(5)));
	}

	@Test
	public void neverSeenMeansNothingPlanned()
	{
		assertFalse(CompostBins.work(null, BIG, false).isNeeded());
	}
}
