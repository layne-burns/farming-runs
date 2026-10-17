/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/** A titled block in a tab that collapses when its title is clicked. */
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
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		setAlignmentX(LEFT_ALIGNMENT);

		title.setForeground(ColorScheme.BRAND_ORANGE);
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
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(title, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
		setCollapsed(collapsed);
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
