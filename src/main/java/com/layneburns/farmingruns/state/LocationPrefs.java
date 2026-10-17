/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.Location;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

/**
 * Your choices per location, saved to your RuneScape profile: an unlock override (force unlocked or locked when the
 * rules get it wrong) and the teleport to use instead of the default. Listeners are told after every change.
 */
@Slf4j
@Singleton
public class LocationPrefs
{
	private static final String OVERRIDES_KEY = "unlockOverrides";
	private static final String TELEPORTS_KEY = "teleportChoices";
	private static final Type BOOLEAN_MAP = new TypeToken<Map<String, Boolean>>()
	{
	}.getType();
	private static final Type STRING_MAP = new TypeToken<Map<String, String>>()
	{
	}.getType();

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile Map<String, Boolean> overrides = Map.of();
	private volatile Map<String, String> teleports = Map.of();

	@Inject
	LocationPrefs(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	/** True or false if you've forced the location unlocked or locked; null to follow the rules. */
	@Nullable
	public Boolean override(String locationId)
	{
		return overrides.get(locationId);
	}

	/** @param unlocked true or false to force it, null to go back to the rules */
	public void setOverride(String locationId, @Nullable Boolean unlocked)
	{
		Map<String, Boolean> copy = new HashMap<>(overrides);
		if (unlocked == null)
		{
			copy.remove(locationId);
		}
		else
		{
			copy.put(locationId, unlocked);
		}
		overrides = Map.copyOf(copy);
		save(OVERRIDES_KEY, overrides);
	}

	/** The teleport id to use for a location: your choice, or the location's default. */
	public String teleportFor(Location location)
	{
		return teleports.getOrDefault(location.getId(), location.getDefaultTeleportId());
	}

	/** @param teleportId the teleport to use, or null to go back to the default */
	public void setTeleport(String locationId, @Nullable String teleportId)
	{
		Map<String, String> copy = new HashMap<>(teleports);
		if (teleportId == null)
		{
			copy.remove(locationId);
		}
		else
		{
			copy.put(locationId, teleportId);
		}
		teleports = Map.copyOf(copy);
		save(TELEPORTS_KEY, teleports);
	}

	/** Loads the saved choices for the current profile. */
	public void load()
	{
		overrides = read(OVERRIDES_KEY, BOOLEAN_MAP);
		teleports = read(TELEPORTS_KEY, STRING_MAP);
		listeners.forEach(Runnable::run);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		load();
	}

	private void save(String key, Map<String, ?> value)
	{
		if (value.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(FarmingRunsConfig.GROUP, key);
		}
		else
		{
			configManager.setRSProfileConfiguration(FarmingRunsConfig.GROUP, key, gson.toJson(value));
		}
		listeners.forEach(Runnable::run);
	}

	private <T> Map<String, T> read(String key, Type type)
	{
		String json = configManager.getRSProfileConfiguration(FarmingRunsConfig.GROUP, key);
		if (json == null)
		{
			return Map.of();
		}
		try
		{
			Map<String, T> map = gson.fromJson(json, type);
			return map == null ? Map.of() : Map.copyOf(map);
		}
		catch (JsonParseException e)
		{
			log.warn("[unlock] couldn't read saved {}; starting fresh", key, e);
			return Map.of();
		}
	}
}
