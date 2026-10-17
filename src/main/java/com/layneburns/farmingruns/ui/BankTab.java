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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

/**
 * Bank tab: the seed log, run supplies and tool leprechaun storage (SPEC §2.2). What to show is decided by
 * {@link BankViewBuilder}; this class only draws it, and redraws when your items or the buffers change.
 */
@Singleton
public class BankTab extends TabPanel
{
	private final GameData data;
	private final ItemStore store;
	private final LeprechaunWatcher leprechaun;
	private final FarmingRunsConfig config;
	private final ItemManager itemManager;
	private final JPanel content = new JPanel();
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

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(content, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(top);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
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

	private void redraw()
	{
		redrawQueued.set(false);
		BankView view = BankViewBuilder.build(data, store.ledger(), BufferSettings.from(config),
			leprechaun.getBottomlessBucketUses(), System.currentTimeMillis());

		content.removeAll();
		content.add(text("<small>" + view.getSeenLine() + "</small>"));
		for (BankView.Section section : view.getSections())
		{
			if (section.getRows().isEmpty() && section.getEmptyText() == null)
			{
				continue;
			}
			String title = section.getTitle();
			SectionPanel panel = new SectionPanel(title, collapsed.getOrDefault(title, false),
				state -> collapsed.put(title, state));
			if (section.getRows().isEmpty())
			{
				panel.addRow(text(section.getEmptyText()));
			}
			for (BankView.Row row : section.getRows())
			{
				panel.addRow(new ItemRow(itemManager, row));
			}
			content.add(panel);
		}
		content.revalidate();
		content.repaint();
	}
}
