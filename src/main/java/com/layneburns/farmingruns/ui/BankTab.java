/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.state.BufferSettings;
import com.layneburns.farmingruns.state.ItemStore;
import com.layneburns.farmingruns.state.LeprechaunWatcher;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.state.UnlockService;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Bank tab: the seed log, run supplies and tool leprechaun storage (SPEC §2.2). Browse by page (Seeds / Supplies /
 * Leprechaun), search every page at once, or filter the seeds. What to show is decided by {@link BankViewBuilder},
 * {@link BankView#search} and {@link BankFilters}; this class only draws it, and redraws when anything changes.
 */
@Singleton
public class BankTab extends TabPanel
{
	private final GameData data;
	private final ItemStore store;
	private final LeprechaunWatcher leprechaun;
	private final PlayerInfo player;
	private final UnlockService unlocks;
	private final FarmingRunsConfig config;
	private final ItemManager itemManager;

	private final BankToolbar toolbar = new BankToolbar(this::refresh);
	private final JPanel seenLine = new JPanel(new GridLayout(1, 3));
	private final FitWidthPanel content = new FitWidthPanel();
	private BankView.Page page = BankView.Page.SEEDS;
	/** Which sections are collapsed, by title, kept across redraws. */
	private final Map<String, Boolean> collapsed = new HashMap<>();
	/** Set while a redraw is queued, so a burst of item changes draws once. */
	private final AtomicBoolean redrawQueued = new AtomicBoolean();

	@Inject
	BankTab(GameData data, ItemStore store, LeprechaunWatcher leprechaun, PlayerInfo player, UnlockService unlocks,
		FarmingRunsConfig config, ItemManager itemManager)
	{
		this.data = data;
		this.store = store;
		this.leprechaun = leprechaun;
		this.player = player;
		this.unlocks = unlocks;
		this.config = config;
		this.itemManager = itemManager;

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(Palette.BACKGROUND);
		top.add(toolbar);
		top.add(pageSwitch());
		seenLine.setBackground(Palette.BACKGROUND);
		seenLine.setToolTipText("When the bank, seed vault and seed box were last opened. Their contents are only "
			+ "visible while open, so the log uses what it saw then.");
		top.add(seenLine);
		add(top, BorderLayout.NORTH);

		add(scrollPane(content), BorderLayout.CENTER);

		store.addListener(this::refresh);
		player.addListener(this::refresh);
		unlocks.addListener(this::refresh);
		refresh();
	}

	/** Redraws soon, on the Swing thread. Safe to call from any thread, as often as you like. */
	public void refresh()
	{
		if (redrawQueued.compareAndSet(false, true))
		{
			SwingUtilities.invokeLater(this::redraw);
		}
	}

	private MaterialTabGroup pageSwitch()
	{
		MaterialTabGroup tabs = new MaterialTabGroup();
		tabs.setBackground(Palette.BACKGROUND);
		// One equal column per page, so all three always fit on one line (the default flow layout wraps).
		tabs.setLayout(new GridLayout(1, BankView.Page.values().length));
		MaterialTab first = null;
		for (BankView.Page p : BankView.Page.values())
		{
			MaterialTab tab = new MaterialTab(p.getTitle(), tabs, null);
			tab.setOnSelectEvent(() ->
			{
				page = p;
				refresh();
				return true;
			});
			tabs.addTab(tab);
			first = first == null ? tab : first;
		}
		tabs.select(first);
		return tabs;
	}

	private void redraw()
	{
		redrawQueued.set(false);
		BankView view = BankViewBuilder.build(data, store.ledger(), BufferSettings.from(config, data),
			unlocks.usablePatchCounts(), leprechaun.getBottomlessBucketUses(), System.currentTimeMillis());

		seenLine.removeAll();
		for (String seen : view.getSeen())
		{
			JLabel label = new JLabel(seen, JLabel.CENTER);
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(Palette.SECONDARY_TEXT);
			seenLine.add(label);
		}

		String query = toolbar.query();
		BankFilters filters = toolbar.filters(player.getFarmingLevel());
		List<BankView.Section> sections = filters.apply(query.isEmpty() ? view.page(page) : view.search(query));

		content.removeAll();
		if (sections.isEmpty())
		{
			content.add(text(query.isEmpty() ? "No seeds match the filters." : "Nothing matches \"" + query + "\"."));
		}
		for (BankView.Section section : sections)
		{
			content.add(sectionPanel(section, !query.isEmpty() || filters.activeCount() > 0));
		}
		revalidate();
		repaint();
	}

	/**
	 * @param narrowed true while searching or filtering: every section shown is opened, whatever it was before
	 */
	private SectionPanel sectionPanel(BankView.Section section, boolean narrowed)
	{
		String title = section.getTitle();
		SectionPanel panel = new SectionPanel(title, !narrowed && collapsed.getOrDefault(title, false),
			state -> collapsed.put(title, state));
		if (section.isEmpty())
		{
			panel.addRow(text(section.getEmptyText()));
		}
		for (BankView.Group group : section.getGroups())
		{
			if (group.getRows().isEmpty())
			{
				continue;
			}
			if (group.getTitle() != null)
			{
				panel.addHeading(group.getTitle());
			}
			for (BankView.Row row : group.getRows())
			{
				panel.addRow(new ItemRow(itemManager, row));
			}
		}
		return panel;
	}
}
