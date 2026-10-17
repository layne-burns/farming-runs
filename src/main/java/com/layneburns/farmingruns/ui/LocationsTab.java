/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.state.LocationPrefs;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.state.UnlockService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.EnumSet;
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
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.components.IconTextField;

/**
 * Locations tab (SPEC §2.1): farming locations grouped by run type (choose which in the "Types" dropdown), with
 * locked ones last. Open a location to see why it's locked or not, change its teleport, or override its unlock.
 * What to show is decided by {@link LocationsViewBuilder}; this class draws it and passes your choices to
 * {@link LocationPrefs}.
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
	private final JButton typesButton = button("Types", "Choose which run types to list");
	private final List<JCheckBoxMenuItem> typeItems = new ArrayList<>();
	private final Set<RunType> runTypes = EnumSet.allOf(RunType.class);
	private final JLabel summary = new JLabel();
	private final FitWidthPanel content = new FitWidthPanel();
	/** Which locations are open, by section and location, kept across redraws. */
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

		JPopupMenu menu = typesMenu();
		typesButton.addActionListener(e -> menu.show(typesButton, 0, typesButton.getHeight()));

		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setBackground(Palette.BACKGROUND);
		searchRow.add(search, BorderLayout.CENTER);
		searchRow.add(typesButton, BorderLayout.EAST);

		JButton recheck = button("Recheck", "Read your quests, diaries and levels again (done automatically at login)");
		recheck.addActionListener(e -> clientThread.invoke(player::refresh));
		summary.setForeground(Palette.SECONDARY_TEXT);
		JPanel summaryRow = new JPanel(new BorderLayout(4, 0));
		summaryRow.setBackground(Palette.BACKGROUND);
		summaryRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
		summaryRow.add(summary, BorderLayout.CENTER);
		summaryRow.add(recheck, BorderLayout.EAST);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(Palette.BACKGROUND);
		top.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		top.add(searchRow, BorderLayout.NORTH);
		top.add(summaryRow, BorderLayout.SOUTH);
		return top;
	}

	/** "All", "None", then a tick box per run type; the menu stays open while ticking. */
	private JPopupMenu typesMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem all = new JMenuItem("All");
		all.addActionListener(e -> setAllTypes(true));
		JMenuItem none = new JMenuItem("None");
		none.addActionListener(e -> setAllTypes(false));
		menu.add(all);
		menu.add(none);
		menu.addSeparator();
		for (RunType type : RunType.values())
		{
			JCheckBoxMenuItem item = new JCheckBoxMenuItem(type.getTitle(), runTypes.contains(type));
			item.putClientProperty("CheckBoxMenuItem.doNotCloseOnMouseClick", true);
			item.addActionListener(e ->
			{
				if (item.isSelected())
				{
					runTypes.add(type);
				}
				else
				{
					runTypes.remove(type);
				}
				typesChanged();
			});
			typeItems.add(item);
			menu.add(item);
		}
		return menu;
	}

	private void setAllTypes(boolean on)
	{
		if (on)
		{
			runTypes.addAll(EnumSet.allOf(RunType.class));
		}
		else
		{
			runTypes.clear();
		}
		typeItems.forEach(item -> item.setSelected(on));
		typesChanged();
	}

	private void typesChanged()
	{
		int count = runTypes.size();
		typesButton.setText(count == RunType.values().length ? "Types" : "Types (" + count + ")");
		refresh();
	}

	private void redraw()
	{
		redrawQueued.set(false);
		String query = search.getText().trim();
		LocationsContext context = LocationsContext.builder()
			.statusOf(unlocks::status)
			.patchRuleOf(unlocks::patchRuleStatus)
			.teleportsOf(prefs::teleportsFor)
			.customTeleports(l -> prefs.hasCustomTeleports(l.getId()))
			.spellbook(player.getFacts().spellbookName())
			.build();
		LocationsView view = LocationsViewBuilder.build(data, context, query, Set.copyOf(runTypes));

		summary.setText(view.getSummary());
		content.removeAll();
		for (LocationsView.Section section : view.getSections())
		{
			content.add(sectionPanel(section, !query.isEmpty()));
		}
		if (view.getSections().isEmpty())
		{
			content.add(text(runTypes.isEmpty() ? "No run types chosen: pick some under Types."
				: "No location matches \"" + query + "\"."));
		}
		revalidate();
		repaint();
	}

	private SectionPanel sectionPanel(LocationsView.Section section, boolean searching)
	{
		String key = section.getKey();
		SectionPanel panel = new SectionPanel(section.getTitle(), !searching && collapsed.getOrDefault(key, false),
			state -> collapsed.put(key, state));
		for (LocationsView.Entry entry : section.getEntries())
		{
			String id = entry.getId();
			// Keyed by section too: opening Catherby under Herb leaves it closed under Allotment.
			String openKey = key + "/" + id;
			panel.addRow(new LocationRow(entry, expanded.contains(openKey),
				() ->
				{
					if (!expanded.remove(openKey))
					{
						expanded.add(openKey);
					}
					refresh();
				},
				order -> prefs.setTeleports(id, order),
				unlocked -> prefs.setOverride(id, unlocked)));
		}
		return panel;
	}
}
