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
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

/** One line in a list: the item's icon, a name, and a value on the right. The tooltip says where the items are. */
class ItemRow extends JPanel
{
	private static final Dimension ICON_SIZE = new Dimension(36, 32);

	ItemRow(ItemManager itemManager, BankView.Row row)
	{
		super(new BorderLayout(6, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR));
		setToolTipText(row.getTooltip());
		setAlignmentX(LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(ICON_SIZE);
		itemManager.getImage(row.getIconItemId()).addTo(icon);

		JLabel name = new JLabel(row.getLabel());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel value = new JLabel(row.getValue());
		value.setForeground(ColorScheme.TEXT_COLOR);
		value.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

		add(icon, BorderLayout.WEST);
		add(name, BorderLayout.CENTER);
		add(value, BorderLayout.EAST);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, ICON_SIZE.height + 1));
	}
}
