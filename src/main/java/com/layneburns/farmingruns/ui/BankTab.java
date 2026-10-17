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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Bank tab: the seed log, run supplies and tool leprechaun storage (SPEC §2.2). Browse by page (Seeds / Supplies /
 * Leprechaun) or type in the search box to find items on every page at once. What to show is decided by
 * {@link BankViewBuilder} and {@link BankView#search}; this class only draws it, and redraws when your items, the
 * buffers, the page or the search change.
 */
@Singleton
public class BankTab extends TabPanel
{
	private final GameData data;
	private final ItemStore store;
	private final LeprechaunWatcher leprechaun;
	private final FarmingRunsConfig config;
	private final ItemManager itemManager;

	private final IconTextField search = new IconTextField();
	private final JPanel seenLine = new JPanel(new GridLayout(1, 3));
	private final FitWidthPanel content = new FitWidthPanel();
	private BankView.Page page = BankView.Page.SEEDS;
	/** Which sections are collapsed, by title, kept across redraws. */
	private final Map<String, Boolean> collapsed = new HashMap<>();
	/** Set while a redraw is queued, so a burst of item changes draws once. */
	private final AtomicBoolean redrawQueued = new AtomicBoolean();

	@Inject
	BankTab(GameData data, ItemStore store, LeprechaunWatcher leprechaun, FarmingRunsConfig config,
		ItemManager itemManager)
	{
		this.data = data;
		this.store = store;
		this.leprechaun = leprechaun;
		this.config = config;
		this.itemManager = itemManager;

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(searchBox());
		top.add(pageSwitch());
		seenLine.setBackground(ColorScheme.DARK_GRAY_COLOR);
		seenLine.setToolTipText("When the bank, seed vault and seed box were last opened. Their contents are only "
			+ "visible while open, so the log uses what it saw then.");
		top.add(seenLine);
		add(top, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(scroll, BorderLayout.CENTER);

		store.addListener(this::refresh);
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

	private JPanel searchBox()
	{
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.setPreferredSize(new Dimension(0, 30));
		search.setToolTipText("Search every page: an item name or a patch kind (e.g. \"ranarr\", \"fruit\")");
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
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
		wrapper.add(search, BorderLayout.CENTER);
		return wrapper;
	}

	private MaterialTabGroup pageSwitch()
	{
		MaterialTabGroup tabs = new MaterialTabGroup();
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
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
		BankView view = BankViewBuilder.build(data, store.ledger(), BufferSettings.from(config),
			leprechaun.getBottomlessBucketUses(), System.currentTimeMillis());

		seenLine.removeAll();
		for (String seen : view.getSeen())
		{
			JLabel label = new JLabel(seen, JLabel.CENTER);
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			seenLine.add(label);
		}

		String query = search.getText().trim();
		List<BankView.Section> sections = query.isEmpty() ? view.page(page) : view.search(query);

		content.removeAll();
		if (sections.isEmpty())
		{
			content.add(text("Nothing matches \"" + query + "\"."));
		}
		for (BankView.Section section : sections)
		{
			content.add(sectionPanel(section, !query.isEmpty()));
		}
		revalidate();
		repaint();
	}

	private SectionPanel sectionPanel(BankView.Section section, boolean searching)
	{
		String title = section.getTitle();
		// While searching, every section with a match is open, whatever it was before.
		SectionPanel panel = new SectionPanel(title, !searching && collapsed.getOrDefault(title, false),
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
