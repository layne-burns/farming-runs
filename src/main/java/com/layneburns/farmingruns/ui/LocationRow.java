/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.state.LocationStatus;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * One location in the Locations tab. The header shows whether it's unlocked; clicking it opens the details: the
 * status and why, the teleport to use, an unlock override, and each patch.
 */
class LocationRow extends JPanel
{
	/** Width the wrapped text lines are laid out to (the panel is about 225 wide). */
	private static final int TEXT_WIDTH = 165;
	private static final String[] OVERRIDES = {"Follow the rules", "Always unlocked", "Always locked"};

	/**
	 * @param onToggle   called when the header is clicked
	 * @param onTeleports the new teleport order, or null for the default
	 * @param onOverride  true / false to force unlocked / locked, or null to follow the rules
	 */
	LocationRow(LocationsView.Entry entry, boolean expanded, Runnable onToggle, Consumer<List<String>> onTeleports,
		Consumer<Boolean> onOverride)
	{
		super(new BorderLayout());
		setBackground(Palette.BACKGROUND);
		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.BACKGROUND));
		setAlignmentX(LEFT_ALIGNMENT);
		add(header(entry, onToggle), BorderLayout.NORTH);
		if (expanded)
		{
			add(details(entry, onTeleports, onOverride), BorderLayout.CENTER);
		}
	}

	private static JPanel header(LocationsView.Entry entry, Runnable onToggle)
	{
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(Palette.SURFACE);
		header.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setToolTipText(entry.getStatusText());

		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setOpaque(false);
		JLabel name = new JLabel(entry.getName());
		name.setForeground(Palette.TEXT);
		JLabel kinds = new JLabel(entry.getKinds());
		kinds.setFont(FontManager.getRunescapeSmallFont());
		kinds.setForeground(Palette.SECONDARY_TEXT);
		text.add(name);
		text.add(kinds);
		text.setMinimumSize(new Dimension(0, 0));
		text.setPreferredSize(new Dimension(0, 30));

		JLabel state = new JLabel(shortStatus(entry.getStatus()));
		state.setForeground(statusColour(entry.getStatus()));

		header.add(text, BorderLayout.CENTER);
		header.add(state, BorderLayout.EAST);
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onToggle.run();
			}
		});
		return header;
	}

	private static JPanel details(LocationsView.Entry entry, Consumer<List<String>> onTeleports,
		Consumer<Boolean> onOverride)
	{
		JPanel details = new JPanel();
		details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
		details.setBackground(Palette.BACKGROUND);
		details.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 4));

		details.add(wrapped(entry.getStatusText(), statusColour(entry.getStatus())));

		details.add(caption("Teleports (first one you have is used)"));
		details.add(new TeleportListEditor(entry, onTeleports));

		details.add(caption("Unlock"));
		JComboBox<String> override = new JComboBox<>(OVERRIDES);
		// The listener goes on after the selection is set, so drawing the row doesn't count as a choice.
		LocationStatus status = entry.getStatus();
		override.setSelectedIndex(!status.isOverridden() ? 0
			: status.getAccess() == LocationStatus.Access.UNLOCKED ? 1 : 2);
		override.addActionListener(e ->
		{
			int chosen = override.getSelectedIndex();
			onOverride.accept(chosen == 0 ? null : chosen == 1);
		});
		details.add(fit(override));

		details.add(caption("Patches"));
		for (LocationsView.PatchLine patch : entry.getPatches())
		{
			details.add(patch.isUsable() ? wrapped("✓ " + patch.getName(), Palette.FULL)
				: wrapped("✗ " + patch.getName() + ": " + patch.getReason(), Palette.NONE));
		}
		if (entry.getNote() != null)
		{
			details.add(caption("Note"));
			details.add(wrapped(entry.getNote(), Palette.SECONDARY_TEXT));
		}
		List<String> hints = entry.getWikiTeleports();
		if (!hints.isEmpty())
		{
			details.add(caption("Wiki suggests"));
			details.add(wrapped(String.join("; ", hints.subList(0, Math.min(3, hints.size()))), Palette.SECONDARY_TEXT));
		}
		return details;
	}

	private static String shortStatus(LocationStatus status)
	{
		switch (status.getAccess())
		{
			case UNLOCKED:
				return status.isOverridden() ? "✓*" : "✓";
			case LOCKED:
				return status.isOverridden() ? "Locked*" : "Locked";
			default:
				return "?";
		}
	}

	private static Color statusColour(LocationStatus status)
	{
		switch (status.getAccess())
		{
			case UNLOCKED:
				return Palette.FULL;
			case LOCKED:
				return Palette.NONE;
			default:
				return Palette.SECONDARY_TEXT;
		}
	}

	private static JLabel caption(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Palette.SECONDARY_TEXT);
		label.setBorder(BorderFactory.createEmptyBorder(6, 0, 1, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	/** Text that wraps onto several lines instead of widening the panel. */
	private static JLabel wrapped(String text, Color colour)
	{
		String html = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		JLabel label = new JLabel("<html><div style='width:" + TEXT_WIDTH + "px'>" + html + "</div></html>");
		label.setForeground(colour);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JComponent fit(JComboBox<?> box)
	{
		box.setBackground(Palette.SURFACE);
		box.setForeground(Palette.TEXT);
		box.setFocusable(false);
		box.setAlignmentX(LEFT_ALIGNMENT);
		box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
		box.setPreferredSize(new Dimension(0, box.getPreferredSize().height));
		return box;
	}
}
