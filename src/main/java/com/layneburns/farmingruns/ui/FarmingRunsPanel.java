/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.BorderLayout;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * The side panel: three tabs (Run, Bank, Locations). It only lays the tabs out; each tab is its own class.
 */
@Singleton
public class FarmingRunsPanel extends PluginPanel
{
	@Inject
	FarmingRunsPanel(RunTab runTab, BankTab bankTab, LocationsTab locationsTab)
	{
		// Not wrapped in RuneLite's scroll pane (each tab scrolls itself), which also means PluginPanel
		// doesn't set its dark background: set it here.
		super(false);
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		MaterialTab run = new MaterialTab("Run", tabs, runTab);
		tabs.addTab(run);
		tabs.addTab(new MaterialTab("Bank", tabs, bankTab));
		tabs.addTab(new MaterialTab("Locations", tabs, locationsTab));
		tabs.select(run);

		add(tabs, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}
}
