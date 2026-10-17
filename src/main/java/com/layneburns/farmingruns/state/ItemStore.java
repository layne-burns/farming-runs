/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.layneburns.farmingruns.FarmingRunsConfig;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

/**
 * Where the plugin keeps track of your items: the current {@link ItemLedger}, kept up to date by the watchers.
 * Bank, seed vault and seed box contents are saved to your RuneScape profile (they are only visible while open)
 * and loaded again when you log in. Listeners (the panel) are told after every change.
 */
@Slf4j
@Singleton
public class ItemStore
{
	private static final String KEY_PREFIX = "snapshot.";

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile ItemLedger ledger = ItemLedger.EMPTY;

	@Inject
	ItemStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public ItemLedger ledger()
	{
		return ledger;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Runnable listener)
	{
		listeners.remove(listener);
	}

	/** Records what a watcher just read. Unchanged live contents are ignored, so the panel isn't redrawn for nothing. */
	public void update(ItemSource source, StorageSnapshot snapshot)
	{
		StorageSnapshot previous = ledger.snapshot(source);
		if (!source.isRemembered() && previous.isSeen() && previous.getItems().equals(snapshot.getItems()))
		{
			return;
		}
		ledger = ledger.with(source, snapshot);
		if (source.isRemembered())
		{
			configManager.setRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY_PREFIX + source.name(),
				gson.toJson(new Saved(snapshot)));
		}
		notifyListeners();
	}

	/** Loads the remembered sources for the current profile and forgets the live ones. */
	public void loadRemembered()
	{
		ItemLedger fresh = ItemLedger.EMPTY;
		for (ItemSource source : ItemSource.values())
		{
			if (source.isRemembered())
			{
				fresh = fresh.with(source, read(source));
			}
		}
		ledger = fresh;
		log.debug("[seedlog] loaded saved bank {}, vault {}, seed box {}",
			fresh.snapshot(ItemSource.BANK).isSeen(), fresh.snapshot(ItemSource.SEED_VAULT).isSeen(),
			fresh.snapshot(ItemSource.SEED_BOX).isSeen());
		notifyListeners();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadRemembered();
	}

	private StorageSnapshot read(ItemSource source)
	{
		String json = configManager.getRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY_PREFIX + source.name());
		if (json == null)
		{
			return StorageSnapshot.UNSEEN;
		}
		try
		{
			Saved saved = gson.fromJson(json, Saved.class);
			return new StorageSnapshot(saved.items == null ? Map.of() : saved.items, saved.capturedAtMillis);
		}
		catch (JsonParseException e)
		{
			log.warn("[seedlog] couldn't read saved {}; it will refresh next time it's opened", source, e);
			return StorageSnapshot.UNSEEN;
		}
	}

	private void notifyListeners()
	{
		for (Runnable listener : listeners)
		{
			listener.run();
		}
	}

	/** The saved form of a snapshot, as JSON in the profile. */
	private static class Saved
	{
		long capturedAtMillis;
		Map<Integer, Integer> items;

		Saved(StorageSnapshot snapshot)
		{
			this.capturedAtMillis = snapshot.getCapturedAtMillis();
			this.items = snapshot.getItems();
		}
	}
}
