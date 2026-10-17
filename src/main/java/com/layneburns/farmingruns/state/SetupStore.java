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
import com.layneburns.farmingruns.run.RunSetup;
import com.layneburns.farmingruns.run.SetupJson;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

/**
 * Your run setup and saved presets (SPEC §2.3), kept in your RuneScape profile: the setup you're editing survives a
 * restart, and presets are saved by name. Listeners are told after every change.
 */
@Slf4j
@Singleton
public class SetupStore
{
	private static final String CURRENT_KEY = "currentSetup";
	private static final String PRESETS_KEY = "presets";
	private static final Type PRESET_MAP = new TypeToken<Map<String, String>>()
	{
	}.getType();

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile RunSetup current = RunSetup.EMPTY;
	/** Preset name to setup, sorted by name. */
	private volatile Map<String, RunSetup> presets = Map.of();

	@Inject
	SetupStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public RunSetup current()
	{
		return current;
	}

	public void setCurrent(RunSetup setup)
	{
		current = setup;
		configManager.setRSProfileConfiguration(FarmingRunsConfig.GROUP, CURRENT_KEY, SetupJson.toJson(gson, setup));
		changed();
	}

	public Map<String, RunSetup> presets()
	{
		return presets;
	}

	public void savePreset(String name, RunSetup setup)
	{
		Map<String, RunSetup> copy = new TreeMap<>(presets);
		copy.put(name, setup);
		presets = copy;
		savePresets();
	}

	public void deletePreset(String name)
	{
		Map<String, RunSetup> copy = new TreeMap<>(presets);
		copy.remove(name);
		presets = copy;
		savePresets();
	}

	/** Loads the setup and presets for the current profile. */
	public void load()
	{
		RunSetup saved = SetupJson.fromJson(gson, configManager.getRSProfileConfiguration(FarmingRunsConfig.GROUP, CURRENT_KEY));
		current = saved == null ? RunSetup.EMPTY : saved;
		Map<String, RunSetup> loaded = new TreeMap<>();
		String json = configManager.getRSProfileConfiguration(FarmingRunsConfig.GROUP, PRESETS_KEY);
		if (json != null)
		{
			try
			{
				Map<String, String> raw = gson.fromJson(json, PRESET_MAP);
				if (raw != null)
				{
					raw.forEach((name, setupJson) ->
					{
						RunSetup setup = SetupJson.fromJson(gson, setupJson);
						if (setup != null)
						{
							loaded.put(name, setup);
						}
					});
				}
			}
			catch (JsonParseException e)
			{
				log.warn("[farmrun] couldn't read saved presets", e);
			}
		}
		presets = loaded;
		changed();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		load();
	}

	/** Each preset is stored as its own JSON text, so one unreadable preset can't spoil the others. */
	private void savePresets()
	{
		Map<String, String> raw = new TreeMap<>();
		presets.forEach((name, setup) -> raw.put(name, SetupJson.toJson(gson, setup)));
		configManager.setRSProfileConfiguration(FarmingRunsConfig.GROUP, PRESETS_KEY, gson.toJson(raw));
		changed();
	}

	private void changed()
	{
		listeners.forEach(Runnable::run);
	}
}
