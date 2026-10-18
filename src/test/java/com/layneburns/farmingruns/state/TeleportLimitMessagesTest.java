/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TeleportLimitMessagesTest
{
	@Test
	public void dailyAllowances()
	{
		TeleportLimitMessages.Limit cabbage = TeleportLimitMessages.parse(
			"You have used 1 of your 3 cabbage teleports for today.");
		assertTrue(cabbage.isDaily());
		assertEquals(1, cabbage.getUsed());
		assertFalse(cabbage.usedUp());
		assertTrue(TeleportLimitMessages.parse("You have used 5 of your 5 Ardougne farm teleports for today.").usedUp());
	}

	@Test
	public void charges()
	{
		assertEquals(10, TeleportLimitMessages.parse("Your quetzal whistle has 10 charges remaining.").getLeft());
		assertEquals(90, TeleportLimitMessages.parse("Your book has 90 charges left.").getLeft());
		assertEquals(150, TeleportLimitMessages.parse("The book of the dead now has 150 memories remaining.").getLeft());
		assertTrue(TeleportLimitMessages.parse("Your quetzal whistle has 0 charges remaining.").usedUp());
	}

	@Test
	public void otherMessages()
	{
		assertNull(TeleportLimitMessages.parse("You treat the herb patch with supercompost."));
	}
}
