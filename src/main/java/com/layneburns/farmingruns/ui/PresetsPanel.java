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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The Run tab's presets (SPEC §2.3): tick one to load it, tick several to combine them; ✕ deletes one; "Save
 * current setup" saves what's below under a name. Built-in goal presets join this list once their data is in.
 */
class PresetsPanel extends JPanel
{
	/**
	 * @param onTick   a preset ticked (true) or unticked (false)
	 * @param onDelete a preset to delete
	 * @param onSave   "Save current setup" clicked
	 */
	PresetsPanel(Set<String> names, Set<String> ticked, BiConsumer<String, Boolean> onTick, Consumer<String> onDelete,
		Runnable onSave)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Palette.BACKGROUND);
		setAlignmentX(LEFT_ALIGNMENT);

		if (names.isEmpty())
		{
			add(TabPanel.text("No saved presets yet. Set up a run below, then save it here."));
		}
		for (String name : names)
		{
			JPanel row = new JPanel(new BorderLayout());
			row.setBackground(Palette.BACKGROUND);
			row.setAlignmentX(LEFT_ALIGNMENT);
			JCheckBox box = new JCheckBox(name, ticked.contains(name));
			box.setForeground(Palette.TEXT);
			box.setBackground(Palette.BACKGROUND);
			box.setFocusable(false);
			box.setToolTipText("Tick to use this preset; tick several to combine them");
			box.addActionListener(e -> onTick.accept(name, box.isSelected()));
			JLabel delete = new JLabel("✕");
			delete.setForeground(Palette.SECONDARY_TEXT);
			delete.setToolTipText("Delete this preset");
			delete.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			delete.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onDelete.accept(name);
				}
			});
			row.add(box, BorderLayout.CENTER);
			row.add(delete, BorderLayout.EAST);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
			add(row);
		}

		JButton save = TabPanel.button("Save current setup…", "Save the setup below as a preset");
		save.addActionListener(e -> onSave.run());
		JPanel saveRow = new JPanel(new BorderLayout());
		saveRow.setBackground(Palette.BACKGROUND);
		saveRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		saveRow.setAlignmentX(LEFT_ALIGNMENT);
		saveRow.add(save, BorderLayout.WEST);
		saveRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, saveRow.getPreferredSize().height));
		add(saveRow);
	}
}
