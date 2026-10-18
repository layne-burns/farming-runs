/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.run.TeleportSuggestions;
import com.layneburns.farmingruns.state.LocationPrefs;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The Run tab's teleport suggestions: for each stop you reached with a teleport other than its first choice, "Make
 * first" (moves it to the top of the stop's list in the Locations tab) or "Dismiss".
 */
final class SuggestionList
{
	private SuggestionList()
	{
	}

	static void fill(SectionPanel section, TeleportSuggestions suggestions, LocationPrefs prefs, GameData data)
	{
		for (TeleportSuggestions.Suggestion suggestion : suggestions.all())
		{
			Location location = data.location(suggestion.getLocationId());
			TeleportChoice choice = data.teleportChoice(suggestion.getRef());
			if (choice == null)
			{
				continue;
			}
			JPanel row = new JPanel();
			row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
			row.setBackground(Palette.SURFACE);
			row.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 4, 0, Palette.BACKGROUND),
				BorderFactory.createEmptyBorder(4, 6, 4, 6)));
			row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			String fare = suggestion.getCoins() > 0 ? String.format(" (and %,d coins on the way)", suggestion.getCoins())
				: "";
			JLabel text = TabPanel.text("You reached <b>" + location.getName() + "</b> with " + choice.displayName()
				+ fare + ". Use it first from now on?");
			text.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			row.add(text);

			JButton first = TabPanel.button("Make first", "Puts it at the top of " + location.getName()
				+ "'s teleports (Locations tab)");
			first.addActionListener(e ->
			{
				List<String> order = new ArrayList<>(prefs.teleportsFor(location));
				order.remove(suggestion.getRef());
				order.add(0, suggestion.getRef());
				prefs.setTeleports(location.getId(), order);
				prefs.setFare(location.getId(), suggestion.getRef(), suggestion.getCoins());
				suggestions.accepted(suggestion);
			});
			JButton dismiss = TabPanel.button("Dismiss", "Keep the list as it is");
			dismiss.addActionListener(e -> suggestions.dismiss(suggestion));
			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
			buttons.setOpaque(false);
			buttons.add(first);
			buttons.add(Box.createHorizontalStrut(6));
			buttons.add(dismiss);
			buttons.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			row.add(buttons);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
			section.addRow(row);
		}
	}
}
