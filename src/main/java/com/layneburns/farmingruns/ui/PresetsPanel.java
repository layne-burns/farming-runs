/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GoalPreset;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.runelite.client.ui.FontManager;

/**
 * The Run tab's presets (SPEC §2.3). Goals (built in: make Prayer potions, Farming XP...) and your saved presets;
 * tick one to use it, tick several to combine them. Goals are added from "+ Add a goal" and removed by unticking.
 * Keys passed to the callbacks: {@link #goalKey} and {@link #presetKey}.
 */
class PresetsPanel extends JPanel
{
	static String goalKey(String goalId)
	{
		return "goal:" + goalId;
	}

	static String presetKey(String name)
	{
		return "preset:" + name;
	}

	/**
	 * @param farmingLevel your Farming level (-1 if unknown), to mark goals you can't plant yet
	 * @param onTick       a key ticked (true) or unticked (false)
	 * @param onDelete     a saved preset's name to delete
	 * @param onSave       "Save current setup" clicked
	 */
	PresetsPanel(List<GoalPreset> goals, Set<String> savedNames, Set<String> ticked, int farmingLevel,
		BiConsumer<String, Boolean> onTick, Consumer<String> onDelete, Runnable onSave)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Palette.BACKGROUND);
		setAlignmentX(LEFT_ALIGNMENT);

		add(heading("Goals"));
		for (GoalPreset goal : goals)
		{
			if (ticked.contains(goalKey(goal.getId())))
			{
				add(row(goal.getName(), true, tooltip(goal, farmingLevel), on -> onTick.accept(goalKey(goal.getId()), on),
					null));
			}
		}
		add(addGoal(goals, ticked, farmingLevel, onTick));

		add(heading("Your presets"));
		if (savedNames.isEmpty())
		{
			add(TabPanel.text("None yet: set up a run below, then save it."));
		}
		for (String name : savedNames)
		{
			add(row(name, ticked.contains(presetKey(name)), "Tick to use this preset; tick several to combine them",
				on -> onTick.accept(presetKey(name), on), () -> onDelete.accept(name)));
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

	/** A tick box row, with a ✕ when it can be deleted. */
	private static JPanel row(String name, boolean selected, String tooltip, Consumer<Boolean> onTick, Runnable onDelete)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(Palette.BACKGROUND);
		row.setAlignmentX(LEFT_ALIGNMENT);
		JCheckBox box = new JCheckBox(name, selected);
		box.setForeground(Palette.TEXT);
		box.setBackground(Palette.BACKGROUND);
		box.setFocusable(false);
		box.setToolTipText(tooltip);
		box.addActionListener(e -> onTick.accept(box.isSelected()));
		row.add(box, BorderLayout.CENTER);
		if (onDelete != null)
		{
			row.add(link("✕", "Delete this preset", onDelete), BorderLayout.EAST);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** "+ Add a goal..." opening a menu by category; goals already ticked are greyed out. */
	private static JPanel addGoal(List<GoalPreset> goals, Set<String> ticked, int farmingLevel,
		BiConsumer<String, Boolean> onTick)
	{
		Map<String, JMenu> categories = new LinkedHashMap<>();
		for (GoalPreset goal : goals)
		{
			JMenu category = categories.computeIfAbsent(goal.getCategory(), JMenu::new);
			boolean tooLow = farmingLevel >= 0 && goal.getMinFarmingLevel() > farmingLevel;
			JMenuItem item = new JMenuItem(goal.getName() + (tooLow ? " (needs " + goal.getMinFarmingLevel() + ")"
				: " (" + goal.getMinFarmingLevel() + ")"));
			item.setToolTipText(tooltip(goal, farmingLevel));
			item.setEnabled(!ticked.contains(goalKey(goal.getId())));
			item.addActionListener(e -> onTick.accept(goalKey(goal.getId()), true));
			category.add(item);
		}
		JPopupMenu menu = new JPopupMenu();
		categories.values().forEach(menu::add);
		JLabel add = new JLabel("+ Add a goal…");
		add.setForeground(Palette.SECONDARY_TEXT);
		add.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		add.setToolTipText("Pick something to make; the run plants what it needs");
		add.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				menu.show(add, 0, add.getHeight());
			}
		});
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(Palette.BACKGROUND);
		wrapper.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 0));
		wrapper.setAlignmentX(LEFT_ALIGNMENT);
		wrapper.add(add, BorderLayout.WEST);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapper.getPreferredSize().height));
		return wrapper;
	}

	/** Why the goal is worth having, what it plants, what it still needs from elsewhere, and its level. */
	private static String tooltip(GoalPreset goal, int farmingLevel)
	{
		StringBuilder html = new StringBuilder("<html><b>").append(goal.getName()).append("</b><br>").append(goal.getWhy());
		html.append("<br>Plants: ");
		goal.getCrops().forEach(c -> html.append(c.getSeed().getName()).append(", "));
		html.setLength(html.length() - 2);
		if (!goal.getNotFarmed().isEmpty())
		{
			html.append("<br>Also needs (not farmed): ").append(String.join(", ", goal.getNotFarmed()));
		}
		html.append("<br>Farming ").append(goal.getMinFarmingLevel());
		if (farmingLevel >= 0 && goal.getMinFarmingLevel() > farmingLevel)
		{
			html.append(" (you're ").append(farmingLevel).append(")");
		}
		return html.append("</html>").toString();
	}

	private static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Palette.SECONDARY_TEXT);
		label.setBorder(BorderFactory.createEmptyBorder(4, 0, 1, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel link(String text, String tooltip, Runnable onClick)
	{
		JLabel label = new JLabel(text);
		label.setForeground(Palette.SECONDARY_TEXT);
		label.setToolTipText(tooltip);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}
		});
		return label;
	}
}
