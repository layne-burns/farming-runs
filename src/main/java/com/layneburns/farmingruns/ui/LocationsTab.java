/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.state.LocationPrefs;
import com.layneburns.farmingruns.state.PatchStates;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.state.UnlockService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.Instant;
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
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
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
	private final PatchStates patchStates;

	private final IconTextField search = new IconTextField();
	private final JButton typesButton = button("Types", "Choose which run types to list");
	private final JButton sortButton = button("Sort", "Order by name or by what's ready first");
	private final ConfigManager configManager;
	private LocationSort sort;
	private final List<JCheckBoxMenuItem> typeItems = new ArrayList<>();
	private final Set<RunType> runTypes = EnumSet.allOf(RunType.class);
	private final JLabel summary = new JLabel();
	private final FitWidthPanel content = new FitWidthPanel();
	/** Which locations are open, by section and location, kept across redraws. */
	private final Set<String> expanded = new HashSet<>();
	private final Map<String, Boolean> collapsed = new HashMap<>();
	private final AtomicBoolean redrawQueued = new AtomicBoolean();
	/** Where the chosen {@link LocationSort} is saved. */
	private static final String SORT_KEY = "locationsSort";

	@Inject
	LocationsTab(GameData data, UnlockService unlocks, LocationPrefs prefs, PlayerInfo player, ClientThread clientThread,
		PatchStates patchStates, ConfigManager configManager)
	{
		this.configManager = configManager;
		this.sort = savedSort(configManager);
		this.data = data;
		this.unlocks = unlocks;
		this.prefs = prefs;
		this.player = player;
		this.clientThread = clientThread;
		this.patchStates = patchStates;

		add(top(), BorderLayout.NORTH);
		add(scrollPane(content), BorderLayout.CENTER);
		unlocks.addListener(this::refresh);
		patchStates.addListener(this::refresh);
		// Ready-in times count down: redraw once a minute.
		new Timer(60_000, e -> refresh()).start();
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

		JPopupMenu sortMenu = sortMenu();
		sortButton.addActionListener(e -> sortMenu.show(sortButton, 0, sortButton.getHeight()));
		JPanel buttons = new JPanel(new BorderLayout(4, 0));
		buttons.setBackground(Palette.BACKGROUND);
		buttons.add(typesButton, BorderLayout.WEST);
		buttons.add(sortButton, BorderLayout.EAST);

		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setBackground(Palette.BACKGROUND);
		searchRow.add(search, BorderLayout.CENTER);
		searchRow.add(buttons, BorderLayout.EAST);

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

	/** One choice per {@link LocationSort}; the choice is saved. */
	private JPopupMenu sortMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		ButtonGroup group = new ButtonGroup();
		for (LocationSort choice : LocationSort.values())
		{
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(choice.getTitle(), choice == sort);
			item.addActionListener(e ->
			{
				sort = choice;
				configManager.setConfiguration(FarmingRunsConfig.GROUP, SORT_KEY, choice.name());
				refresh();
			});
			group.add(item);
			menu.add(item);
		}
		return menu;
	}

	private static LocationSort savedSort(ConfigManager configManager)
	{
		String saved = configManager.getConfiguration(FarmingRunsConfig.GROUP, SORT_KEY);
		try
		{
			return saved == null ? LocationSort.NAME : LocationSort.valueOf(saved);
		}
		catch (IllegalArgumentException e)
		{
			return LocationSort.NAME;
		}
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
		long now = Instant.now().getEpochSecond();
		LocationsContext context = LocationsContext.builder()
			.statusOf(unlocks::status)
			.patchRuleOf(unlocks::patchRuleStatus)
			.teleportsOf(prefs::teleportsFor)
			.customTeleports(l -> prefs.hasCustomTeleports(l.getId()))
			.spellbook(player.getFacts().spellbookName())
			.patchStateOf(p -> patchStates.status(p, now))
			.nowSeconds(now)
			.timeTrackingOn(patchStates.isTimeTrackingOn())
			.build();
		LocationsView view = LocationsViewBuilder.build(data, context, query, Set.copyOf(runTypes), sort);

		summary.setText(view.isTimeTrackingOff()
			? "<html>" + view.getSummary() + "<br>Turn on RuneLite's Time Tracking plugin to see patch states.</html>"
			: view.getSummary());
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
