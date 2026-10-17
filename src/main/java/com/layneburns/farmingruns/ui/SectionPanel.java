/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * A titled block in a tab that collapses when its title is clicked. Inside, rows can be grouped under small
 * sub-headings (one per patch kind).
 */
class SectionPanel extends JPanel
{
	private final JPanel body = new JPanel();
	private final JLabel title = new JLabel();
	private final String text;
	private boolean collapsed;

	/**
	 * @param onToggle told the new collapsed state, so the tab can keep it across redraws
	 */
	SectionPanel(String text, boolean collapsed, Consumer<Boolean> onToggle)
	{
		super(new BorderLayout());
		this.text = text;
		setBackground(Palette.BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		setAlignmentX(LEFT_ALIGNMENT);

		title.setForeground(Palette.ACCENT);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		title.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setCollapsed(!SectionPanel.this.collapsed);
				onToggle.accept(SectionPanel.this.collapsed);
			}
		});

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(Palette.BACKGROUND);
		add(title, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
		setCollapsed(collapsed);
	}

	/** A small grey sub-heading, e.g. "Fruit tree". */
	void addHeading(String heading)
	{
		JLabel label = new JLabel(heading);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Palette.SECONDARY_TEXT);
		label.setBorder(BorderFactory.createEmptyBorder(4, 4, 1, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
		body.add(label);
	}

	void addRow(JComponent row)
	{
		body.add(row);
	}

	private void setCollapsed(boolean collapsed)
	{
		this.collapsed = collapsed;
		title.setText((collapsed ? "▶ " : "▼ ") + text);
		body.setVisible(!collapsed);
	}
}
