/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.Test;

public class ButtonTextTest
{
	/** Every letter (and space) is 6 pixels wide. */
	private static final ToIntFunction<String> SIX = s -> 6 * s.length();

	@Test
	public void wordsWrapOntoLinesThatFit()
	{
		assertEquals(List.of("Between", "herb runs", "Items"),
			ButtonText.wrap(List.of("Between herb runs", "Items"), SIX, 60, false));
	}

	@Test
	public void aWordTooWideFailsUnlessSplitting()
	{
		assertNull(ButtonText.wrap(List.of("Ultracompost"), SIX, 60, false));
		assertEquals(List.of("Ultracompo", "st", "Items"), ButtonText.wrap(List.of("Ultracompost", "Items"), SIX, 60, true));
	}
}
