/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.ui.FarmingRunsPanel;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Entry point. This class only wires the parts together and forwards events to them; the work itself lives in
 * the packages described in PLAN.md §2 (data, state, run, inventory, pathing, ui).
 */
@Slf4j
@PluginDescriptor(
	name = "Farming Runs",
	description = "Plans farming runs: seed log, unlocks, inventory checks, route ordering and Shortest Path guidance",
	tags = {"farming", "herb", "run", "seed", "shortest", "path"}
)
public class FarmingRunsPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private FarmingRunsPanel panel;

	@Inject
	private GameData gameData;

	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		BufferedImage icon = ImageUtil.loadImageResource(FarmingRunsPlugin.class, "icon.png");
		navigationButton = NavigationButton.builder()
			.tooltip("Farming Runs")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		log.debug("[farmrun] started: {} locations, {} patches, {} seeds (patch table from {})",
			gameData.locations().size(), gameData.patches().size(), gameData.seeds().size(), gameData.getRuneliteTag());
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navigationButton);
		navigationButton = null;
		log.debug("[farmrun] stopped");
	}

	/** The game data, loaded and checked once. A broken data file stops the plugin here with a clear message. */
	@Provides
	@Singleton
	GameData provideGameData(Gson gson)
	{
		return new DataLoader(gson).load();
	}

	@Provides
	FarmingRunsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FarmingRunsConfig.class);
	}
}
