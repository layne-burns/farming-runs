/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.Color;
import javax.swing.JComponent;
import net.runelite.client.ui.laf.RuneLiteScrollBarUI;

/** RuneLite's scroll bar with a dark grey handle on the panel background, in the {@link Palette} colours. */
class DarkScrollBarUI extends RuneLiteScrollBarUI
{
	@Override
	protected Color getTrackColor(JComponent c, boolean hover, boolean pressed)
	{
		return Palette.BACKGROUND;
	}

	@Override
	protected Color getThumbColor(JComponent c, boolean hover, boolean pressed)
	{
		return hover || pressed ? Palette.SCROLL_THUMB_HOVER : Palette.SCROLL_THUMB;
	}
}
