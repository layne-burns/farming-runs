/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;

/**
 * Every colour the panel uses, in one place, so the look can be tuned without hunting through the UI classes.
 * Built on RuneLite's own {@link ColorScheme}.
 */
final class Palette
{
	/** The panel background. */
	static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	/** Rows, the search box and buttons: a step darker than the background. */
	static final Color SURFACE = ColorScheme.DARKER_GRAY_COLOR;
	static final Color SURFACE_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;

	/** Names and counts. */
	static final Color TEXT = ColorScheme.TEXT_COLOR;
	/** Detail lines, sub-headings and notes: dimmer than {@link #TEXT}, still easy to read. */
	static final Color SECONDARY_TEXT = ColorScheme.LIGHT_GRAY_COLOR;
	/** Section titles. */
	static final Color ACCENT = ColorScheme.BRAND_ORANGE;

	/** The scroll bar's handle, and while hovered; its track is the {@link #BACKGROUND}. */
	static final Color SCROLL_THUMB = ColorScheme.MEDIUM_GRAY_COLOR;
	static final Color SCROLL_THUMB_HOVER = new Color(95, 95, 95);

	/** Run coverage: full, part, none. */
	static final Color FULL = ColorScheme.PROGRESS_COMPLETE_COLOR;
	static final Color PARTIAL = ColorScheme.PROGRESS_INPROGRESS_COLOR;
	static final Color NONE = ColorScheme.PROGRESS_ERROR_COLOR;

	private Palette()
	{
	}

	/** "#rrggbb", for FlatLaf style strings. */
	static String hex(Color colour)
	{
		return String.format("#%02x%02x%02x", colour.getRed(), colour.getGreen(), colour.getBlue());
	}
}
