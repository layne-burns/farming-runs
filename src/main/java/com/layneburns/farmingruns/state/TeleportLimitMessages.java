/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Reads the game's messages about a teleport's limits: a daily allowance ("You have used 1 of your 3 cabbage teleports
 * for today.") or charges left ("Your quetzal whistle has 10 charges remaining.", "Your book has 90 charges left.",
 * "The book of the dead now has 150 memories remaining."). Pure: unit tested.
 */
public final class TeleportLimitMessages
{
	/** What a message says. */
	@Value
	public static class Limit
	{
		/** True for a daily allowance (used of total); false for charges (left). */
		boolean daily;
		int used;
		int total;
		int left;

		/** Nothing left: the daily allowance is used up, or there are no charges. */
		public boolean usedUp()
		{
			return daily ? used >= total : left <= 0;
		}
	}

	private static final Pattern DAILY = Pattern.compile("you have used (\\d+) of your (\\d+) .*teleports? (?:for )?today");
	private static final Pattern DAILY_ALL = Pattern.compile("(?:used (?:up )?all|no more) .*teleports? (?:for )?today");
	private static final Pattern CHARGES = Pattern.compile("has (\\d+|no) (?:charges?|memories|memory)(?: remaining| left)?");

	private TeleportLimitMessages()
	{
	}

	/** The limit a game message tells of, or null when it isn't about one. */
	@Nullable
	public static Limit parse(String message)
	{
		String text = message.toLowerCase(Locale.ROOT);
		Matcher daily = DAILY.matcher(text);
		if (daily.find())
		{
			return new Limit(true, Integer.parseInt(daily.group(1)), Integer.parseInt(daily.group(2)), 0);
		}
		if (DAILY_ALL.matcher(text).find())
		{
			return new Limit(true, 1, 1, 0);
		}
		Matcher charges = CHARGES.matcher(text);
		if (charges.find())
		{
			int left = charges.group(1).equals("no") ? 0 : Integer.parseInt(charges.group(1));
			return new Limit(false, 0, 0, left);
		}
		return null;
	}
}
