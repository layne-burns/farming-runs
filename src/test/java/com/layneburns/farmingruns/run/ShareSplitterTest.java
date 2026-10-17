/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertArrayEquals;
import java.util.List;
import org.junit.Test;

public class ShareSplitterTest
{
	private static final int PLENTY = 1000;

	@Test
	public void evenSplitGivesTheOddPatchToTheFirst()
	{
		assertArrayEquals(new int[]{5, 4}, split(9, share(1, PLENTY), share(1, PLENTY)));
		assertArrayEquals(new int[]{3, 3, 3}, split(9, share(1, PLENTY), share(1, PLENTY), share(1, PLENTY)));
	}

	@Test
	public void weightsSplitInProportion()
	{
		assertArrayEquals(new int[]{6, 3}, split(9, share(2, PLENTY), share(1, PLENTY)));
	}

	@Test
	public void aCropShortOfSeedsLeavesTheRestToOthers()
	{
		assertArrayEquals(new int[]{2, 7}, split(9, share(1, 2), share(1, PLENTY)));
	}

	@Test
	public void exactCountsComeFirst()
	{
		assertArrayEquals(new int[]{3, 6}, split(9, exact(3, PLENTY), share(1, PLENTY)));
		assertArrayEquals(new int[]{9, 0}, split(9, exact(12, PLENTY), share(1, PLENTY)));
		assertArrayEquals(new int[]{2, 7}, split(9, exact(3, 2), share(1, PLENTY)));
	}

	@Test
	public void notEnoughSeedsAnywhereLeavesPatchesOver()
	{
		assertArrayEquals(new int[]{2, 3}, split(9, share(1, 2), share(1, 3)));
	}

	private static int[] split(int patches, ShareSplitter.Share... shares)
	{
		return ShareSplitter.split(patches, List.of(shares));
	}

	private static ShareSplitter.Share share(int weight, int capacity)
	{
		return new ShareSplitter.Share(weight, null, capacity);
	}

	private static ShareSplitter.Share exact(int patches, int capacity)
	{
		return new ShareSplitter.Share(1, patches, capacity);
	}
}
