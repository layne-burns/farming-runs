/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Base for the side panel's tabs, so every tab gets RuneLite's dark background and the same layout.
 */
abstract class TabPanel extends JPanel
{
	TabPanel()
	{
		super(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}
}
