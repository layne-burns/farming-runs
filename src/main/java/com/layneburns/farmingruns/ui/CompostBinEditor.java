/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.run.CompostBinFill;
import com.layneburns.farmingruns.state.CompostPrefs;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.runelite.client.ui.FontManager;

/**
 * The Run tab's compost bin choices (SPEC §2.7): the item used first, and the ranking that tops the bin up, each row
 * saying why it's there, with ▲ ▼ to reorder and "keep" to hold some back (or never use it). Changes go to
 * {@link CompostPrefs}, which redraws the tab.
 */
final class CompostBinEditor
{
	private static final String NO_DEFAULT = "Top of the ranking";

	private CompostBinEditor()
	{
	}

	/** "Bin, use first:" and a drop-down of the produce, or the top of the ranking. */
	static JPanel defaultChoice(CompostBinFill.Fill fill, CompostPrefs prefs)
	{
		List<CompostBinFill.Row> rows = fill.getRanking();
		List<String> names = new ArrayList<>();
		names.add(NO_DEFAULT);
		rows.forEach(r -> names.add(r.getItem().getName()));
		JComboBox<String> box = new JComboBox<>(names.toArray(new String[0]));
		Integer chosen = prefs.choices().getDefaultItem();
		for (int i = 0; i < rows.size(); i++)
		{
			if (Objects.equals(rows.get(i).getItem().getItemId(), chosen))
			{
				box.setSelectedIndex(i + 1);
			}
		}
		box.setFocusable(false);
		box.setToolTipText("Filled from this first while it has any spare, then down the ranking");
		box.addActionListener(e -> prefs.setDefault(box.getSelectedIndex() == 0 ? null
			: rows.get(box.getSelectedIndex() - 1).getItem().getItemId()));

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel label = TabPanel.text("Farming Guild bin, use first:");
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		panel.add(label);
		panel.add(TabPanel.fill(box));
		return panel;
	}

	/** The ranking: "1. Watermelon" with why underneath, ▲ ▼ and keep; then "Reset order" when it's yours. */
	static JPanel ranking(CompostBinFill.Fill fill, CompostPrefs prefs)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		List<Integer> order = fill.getRanking().stream().map(r -> r.getItem().getItemId()).collect(Collectors.toList());
		Integer chosen = prefs.choices().getDefaultItem();
		for (int i = 0; i < fill.getRanking().size(); i++)
		{
			CompostBinFill.Row row = fill.getRanking().get(i);
			panel.add(line(i, row, order, Objects.equals(row.getItem().getItemId(), chosen), prefs));
			JLabel why = new JLabel(row.getWhy());
			why.setFont(FontManager.getRunescapeSmallFont());
			why.setForeground(Palette.SECONDARY_TEXT);
			why.setBorder(BorderFactory.createEmptyBorder(0, 14, 3, 0));
			why.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(why);
		}
		if (!prefs.choices().getOrder().isEmpty())
		{
			JLabel reset = TabPanel.link("Reset order", () -> prefs.setOrder(List.of()));
			reset.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
			panel.add(reset);
		}
		return panel;
	}

	/** "1. Watermelon (default)   ▲ ▼ keep" */
	private static JPanel line(int index, CompostBinFill.Row row, List<Integer> order, boolean isDefault,
		CompostPrefs prefs)
	{
		JPanel line = new JPanel(new BorderLayout(4, 0));
		line.setOpaque(false);
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel name = new JLabel((index + 1) + ". " + row.getItem().getName() + (isDefault ? " (first)" : ""));
		name.setForeground(row.getSpare() > 0 ? Palette.TEXT : Palette.SECONDARY_TEXT);
		name.setToolTipText(row.getHeld() + " in your bank and inventory"
			+ (row.getItem().isShopBought() ? " · sold by the " + row.getItem().getShop() : ""));
		name.setMinimumSize(new Dimension(0, 0));
		name.setPreferredSize(new Dimension(0, name.getPreferredSize().height));

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		controls.setOpaque(false);
		if (index > 0)
		{
			controls.add(TabPanel.link("▲", () -> prefs.setOrder(moved(order, index, index - 1))));
		}
		if (index < order.size() - 1)
		{
			controls.add(TabPanel.link("▼", () -> prefs.setOrder(moved(order, index, index + 1))));
		}
		JLabel keep = TabPanel.link("keep", () ->
		{
		});
		keep.setToolTipText("How many to hold back from the bin, or never use it");
		JPopupMenu menu = keepMenu(row, prefs, keep);
		keep.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				menu.show(keep, 0, keep.getHeight());
			}
		});
		controls.add(keep);
		line.add(name, BorderLayout.CENTER);
		line.add(controls, BorderLayout.EAST);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		return line;
	}

	/** Automatic, none, 100, a number of your own, or never. */
	private static JPopupMenu keepMenu(CompostBinFill.Row row, CompostPrefs prefs, Component parent)
	{
		int id = row.getItem().getItemId();
		JPopupMenu menu = new JPopupMenu();
		menu.add(item("Automatic", () -> prefs.setKeep(id, null), !row.isOwnKeep()));
		menu.add(item("Keep none", () -> prefs.setKeep(id, 0), row.isOwnKeep() && row.getKeep() == 0));
		menu.add(item("Keep 100", () -> prefs.setKeep(id, 100), row.isOwnKeep() && row.getKeep() == 100));
		menu.add(item("Keep…", () ->
		{
			Integer count = askCount(parent, row);
			if (count != null)
			{
				prefs.setKeep(id, count);
			}
		}, false));
		menu.add(item("Never use it", () -> prefs.setKeep(id, CompostBinFill.NEVER),
			row.isOwnKeep() && row.getKeep() == CompostBinFill.NEVER));
		return menu;
	}

	private static JMenuItem item(String text, Runnable onClick, boolean current)
	{
		JMenuItem item = new JMenuItem(current ? "✓ " + text : text);
		item.addActionListener(e -> onClick.run());
		return item;
	}

	/** Asks how many to keep; null when cancelled or not a number. */
	@Nullable
	private static Integer askCount(Component parent, CompostBinFill.Row row)
	{
		String answer = JOptionPane.showInputDialog(parent, "How many " + row.getItem().getName() + " to keep back?",
			row.getKeep() == CompostBinFill.NEVER ? "" : String.valueOf(row.getKeep()));
		if (answer == null)
		{
			return null;
		}
		try
		{
			int count = Integer.parseInt(answer.trim());
			return count >= 0 ? count : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static List<Integer> moved(List<Integer> order, int from, int to)
	{
		List<Integer> copy = new ArrayList<>(order);
		Collections.swap(copy, from, to);
		return copy;
	}
}
