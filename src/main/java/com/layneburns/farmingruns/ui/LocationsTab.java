/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.state.LocationPrefs;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.state.UnlockService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.components.IconTextField;

/**
 * Locations tab (SPEC §2.1): every farming location, whether you've unlocked it and why, the teleport it uses
 * (changeable), and an override for when the rules get it wrong. What to show is decided by
 * {@link LocationsViewBuilder}; this class draws it and passes your choices to {@link LocationPrefs}.
 */
@Singleton
public class LocationsTab extends TabPanel
{
	private final GameData data;
	private final UnlockService unlocks;
	private final LocationPrefs prefs;
	private final PlayerInfo player;
	private final ClientThread clientThread;

	private final IconTextField search = new IconTextField();
	private final JLabel summary = new JLabel();
	private final FitWidthPanel content = new FitWidthPanel();
	/** Which locations are open, kept across redraws. */
	private final Set<String> expanded = new HashSet<>();
	private final Map<String, Boolean> collapsed = new HashMap<>();
	private final AtomicBoolean redrawQueued = new AtomicBoolean();

	@Inject
	LocationsTab(GameData data, UnlockService unlocks, LocationPrefs prefs, PlayerInfo player, ClientThread clientThread)
	{
		this.data = data;
		this.unlocks = unlocks;
		this.prefs = prefs;
		this.player = player;
		this.clientThread = clientThread;

		add(top(), BorderLayout.NORTH);
		add(scrollPane(content), BorderLayout.CENTER);
		unlocks.addListener(this::refresh);
		refresh();
	}

	/** Redraws soon, on the Swing thread. Safe to call from any thread. */
	public void refresh()
	{
		if (redrawQueued.compareAndSet(false, true))
		{
			SwingUtilities.invokeLater(this::redraw);
		}
	}

	private JPanel top()
	{
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setBackground(Palette.SURFACE);
		search.setHoverBackgroundColor(Palette.SURFACE_HOVER);
		search.setPreferredSize(new Dimension(0, 30));
		search.setToolTipText("Search by location name or patch kind (e.g. \"guild\", \"herb\")");
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refresh();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refresh();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refresh();
			}
		});
		search.addClearListener(this::refresh);

		JButton recheck = button("Recheck", "Read your quests, diaries and levels again (done automatically at login)");
		recheck.addActionListener(e -> clientThread.invoke(player::refresh));

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Palette.BACKGROUND);
		row.add(search, BorderLayout.CENTER);
		row.add(recheck, BorderLayout.EAST);

		summary.setForeground(Palette.SECONDARY_TEXT);
		summary.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(Palette.BACKGROUND);
		top.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		top.add(row, BorderLayout.NORTH);
		top.add(summary, BorderLayout.SOUTH);
		return top;
	}

	private void redraw()
	{
		redrawQueued.set(false);
		String query = search.getText().trim();
		LocationsView view = LocationsViewBuilder.build(data, unlocks::status, unlocks::patchRuleStatus,
			prefs::teleportFor, player.getFacts().spellbookName(), query);

		summary.setText(view.getSummary());
		content.removeAll();
		addSection("Locked", view.getLocked(), !query.isEmpty());
		addSection("Unlocked", view.getUnlocked(), !query.isEmpty());
		if (view.getLocked().isEmpty() && view.getUnlocked().isEmpty())
		{
			content.add(text("No location matches \"" + query + "\"."));
		}
		revalidate();
		repaint();
	}

	private void addSection(String title, List<LocationsView.Entry> entries, boolean searching)
	{
		if (entries.isEmpty())
		{
			return;
		}
		String heading = title + " (" + entries.size() + ")";
		SectionPanel section = new SectionPanel(heading, !searching && collapsed.getOrDefault(title, false),
			state -> collapsed.put(title, state));
		for (LocationsView.Entry entry : entries)
		{
			String id = entry.getId();
			section.addRow(new LocationRow(entry, expanded.contains(id),
				() ->
				{
					if (!expanded.remove(id))
					{
						expanded.add(id);
					}
					refresh();
				},
				teleportId -> prefs.setTeleport(id, teleportId),
				unlocked -> prefs.setOverride(id, unlocked)));
		}
		content.add(section);
	}
}
