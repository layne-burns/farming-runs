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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * A location's teleports in order of preference: move one up or down, remove it, add another, or go back to the
 * default order. Every change is handed to {@code onChange} as the new order (null for "back to default").
 */
class TeleportListEditor extends JPanel
{
	private static final String ADD_PROMPT = "Add a teleport…";

	TeleportListEditor(LocationsView.Entry entry, Consumer<List<String>> onChange)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Palette.BACKGROUND);
		setAlignmentX(LEFT_ALIGNMENT);

		List<String> order = entry.getTeleports().stream().map(LocationsView.TeleportLine::getId)
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
		if (!entry.getAddable().isEmpty())
		{
			add(addBox(entry.getAddable(), order, onChange));
		}
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
		name.setToolTipText(index == 0 ? "Used first, if you have it" : "Used if the ones above aren't available");
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

	private static JComboBox<Object> addBox(List<LocationsView.TeleportOption> addable, List<String> order,
		Consumer<List<String>> onChange)
	{
		JComboBox<Object> box = new JComboBox<>();
		box.addItem(ADD_PROMPT);
		addable.forEach(box::addItem);
		box.setBackground(Palette.SURFACE);
		box.setForeground(Palette.TEXT);
		box.setFocusable(false);
		box.setAlignmentX(LEFT_ALIGNMENT);
		box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
		box.setPreferredSize(new Dimension(0, box.getPreferredSize().height));
		box.addActionListener(e ->
		{
			if (box.getSelectedItem() instanceof LocationsView.TeleportOption)
			{
				List<String> added = new ArrayList<>(order);
				added.add(((LocationsView.TeleportOption) box.getSelectedItem()).getId());
				onChange.accept(added);
			}
		});
		return box;
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
