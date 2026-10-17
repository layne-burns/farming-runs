/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.run.RunSession;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * The side panel: four tabs (Run, Live, Bank, Locations). It lays the tabs out, and switches to Live when a run
 * starts; each tab is its own class.
 */
@Singleton
public class FarmingRunsPanel extends PluginPanel
{
	@Inject
	FarmingRunsPanel(RunTab runTab, LiveTab liveTab, BankTab bankTab, LocationsTab locationsTab, RunSession session)
	{
		// Not wrapped in RuneLite's scroll pane (each tab scrolls itself), which also means PluginPanel
		// doesn't set its dark background: set it here.
		super(false);
		setBackground(Palette.BACKGROUND);
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(Palette.BACKGROUND);
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		// Four tabs share the width evenly, so none is cut off.
		tabs.setLayout(new GridLayout(1, 0, 4, 0));
		MaterialTab run = new MaterialTab("Run", tabs, runTab);
		MaterialTab live = new MaterialTab("Live", tabs, liveTab);
		tabs.addTab(run);
		tabs.addTab(live);
		tabs.addTab(new MaterialTab("Bank", tabs, bankTab));
		tabs.addTab(new MaterialTab("Locations", tabs, locationsTab));
		tabs.select(run);

		// Starting a run brings up the Live tab (once per run, so you can still look elsewhere during it).
		AtomicLong shownFor = new AtomicLong();
		session.addListener(() ->
		{
			RunSession.ActiveRun active = session.active();
			if (active != null && shownFor.getAndSet(active.getStartedAtMillis()) != active.getStartedAtMillis())
			{
				SwingUtilities.invokeLater(() -> tabs.select(live));
			}
		});

		add(tabs, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}
}
