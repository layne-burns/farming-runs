/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.Set;
import org.junit.Test;

public class TreeRoutineTest
{
	@Test
	public void plantsWhoseCropGrowsBackCanBeLeftStanding()
	{
		assertTrue(TreeRoutine.regrows(PatchImplementation.FRUIT_TREE));
		assertTrue(TreeRoutine.regrows(PatchImplementation.BUSH));
		assertTrue(TreeRoutine.regrows(PatchImplementation.CACTUS));
		assertFalse(TreeRoutine.regrows(PatchImplementation.HERB));
		assertFalse(TreeRoutine.regrows(PatchImplementation.CALQUAT));

		KindPlan pickOnly = KindPlan.AUTO.withTreeRoutine(TreeRoutine.LEAVE_STANDING);
		assertTrue(pickOnly.harvestOnly(PatchImplementation.BUSH));
		// A bush type you chose to replace (redberry, 5101) is cleared and replanted; other bushes are kept.
		Seed redberry = new DataLoader(new Gson()).load().seedByItem(5101);
		Seed whiteberry = new DataLoader(new Gson()).load().seedByItem(5105);
		KindPlan replaceRedberry = pickOnly.withReplaceStanding(Set.of(redberry.getItemId()));
		assertFalse(replaceRedberry.keeps(PatchImplementation.BUSH, redberry));
		assertTrue(replaceRedberry.keeps(PatchImplementation.BUSH, whiteberry));
		assertFalse(pickOnly.harvestOnly(PatchImplementation.HERB));
	}
}
