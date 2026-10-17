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
import com.layneburns.farmingruns.run.RunStats;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

/**
 * Your lifetime farming-run stats, saved with your RuneScape profile like the presets: loaded for the account you're
 * on, and never written before the account is known (so they can't be overwritten with nothing).
 */
@Slf4j
@Singleton
public class StatsStore
{
	private static final String KEY = "runStats";

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile RunStats stats = RunStats.NONE;
	/** The account these were loaded for, or null before login (see SetupStore). */
	private volatile String loadedFor;
	private volatile boolean everLoaded;

	@Inject
	StatsStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public RunStats stats()
	{
		ensureLoaded();
		return stats;
	}

	/** Saves new stats for this account (kept for the session only when not logged in). */
	public void save(RunStats updated)
	{
		ensureLoaded();
		stats = updated;
		if (loadedFor != null)
		{
			configManager.setRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY, gson.toJson(updated));
		}
		listeners.forEach(Runnable::run);
	}

	public void load()
	{
		loadedFor = configManager.getRSProfileKey();
		everLoaded = true;
		RunStats loaded = RunStats.NONE;
		String json = configManager.getRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY);
		if (json != null)
		{
			try
			{
				RunStats read = gson.fromJson(json, RunStats.class);
				if (read != null)
				{
					// Older or partial saves may lack the map.
					loaded = new RunStats(read.getRuns(), read.getPatchesPlanted(), read.getXp(), read.getMillis(),
						read.getFastestMillis(), read.getBestAtOneStop() == null ? Map.of() : Map.copyOf(read.getBestAtOneStop()));
				}
			}
			catch (JsonParseException e)
			{
				log.warn("[farmrun] couldn't read saved run stats", e);
			}
		}
		stats = loaded;
		listeners.forEach(Runnable::run);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		load();
	}

	private void ensureLoaded()
	{
		if (!everLoaded || !Objects.equals(configManager.getRSProfileKey(), loadedFor))
		{
			load();
		}
	}
}
