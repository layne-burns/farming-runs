/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.inventory.RunCheckService;
import com.layneburns.farmingruns.state.ItemStore;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;

/**
 * Saplings tab: sapling making, apart from a run (runs grow the saplings they need by themselves). With it on, the
 * bank's "Sapling Items" button, the bank tab, the highlights and the seed vault filter show what pots every tree seed
 * you have: the seeds, filled plant pots (an inventory's worth), a trowel and a watering can. The kit is worked out with the run check
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

	/** Leaves a seed out of sapling making (or puts it back), and works the kit out again. */
	private void setSkipped(String seedId, boolean skip)
	{
		Set<String> skipped = new TreeSet<>(checks.saplingSkipped());
		if (skip)
		{
			skipped.add(seedId);
		}
		else
		{
			skipped.remove(seedId);
		}
		configManager.setConfiguration(FarmingRunsConfig.GROUP, FarmingRunsConfig.SAPLING_SKIPPED,
			String.join(",", skipped));
		runTab.refresh();
		refresh();
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
		section.addRow(text("Pot every tree seed you have. While it's on, the bank's search button reads \"Sapling"
			+ " Items\" and brings up the seeds, filled plant pots (an inventory's worth), a trowel and 3 watering"
			+ " cans; the seed vault shows just those seeds, and the run's own items step aside."));
		content.add(section);

		// A tick box per tree seed you have: unticked ones are left out (e.g. oak, until you want oak saplings).
		Map<Seed, Integer> held = checks.saplingSeedsHeld();
		if (!held.isEmpty())
		{
			SectionPanel seeds = new SectionPanel("Seeds to pot", false, state ->
			{
			});
			Set<String> skipped = checks.saplingSkipped();
			held.forEach((seed, count) ->
			{
				String id = String.valueOf(seed.getItemId());
				JCheckBox box = new JCheckBox(count + " × " + seed.getName(), !skipped.contains(id));
				box.setFocusable(false);
				box.setOpaque(false);
				box.addActionListener(e -> setSkipped(id, !box.isSelected()));
				seeds.addRow(box);
			});
			content.add(seeds);
		}

		if (config.saplingMode())
		{
			SectionPanel kitSection = new SectionPanel("To take", false, state ->
			{
			});
			List<String> kit = checks.saplingKit();
			kitSection.addRow(text(kit.isEmpty() ? "No tree seeds to pot."
				: "<html>" + String.join("<br>", kit) + "</html>"));
			if (!kit.isEmpty())
			{
				JButton show = button("Show in bank", "Opens a bank tab with just the sapling items");
				show.addActionListener(e -> bankRunTab.show());
				JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
				row.setOpaque(false);
				row.add(show);
				kitSection.addRow(row);
			}
			content.add(kitSection);
		}
		revalidate();
		repaint();
	}
}
