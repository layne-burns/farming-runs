/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.Insets;
import javax.swing.JComponent;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * A RuneLite tab with less padding at the sides (2 pixels, not 10), so names like "Locations" fit when the panel's
 * tabs share its width three to a row. The borders it draws (the selected tab's underline) are unchanged.
 */
class CompactTab extends MaterialTab
{
	private static final int SIDE = 2;

	CompactTab(String text, MaterialTabGroup group, JComponent content)
	{
		super(text, group, content);
	}

	@Override
	public Insets getInsets()
	{
		return narrow(super.getInsets());
	}

	@Override
	public Insets getInsets(Insets insets)
	{
		return narrow(super.getInsets(insets));
	}

	private static Insets narrow(Insets insets)
	{
		insets.left = Math.min(insets.left, SIDE);
		insets.right = Math.min(insets.right, SIDE);
		return insets;
	}
}
