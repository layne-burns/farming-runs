/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One item in a list: its icon, its name with an optional smaller detail line underneath, and a short count on the
 * right. The name and detail shorten with "..." when the panel is narrow; the tooltip has the full story.
 */
class ItemRow extends JPanel
{
	private static final Dimension ICON_SIZE = new Dimension(36, 32);

	ItemRow(ItemManager itemManager, BankView.Row row)
	{
		super(new BorderLayout(6, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR));
		setAlignmentX(LEFT_ALIGNMENT);
		String tooltip = row.getDetail() == null ? row.getTooltip() : row.getDetail() + " · " + row.getTooltip();
		setToolTipText(tooltip);

		JLabel icon = new JLabel();
		icon.setPreferredSize(ICON_SIZE);
		itemManager.getImage(row.getIconItemId()).addTo(icon);

		JPanel text = new JPanel(new GridLayout(row.getDetail() == null ? 1 : 2, 1));
		text.setOpaque(false);
		JLabel name = new JLabel(row.getLabel());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		text.add(name);
		if (row.getDetail() != null)
		{
			JLabel detail = new JLabel(row.getDetail());
			detail.setFont(FontManager.getRunescapeSmallFont());
			detail.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			text.add(detail);
		}
		// A zero minimum lets the text shrink (and shorten with "...") instead of widening the panel.
		text.setMinimumSize(new Dimension(0, 0));
		text.setPreferredSize(new Dimension(0, ICON_SIZE.height));

		JLabel value = new JLabel(row.getValue());
		value.setForeground(ColorScheme.TEXT_COLOR);
		value.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

		add(icon, BorderLayout.WEST);
		add(text, BorderLayout.CENTER);
		add(value, BorderLayout.EAST);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, ICON_SIZE.height + 1));
	}
}
