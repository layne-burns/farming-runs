/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.vendor.timetracking;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Checks that the vendored RuneLite classes are wired up and callable from our code. If a re-vendor breaks the
 * "make public" edits in scripts/vendor_timetracking.py, this stops compiling.
 */
public class VendoredDecodeTest
{
	@Test
	public void herbVarbitValuesDecode()
	{
		PatchState weeds = PatchImplementation.HERB.forVarbitValue(0);
		assertEquals(Produce.WEEDS, weeds.getProduce());
		assertEquals(CropState.GROWING, weeds.getCropState());

		PatchState guamReady = PatchImplementation.HERB.forVarbitValue(10);
		assertEquals(Produce.GUAM, guamReady.getProduce());
		assertEquals(CropState.HARVESTABLE, guamReady.getCropState());
		assertEquals(Produce.GUAM.getHarvestStages(), guamReady.getStages());
	}

	@Test
	public void produceLooksUpByCropItem()
	{
		assertEquals(Produce.RANARR, Produce.getByItemID(Produce.RANARR.getItemID()));
	}
}
