/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RunTrackerTest
{
	@Test
	public void herbsAreCountedFromHarvestXp()
	{
		// Lantadyme gives 67.5 XP a herb; the client's XP is whole, so 11 herbs show as 742 or 743.
		assertEquals(11, RunTracker.herbsFromXp(742, 67.5));
		assertEquals(11, RunTracker.herbsFromXp(743, 67.5));
		assertEquals(0, RunTracker.herbsFromXp(500, 0));
	}
}
