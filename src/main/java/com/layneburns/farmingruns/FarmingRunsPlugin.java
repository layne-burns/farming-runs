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
import com.layneburns.farmingruns.state.ContainerWatcher;
import com.layneburns.farmingruns.state.ContractService;
import com.layneburns.farmingruns.state.ItemStore;
import com.layneburns.farmingruns.state.LeprechaunWatcher;
import com.layneburns.farmingruns.state.LocationPrefs;
import com.layneburns.farmingruns.state.PatchStates;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.state.RunePouchWatcher;
import com.layneburns.farmingruns.state.SetupStore;
import com.layneburns.farmingruns.run.RunController;
import com.layneburns.farmingruns.ui.BankHighlightOverlay;
import com.layneburns.farmingruns.ui.BankRunTab;
import com.layneburns.farmingruns.ui.BankSearchButton;
import com.layneburns.farmingruns.ui.BankTab;
import com.layneburns.farmingruns.ui.FarmingRunsPanel;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

/**
 * Entry point. This class only wires the parts together and forwards events to them; the work itself lives in
 * the packages described in PLAN.md §2 (data, state, run, inventory, pathing, ui).
 */
@Slf4j
@PluginDependency(BankTagsPlugin.class)
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
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private GameData gameData;

	@Inject
	private FarmingRunsPanel panel;

	@Inject
	private BankTab bankTab;

	@Inject
	private ItemStore itemStore;

	@Inject
	private ContainerWatcher containerWatcher;

	@Inject
	private LeprechaunWatcher leprechaunWatcher;

	@Inject
	private PlayerInfo playerInfo;

	@Inject
	private LocationPrefs locationPrefs;

	@Inject
	private PatchStates patchStates;

	@Inject
	private ContractService contractService;

	@Inject
	private SetupStore setupStore;

	@Inject
	private RunePouchWatcher runePouchWatcher;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BankHighlightOverlay bankHighlightOverlay;

	@Inject
	private BankRunTab bankRunTab;

	@Inject
	private BankSearchButton bankSearchButton;

	@Inject
	private RunController runController;

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
		overlayManager.add(bankHighlightOverlay);

		eventBus.register(itemStore);
		eventBus.register(containerWatcher);
		eventBus.register(leprechaunWatcher);
		eventBus.register(playerInfo);
		eventBus.register(locationPrefs);
		eventBus.register(patchStates);
		eventBus.register(contractService);
		eventBus.register(setupStore);
		eventBus.register(runePouchWatcher);
		eventBus.register(bankRunTab);
		eventBus.register(bankSearchButton);
		eventBus.register(runController);
		bankRunTab.register();
		itemStore.loadRemembered();
		locationPrefs.load();
		setupStore.load();
		// If the plugin starts while logged in, read what the client already has.
		clientThread.invokeLater(() ->
		{
			containerWatcher.readCurrent();
			leprechaunWatcher.refresh();
			playerInfo.refresh();
			contractService.refresh();
			runePouchWatcher.refresh();
		});

		log.debug("[farmrun] started: {} locations, {} patches, {} seeds (patch table from {})",
			gameData.locations().size(), gameData.patches().size(), gameData.seeds().size(),
			gameData.getRuneliteTag());
	}

	@Override
	protected void shutDown()
	{
		runController.stop();
		eventBus.unregister(runController);
		bankSearchButton.hide();
		eventBus.unregister(bankSearchButton);
		bankRunTab.unregister();
		eventBus.unregister(bankRunTab);
		eventBus.unregister(runePouchWatcher);
		eventBus.unregister(setupStore);
		eventBus.unregister(contractService);
		eventBus.unregister(patchStates);
		eventBus.unregister(locationPrefs);
		eventBus.unregister(playerInfo);
		eventBus.unregister(leprechaunWatcher);
		eventBus.unregister(containerWatcher);
		eventBus.unregister(itemStore);
		overlayManager.remove(bankHighlightOverlay);
		clientToolbar.removeNavigation(navigationButton);
		navigationButton = null;
		log.debug("[farmrun] stopped");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (FarmingRunsConfig.GROUP.equals(event.getGroup()))
		{
			bankTab.refresh();
		}
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
