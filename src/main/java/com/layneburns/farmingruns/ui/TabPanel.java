/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.laf.RuneLiteScrollBarUI;

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

	/** Width of the scroll bar in our tabs: thinner than RuneLite's default 17. */
	private static final int SCROLL_BAR_WIDTH = 6;

	/**
	 * A scroll pane in RuneLite's style: dark, borderless, a thin RuneLite scroll bar, and never a sideways one.
	 */
	static JScrollPane scrollPane(JComponent content)
	{
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		JScrollBar bar = scroll.getVerticalScrollBar();
		bar.setUI(new RuneLiteScrollBarUI());
		bar.setPreferredSize(new Dimension(SCROLL_BAR_WIDTH, 0));
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bar.setUnitIncrement(16);
		return scroll;
	}

	/** A text label in the panel's standard light grey, readable on the dark background. */
	static JLabel text(String html)
	{
		JLabel label = new JLabel("<html>" + html + "</html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}
}
