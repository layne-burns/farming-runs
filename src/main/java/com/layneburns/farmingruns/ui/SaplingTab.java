/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.inventory.RunCheckService;
import com.layneburns.farmingruns.state.ItemStore;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;

/**
 * Saplings tab: sapling making, apart from a run. With it on, the bank's "Sapling Items" button, the bank tab, the
 * highlights and the seed vault filter show what grows the saplings your next run (as set up in the Run tab) lacks:
 * the tree seeds, filled plant pots, a trowel and a watering can. The kit is worked out with the run check
 * ({@link RunCheckService#saplingKit()}). Swing thread.
 */
@Singleton
public class SaplingTab extends TabPanel
{
	private final RunCheckService checks;
	private final FarmingRunsConfig config;
	private final ConfigManager configManager;
	private final RunTab runTab;
	private final BankRunTab bankRunTab;
	private final FitWidthPanel content = new FitWidthPanel();
	private final AtomicBoolean redrawQueued = new AtomicBoolean();

	@Inject
	SaplingTab(RunCheckService checks, FarmingRunsConfig config, ConfigManager configManager, RunTab runTab,
		BankRunTab bankRunTab, ItemStore items)
	{
		this.checks = checks;
		this.config = config;
		this.configManager = configManager;
		this.runTab = runTab;
		this.bankRunTab = bankRunTab;
		add(scrollPane(content), BorderLayout.CENTER);
		checks.addListener(this::refresh);
		items.addListener(this::refresh);
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

	private void redraw()
	{
		redrawQueued.set(false);
		content.removeAll();
		SectionPanel section = new SectionPanel("Sapling making", false, state ->
		{
		});
		JCheckBox on = new JCheckBox("Sapling making mode", config.saplingMode());
		on.setFocusable(false);
		on.setOpaque(false);
		on.addActionListener(e ->
		{
			configManager.setConfiguration(FarmingRunsConfig.GROUP, FarmingRunsConfig.SAPLING_MODE, on.isSelected());
			// The run check works the kit out; then this redraws with it.
			runTab.refresh();
			refresh();
		});
		section.addRow(on);
		section.addRow(text("While it's on, the bank's search button reads \"Sapling Items\" and brings up what grows"
			+ " the saplings your next run lacks (tree kinds from the Run tab), the seed vault shows just those seeds,"
			+ " and the run's own items step aside."));
		if (config.saplingMode())
		{
			List<String> kit = checks.saplingKit();
			section.addRow(text(kit.isEmpty() ? "No saplings to grow: you have them for your next run."
				: "<html><b>To grow</b><br>" + String.join("<br>", kit) + "</html>"));
			if (!kit.isEmpty())
			{
				JButton show = button("Show in bank", "Opens a bank tab with just the sapling items");
				show.addActionListener(e -> bankRunTab.show());
				JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
				row.setOpaque(false);
				row.add(show);
				section.addRow(row);
			}
		}
		content.add(section);
		revalidate();
		repaint();
	}
}
