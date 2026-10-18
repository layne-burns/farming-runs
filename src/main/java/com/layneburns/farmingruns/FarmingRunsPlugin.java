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
import com.layneburns.farmingruns.state.PatchObjects;
import com.layneburns.farmingruns.state.SetupStore;
import com.layneburns.farmingruns.state.StatsStore;
import com.layneburns.farmingruns.state.TeleportLimits;
import com.layneburns.farmingruns.run.GuidanceTracker;
import com.layneburns.farmingruns.run.RunController;
import com.layneburns.farmingruns.run.KeepStandingGuard;
import com.layneburns.farmingruns.run.LegTimer;
import com.layneburns.farmingruns.run.PatchClicks;
import com.layneburns.farmingruns.run.RunTracker;
import com.layneburns.farmingruns.run.PineappleErrand;
import com.layneburns.farmingruns.run.TeleportFallback;
import com.layneburns.farmingruns.run.TeleportWatcher;
import com.layneburns.farmingruns.ui.BankHighlightOverlay;
import com.layneburns.farmingruns.ui.BankRunTab;
import com.layneburns.farmingruns.ui.BankSearchButton;
import com.layneburns.farmingruns.ui.SeedVaultFilter;
import com.layneburns.farmingruns.ui.BankTab;
import com.layneburns.farmingruns.ui.DepositOverlay;
import com.layneburns.farmingruns.ui.PatchOutlineOverlay;
import com.layneburns.farmingruns.ui.ReminderOverlay;
import com.layneburns.farmingruns.ui.TeleportMenuOverlay;
import com.layneburns.farmingruns.ui.GardenerOverlay;
import com.layneburns.farmingruns.ui.RunItemOverlay;
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
	private FarmingRunsConfig config;

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
	private SeedVaultFilter seedVaultFilter;

	@Inject
	private RunController runController;

	@Inject
	private TeleportWatcher teleportWatcher;

	@Inject
	private GuidanceTracker guidanceTracker;

	@Inject
	private RunTracker runTracker;

	@Inject
	private LegTimer legTimer;

	@Inject
	private PatchClicks patchClicks;

	@Inject
	private KeepStandingGuard keepStandingGuard;

	@Inject
	private StatsStore statsStore;

	@Inject
	private TeleportLimits teleportLimits;

	/** Not used directly: it listens for used-up teleports once created. */
	@Inject
	private TeleportFallback teleportFallback;

	@Inject
	private RunItemOverlay runItemOverlay;

	@Inject
	private GardenerOverlay gardenerOverlay;

	@Inject
	private DepositOverlay depositOverlay;

	@Inject
	private PatchOutlineOverlay patchOutlineOverlay;

	@Inject
	private ReminderOverlay reminderOverlay;

	@Inject
	private TeleportMenuOverlay teleportMenuOverlay;

	@Inject
	private PineappleErrand pineappleErrand;

	@Inject
	private PatchObjects patchObjects;

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
		overlayManager.add(runItemOverlay);
		overlayManager.add(gardenerOverlay);
		overlayManager.add(depositOverlay);
		overlayManager.add(patchOutlineOverlay);
		overlayManager.add(reminderOverlay);
		overlayManager.add(teleportMenuOverlay);

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
		eventBus.register(seedVaultFilter);
		eventBus.register(runController);
		eventBus.register(teleportWatcher);
		eventBus.register(guidanceTracker);
		eventBus.register(runTracker);
		eventBus.register(legTimer);
		eventBus.register(patchClicks);
		eventBus.register(keepStandingGuard);
		eventBus.register(statsStore);
		eventBus.register(teleportLimits);
		eventBus.register(patchObjects);
		eventBus.register(teleportMenuOverlay);
		eventBus.register(pineappleErrand);
		bankRunTab.register();
		itemStore.loadRemembered();
		locationPrefs.load();
		DebugLogging.apply(config.debugLogging());
		setupStore.load();
		statsStore.load();
		teleportLimits.load();
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
		eventBus.unregister(pineappleErrand);
		pineappleErrand.stop();
		eventBus.unregister(teleportMenuOverlay);
		eventBus.unregister(patchObjects);
		eventBus.unregister(teleportLimits);
		eventBus.unregister(statsStore);
		eventBus.unregister(keepStandingGuard);
		eventBus.unregister(patchClicks);
		eventBus.unregister(legTimer);
		eventBus.unregister(runTracker);
		eventBus.unregister(guidanceTracker);
		eventBus.unregister(teleportWatcher);
		eventBus.unregister(runController);
		bankSearchButton.hide();
		eventBus.unregister(seedVaultFilter);
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
		overlayManager.remove(reminderOverlay);
		overlayManager.remove(teleportMenuOverlay);
		overlayManager.remove(patchOutlineOverlay);
		overlayManager.remove(depositOverlay);
		overlayManager.remove(gardenerOverlay);
		overlayManager.remove(runItemOverlay);
		overlayManager.remove(bankHighlightOverlay);
		clientToolbar.removeNavigation(navigationButton);
		navigationButton = null;
		log.debug("[farmrun] stopped");
		DebugLogging.apply(false);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (FarmingRunsConfig.GROUP.equals(event.getGroup()))
		{
			DebugLogging.apply(config.debugLogging());
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
