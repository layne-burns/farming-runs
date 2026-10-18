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
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.run.LearnedCosts;
import com.layneburns.farmingruns.run.LegTimer;
import com.layneburns.farmingruns.run.TravelSamples;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Your recent travel and work times on runs ({@link TravelSamples}), saved with your RuneScape profile so runs are
 * planned with how long things take you ({@link LearnedCosts}). Loaded for the account you're on, and never written
 * before the account is known (see {@link StatsStore}).
 */
@Slf4j
@Singleton
public class TravelLog
{
	private static final String KEY = "travelSamples";

	private final ConfigManager configManager;
	private final Gson gson;
	private final GameData data;
	private volatile TravelSamples samples = TravelSamples.NONE;
	/** The account these were loaded for, or null before login. */
	private volatile String loadedFor;
	private volatile boolean everLoaded;

	@Inject
	TravelLog(ConfigManager configManager, Gson gson, GameData data)
	{
		this.configManager = configManager;
		this.gson = gson;
		this.data = data;
	}

	/** Route costs and work times for planning: your own where there are enough, the hand table's otherwise. */
	public LearnedCosts costs()
	{
		ensureLoaded();
		return new LearnedCosts(data.getCosts()::cost, samples);
	}

	/** Adds a finished stop's times (unless they can't be right). Client thread. */
	public void record(LegTimer.Leg leg)
	{
		ensureLoaded();
		TravelSamples before = samples;
		TravelSamples updated = before.record(leg);
		boolean travelKept = changed(before.getTravel(), updated.getTravel())
			|| changed(before.getWalks(), updated.getWalks());
		boolean workKept = changed(before.getWork(), updated.getWork());
		log.debug("[farmrun] learned from leg {}: travel {}, work {}", leg.getLocationId(),
			travelKept ? "kept" : "left out", workKept ? "kept" : "left out");
		if (!travelKept && !workKept)
		{
			return;
		}
		samples = updated;
		if (loadedFor != null)
		{
			configManager.setRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY, gson.toJson(updated));
		}
	}

	private void load()
	{
		loadedFor = configManager.getRSProfileKey();
		everLoaded = true;
		TravelSamples loaded = TravelSamples.NONE;
		String json = configManager.getRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY);
		if (json != null)
		{
			try
			{
				TravelSamples read = gson.fromJson(json, TravelSamples.class);
				if (read != null)
				{
					loaded = new TravelSamples(orEmpty(read.getTravel()), orEmpty(read.getWalks()),
						orEmpty(read.getWork()));
				}
			}
			catch (JsonParseException e)
			{
				log.warn("[farmrun] couldn't read saved travel times", e);
			}
		}
		samples = loaded;
	}

	/** Loads on first use and again whenever you've switched account. */
	private void ensureLoaded()
	{
		if (!everLoaded || !Objects.equals(configManager.getRSProfileKey(), loadedFor))
		{
			load();
		}
	}

	private static boolean changed(Map<String, List<Integer>> before, Map<String, List<Integer>> after)
	{
		return !before.equals(after);
	}

	/** Older or partial saves may lack a kind of time. */
	private static Map<String, List<Integer>> orEmpty(Map<String, List<Integer>> samples)
	{
		return samples == null ? Map.of() : samples;
	}
}
