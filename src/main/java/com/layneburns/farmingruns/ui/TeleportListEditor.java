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
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.runelite.client.ui.FontManager;

/**
 * A location's teleports in order of preference: move one up or down, remove it, add another, or go back to the
 * default order. Every change is handed to {@code onChange} as the new order (null for "back to default").
 */
class TeleportListEditor extends JPanel
{
	private static final String ADD_PROMPT = "+ Add a teleport…";
	/** Destinations past this many are split into submenus by first letter. */
	private static final int LONG_LIST = 20;

	TeleportListEditor(LocationsView.Entry entry, Consumer<List<String>> onChange)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Palette.BACKGROUND);
		setAlignmentX(LEFT_ALIGNMENT);

		List<String> order = entry.getTeleports().stream().map(LocationsView.TeleportLine::getRef)
			.collect(Collectors.toList());
		for (int i = 0; i < entry.getTeleports().size(); i++)
		{
			LocationsView.TeleportLine line = entry.getTeleports().get(i);
			add(line(i, line, order, onChange));
			if (line.getWarning() != null)
			{
				JLabel warning = new JLabel(line.getWarning());
				warning.setFont(FontManager.getRunescapeSmallFont());
				warning.setForeground(Palette.PARTIAL);
				warning.setBorder(BorderFactory.createEmptyBorder(0, 14, 2, 0));
				warning.setAlignmentX(LEFT_ALIGNMENT);
				add(warning);
			}
		}
		JLabel add = link(ADD_PROMPT, () ->
		{
		});
		JPopupMenu menu = addMenu(entry.getAddMenu(), order, onChange);
		add.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				menu.show(add, 0, add.getHeight());
			}
		});
		add.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
		add(add);
		if (entry.isCustomTeleports())
		{
			JLabel reset = link("Reset to default", () -> onChange.accept(null));
			reset.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
			add(reset);
		}
	}

	/** "1. Skills necklace (Farming Guild)   ▲ ▼ ✕" */
	private static JPanel line(int index, LocationsView.TeleportLine line, List<String> order,
		Consumer<List<String>> onChange)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Palette.BACKGROUND);
		row.setAlignmentX(LEFT_ALIGNMENT);
		JLabel name = new JLabel((index + 1) + ". " + line.getName());
		name.setForeground(index == 0 ? Palette.TEXT : Palette.SECONDARY_TEXT);
		String when = index == 0 ? "Used first, if you have it" : "Used if the ones above aren't available";
		name.setToolTipText(line.getDetail() == null ? when : line.getDetail() + " · " + when);
		name.setMinimumSize(new Dimension(0, 0));
		name.setPreferredSize(new Dimension(0, name.getPreferredSize().height));

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		controls.setOpaque(false);
		if (index > 0)
		{
			controls.add(link("▲", () -> onChange.accept(moved(order, index, index - 1))));
		}
		if (index < order.size() - 1)
		{
			controls.add(link("▼", () -> onChange.accept(moved(order, index, index + 1))));
		}
		if (order.size() > 1)
		{
			controls.add(link("✕", () ->
			{
				List<String> without = new ArrayList<>(order);
				without.remove(index);
				onChange.accept(without);
			}));
		}
		row.add(name, BorderLayout.CENTER);
		row.add(controls, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/**
	 * The "Add a teleport" menu: a submenu per group (Jewellery, spells...), then per method with several
	 * destinations. Fairy rings, with dozens of codes, are split by first letter. Listed ones are greyed out.
	 */
	private static JPopupMenu addMenu(List<LocationsView.TeleportMenuGroup> groups, List<String> order,
		Consumer<List<String>> onChange)
	{
		JPopupMenu menu = new JPopupMenu();
		for (LocationsView.TeleportMenuGroup group : groups)
		{
			JMenu groupMenu = new JMenu(group.getTitle());
			for (LocationsView.TeleportMenuMethod method : group.getMethods())
			{
				if (method.getOptions().size() == 1)
				{
					groupMenu.add(item(method.getOptions().get(0), order, onChange));
					continue;
				}
				JMenu methodMenu = new JMenu(method.getName());
				if (method.getOptions().size() > LONG_LIST)
				{
					Map<Character, JMenu> byLetter = new TreeMap<>();
					for (LocationsView.TeleportOption option : method.getOptions())
					{
						byLetter.computeIfAbsent(option.getName().charAt(0), c -> new JMenu(c + "…"))
							.add(item(option, order, onChange));
					}
					byLetter.values().forEach(methodMenu::add);
				}
				else
				{
					method.getOptions().forEach(o -> methodMenu.add(item(o, order, onChange)));
				}
				groupMenu.add(methodMenu);
			}
			menu.add(groupMenu);
		}
		return menu;
	}

	private static JMenuItem item(LocationsView.TeleportOption option, List<String> order,
		Consumer<List<String>> onChange)
	{
		JMenuItem item = new JMenuItem(option.getDetail() == null ? option.getName()
			: option.getName() + " – " + option.getDetail());
		item.setEnabled(!option.isListed());
		item.addActionListener(e ->
		{
			List<String> added = new ArrayList<>(order);
			added.add(option.getRef());
			onChange.accept(added);
		});
		return item;
	}

	private static List<String> moved(List<String> order, int from, int to)
	{
		List<String> copy = new ArrayList<>(order);
		Collections.swap(copy, from, to);
		return copy;
	}

	/** Small clickable text that lights up on hover. */
	private static JLabel link(String text, Runnable onClick)
	{
		JLabel label = new JLabel(text);
		label.setForeground(Palette.SECONDARY_TEXT);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setForeground(Palette.ACCENT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setForeground(Palette.SECONDARY_TEXT);
			}
		});
		return label;
	}
}
