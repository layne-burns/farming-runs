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
import com.layneburns.farmingruns.run.CompostBinFill;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

/**
 * Your compost bin choices (SPEC §2.7), saved to your RuneScape profile: the default item, your order and your
 * keeps. Listeners are told after every change.
 */
@Slf4j
@Singleton
public class CompostPrefs
{
	private static final String KEY = "compostBin";

	/** As saved. */
	private static class Saved
	{
		Integer defaultItem;
		List<Integer> order;
		Map<Integer, Integer> keeps;
	}

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile CompostBinFill.Choices choices = CompostBinFill.Choices.NONE;
	/** The account these were loaded for, or null before login. */
	private volatile String loadedFor;
	private volatile boolean everLoaded;

	@Inject
	CompostPrefs(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public CompostBinFill.Choices choices()
	{
		ensureLoaded();
		return choices;
	}

	/** @param itemId the item to use first, or null for none */
	public void setDefault(@Nullable Integer itemId)
	{
		ensureLoaded();
		save(new CompostBinFill.Choices(itemId, choices.getOrder(), choices.getKeeps()));
	}

	/** @param order your order of item ids, or an empty list for the automatic ranking */
	public void setOrder(List<Integer> order)
	{
		ensureLoaded();
		save(new CompostBinFill.Choices(choices.getDefaultItem(), List.copyOf(order), choices.getKeeps()));
	}

	/** @param keep {@link CompostBinFill#NEVER}, how many to hold back, or null for the default */
	public void setKeep(int itemId, @Nullable Integer keep)
	{
		ensureLoaded();
		Map<Integer, Integer> keeps = new HashMap<>(choices.getKeeps());
		if (keep == null)
		{
			keeps.remove(itemId);
		}
		else
		{
			keeps.put(itemId, keep);
		}
		save(new CompostBinFill.Choices(choices.getDefaultItem(), choices.getOrder(), Map.copyOf(keeps)));
	}

	/** Loads the saved choices for the current profile. */
	public void load()
	{
		loadedFor = configManager.getRSProfileKey();
		everLoaded = true;
		choices = read();
		listeners.forEach(Runnable::run);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		load();
	}

	/** Reloads if the account changed, so choices are never saved over another account's. */
	private void ensureLoaded()
	{
		if (!everLoaded || !Objects.equals(configManager.getRSProfileKey(), loadedFor))
		{
			load();
		}
	}

	private void save(CompostBinFill.Choices updated)
	{
		choices = updated;
		if (loadedFor != null)
		{
			Saved saved = new Saved();
			saved.defaultItem = updated.getDefaultItem();
			saved.order = updated.getOrder();
			saved.keeps = updated.getKeeps();
			configManager.setRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY, gson.toJson(saved));
		}
		// Not logged in: kept for this session only.
		listeners.forEach(Runnable::run);
	}

	private CompostBinFill.Choices read()
	{
		String json = configManager.getRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY);
		if (json == null)
		{
			return CompostBinFill.Choices.NONE;
		}
		try
		{
			Saved saved = gson.fromJson(json, Saved.class);
			if (saved == null)
			{
				return CompostBinFill.Choices.NONE;
			}
			return new CompostBinFill.Choices(saved.defaultItem, saved.order == null ? List.of() : List.copyOf(saved.order),
				saved.keeps == null ? Map.of() : Map.copyOf(saved.keeps));
		}
		catch (JsonParseException e)
		{
			log.warn("[farmrun] couldn't read saved compost bin choices; starting fresh", e);
			return CompostBinFill.Choices.NONE;
		}
	}
}
