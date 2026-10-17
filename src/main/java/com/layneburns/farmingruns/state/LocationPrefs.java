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
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

/**
 * Your choices per location, saved to your RuneScape profile: an unlock override (force unlocked or locked when the
 * rules get it wrong) and your own order of teleports instead of the default one. Listeners are told after every
 * change.
 */
@Slf4j
@Singleton
public class LocationPrefs
{
	private static final String OVERRIDES_KEY = "unlockOverrides";
	private static final String TELEPORTS_KEY = "teleportOrders";
	/** Before ranked lists, a single teleport per location was saved here; it's moved to the front on load. */
	private static final String OLD_TELEPORT_KEY = "teleportChoices";
	private static final Type BOOLEAN_MAP = new TypeToken<Map<String, Boolean>>()
	{
	}.getType();
	private static final Type LIST_MAP = new TypeToken<Map<String, List<String>>>()
	{
	}.getType();
	private static final Type STRING_MAP = new TypeToken<Map<String, String>>()
	{
	}.getType();

	private final ConfigManager configManager;
	private final Gson gson;
	private final GameData data;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile Map<String, Boolean> overrides = Map.of();
	private volatile Map<String, List<String>> teleports = Map.of();

	@Inject
	LocationPrefs(ConfigManager configManager, Gson gson, GameData data)
	{
		this.configManager = configManager;
		this.gson = gson;
		this.data = data;
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

	/**
	 * The location's teleports in order of preference: your order, or the default one. Saved entries that no longer
	 * name a known teleport (after a data update) are skipped; if none are left, the default is used.
	 */
	public List<String> teleportsFor(Location location)
	{
		List<String> saved = teleports.get(location.getId());
		if (saved != null)
		{
			List<String> valid = saved.stream().filter(ref -> data.teleportChoice(ref) != null)
				.collect(Collectors.toList());
			if (!valid.isEmpty())
			{
				return valid;
			}
		}
		return location.getDefaultTeleports();
	}

	public boolean hasCustomTeleports(String locationId)
	{
		return teleports.containsKey(locationId);
	}

	/** @param order your order of teleport ids, or null to go back to the default */
	public void setTeleports(String locationId, @Nullable List<String> order)
	{
		Map<String, List<String>> copy = new HashMap<>(teleports);
		if (order == null)
		{
			copy.remove(locationId);
		}
		else
		{
			copy.put(locationId, List.copyOf(order));
		}
		teleports = Map.copyOf(copy);
		save(TELEPORTS_KEY, teleports);
	}

	/** Loads the saved choices for the current profile. */
	public void load()
	{
		overrides = read(OVERRIDES_KEY, BOOLEAN_MAP);
		teleports = read(TELEPORTS_KEY, LIST_MAP);
		migrateSingleChoices();
		listeners.forEach(Runnable::run);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		load();
	}

	/** A teleport picked before ranked lists existed becomes the first of a custom list (the default follows on use). */
	private void migrateSingleChoices()
	{
		Map<String, String> old = read(OLD_TELEPORT_KEY, STRING_MAP);
		if (old.isEmpty())
		{
			return;
		}
		Map<String, List<String>> copy = new HashMap<>(teleports);
		old.forEach((location, teleport) -> copy.putIfAbsent(location, List.of(teleport)));
		teleports = Map.copyOf(copy);
		configManager.setRSProfileConfiguration(FarmingRunsConfig.GROUP, TELEPORTS_KEY, gson.toJson(teleports));
		configManager.unsetRSProfileConfiguration(FarmingRunsConfig.GROUP, OLD_TELEPORT_KEY);
		log.debug("[unlock] moved {} single teleport choices to ranked lists", old.size());
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
