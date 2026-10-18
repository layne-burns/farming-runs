/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

/** Breaks a button's label into lines that fit its width, for {@link BankSearchButton}. Pure: unit tested. */
final class ButtonText
{
	private ButtonText()
	{
	}

	/**
	 * @param parts   each starts a new line (e.g. the preset's name, then "Items")
	 * @param widthOf the width of some text in the font being tried
	 * @param split   when a single word is wider than the button: true to split it, false to give up on this font
	 * @return the lines, or null when a word doesn't fit and {@code split} is false
	 */
	@Nullable
	static List<String> wrap(List<String> parts, ToIntFunction<String> widthOf, int width, boolean split)
	{
		List<String> lines = new ArrayList<>();
		for (String part : parts)
		{
			String line = "";
			for (String word : part.trim().split("\\s+"))
			{
				if (widthOf.applyAsInt(word) > width)
				{
					if (!split)
					{
						return null;
					}
					if (!line.isEmpty())
					{
						lines.add(line);
						line = "";
					}
					// As many letters as fit on each line.
					String rest = word;
					while (widthOf.applyAsInt(rest) > width && rest.length() > 1)
					{
						int n = rest.length() - 1;
						while (n > 1 && widthOf.applyAsInt(rest.substring(0, n)) > width)
						{
							n--;
						}
						lines.add(rest.substring(0, n));
						rest = rest.substring(n);
					}
					line = rest;
					continue;
				}
				String joined = line.isEmpty() ? word : line + " " + word;
				if (widthOf.applyAsInt(joined) <= width)
				{
					line = joined;
				}
				else
				{
					lines.add(line);
					line = word;
				}
			}
			if (!line.isEmpty())
			{
				lines.add(line);
			}
		}
		return lines;
	}
}
