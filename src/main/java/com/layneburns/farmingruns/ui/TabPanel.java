/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * Base for the side panel's tabs, so every tab gets RuneLite's dark background and the same layout.
 */
abstract class TabPanel extends JPanel
{
	TabPanel()
	{
		super(new BorderLayout());
		setBackground(Palette.BACKGROUND);
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
		scroll.getViewport().setBackground(Palette.BACKGROUND);
		JScrollBar bar = scroll.getVerticalScrollBar();
		bar.setUI(new DarkScrollBarUI());
		bar.setPreferredSize(new Dimension(SCROLL_BAR_WIDTH, 0));
		bar.setBackground(Palette.BACKGROUND);
		bar.setUnitIncrement(16);
		return scroll;
	}

	/** A dark grey button with white text that lightens on hover, matching the search box. */
	static JButton button(String text, String tooltip)
	{
		JButton button = new JButton(text);
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setBackground(Palette.SURFACE);
		button.setForeground(Color.WHITE);
		button.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setBackground(Palette.SURFACE_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setBackground(Palette.SURFACE);
			}
		});
		return button;
	}

	/** Width text wraps at: the panel is about 225 wide, less padding and the scroll bar. */
	private static final int WRAP_WIDTH = 175;

	/** A text label in the panel's standard light grey, readable on the dark background; long text wraps. */
	static JLabel text(String html)
	{
		JLabel label = new JLabel("<html><div style='width:" + WRAP_WIDTH + "px'>" + html + "</div></html>");
		label.setForeground(Palette.SECONDARY_TEXT);
		return label;
	}
}
