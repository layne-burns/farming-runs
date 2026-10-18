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
import com.layneburns.farmingruns.data.Teleport;
import com.layneburns.farmingruns.run.MenuItems;
import com.layneburns.farmingruns.run.TeleportRecognizer;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.util.Text;

/**
 * Teleports used up for now: a daily allowance spent ("You have used 3 of your 3 cabbage teleports for today") until
 * the game's daily reset (midnight UTC), or an item out of charges until a message shows charges again. Each message
 * is tied to the teleport (or teleport item) you clicked just before it ({@link TeleportRecognizer}), so it works for any limited item.
 * Saved with your RuneScape profile. The run uses the next teleport in a stop's list while one is used up. Client
 * thread for the events; reads from any thread.
 */
@Slf4j
@Singleton
public class TeleportLimits
{
	private static final String KEY = "teleportLimits";
	/** A limit message counts for the teleport clicked within this many ticks. */
	private static final int CLICK_TICKS = 10;
	/** Saved as teleport (ref, or method for charges) to the UTC date it was used up, or "charges". */
	private static final String CHARGES = "charges";
	private static final Type MAP = new TypeToken<Map<String, String>>()
	{
	}.getType();

	private final Client client;
	private final GameData data;
	private final ConfigManager configManager;
	private final Gson gson;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile Map<String, String> usedUp = Map.of();
	@Nullable
	private volatile String loadedFor;
	private volatile boolean everLoaded;
	/** The teleport you last clicked, and when. Client thread. */
	@Nullable
	private String lastTeleport;
	private int lastTeleportTick;
	/** The teleport item you last clicked for anything (teleporting, checking charges), and when. Client thread. */
	@Nullable
	private String lastItemMethod;
	private int lastItemTick;

	@Inject
	TeleportLimits(Client client, GameData data, ConfigManager configManager, Gson gson)
	{
		this.client = client;
		this.data = data;
		this.configManager = configManager;
		this.gson = gson;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	/** True when this teleport ("method" or "method:destination") is used up for now. */
	public boolean isUsedUp(String ref)
	{
		ensureLoaded();
		String method = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : ref;
		return stillUsedUp(usedUp.get(ref)) || stillUsedUp(usedUp.get(method));
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		int itemId = MenuItems.itemId(event);
		String recognised = TeleportRecognizer.recognise(event.getMenuOption(), Text.removeTags(event.getMenuTarget()),
			itemId, data);
		if (recognised != null)
		{
			lastTeleport = recognised;
			lastTeleportTick = client.getTickCount();
		}
		String method = itemId > 0 ? methodOf(itemId) : null;
		if (method != null)
		{
			lastItemMethod = method;
			lastItemTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		TeleportLimitMessages.Limit limit = TeleportLimitMessages.parse(Text.removeTags(event.getMessage()));
		String key = limit == null ? null : keyFor(limit);
		if (key == null)
		{
			return;
		}
		ensureLoaded();
		Map<String, String> copy = new HashMap<>(usedUp);
		if (limit.usedUp())
		{
			copy.put(key, limit.isDaily() ? today() : CHARGES);
		}
		else
		{
			copy.remove(key);
		}
		if (!copy.equals(usedUp))
		{
			usedUp = Map.copyOf(copy);
			log.debug("[farmrun] teleport limits: {}", usedUp);
			save();
			listeners.forEach(Runnable::run);
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		load();
	}

	public void load()
	{
		loadedFor = configManager.getRSProfileKey();
		everLoaded = true;
		Map<String, String> loaded = Map.of();
		String json = configManager.getRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY);
		if (json != null)
		{
			try
			{
				Map<String, String> read = gson.fromJson(json, MAP);
				loaded = read == null ? Map.of() : Map.copyOf(read);
			}
			catch (JsonParseException e)
			{
				log.warn("[farmrun] couldn't read saved teleport limits", e);
			}
		}
		usedUp = loaded;
		listeners.forEach(Runnable::run);
	}

	/**
	 * What a message is about: a daily allowance belongs to the teleport just clicked, destination and all (the
	 * Ardougne cloak's farm, not its monastery); charges to the item just clicked. Null when nothing fits.
	 */
	@Nullable
	private String keyFor(TeleportLimitMessages.Limit limit)
	{
		int now = client.getTickCount();
		if (limit.isDaily())
		{
			return now - lastTeleportTick <= CLICK_TICKS ? lastTeleport : null;
		}
		return now - lastItemTick <= CLICK_TICKS ? lastItemMethod : null;
	}

	/** The teleport method an item belongs to, or null when it isn't a teleport item. */
	@Nullable
	private String methodOf(int itemId)
	{
		return data.teleports().stream()
			.filter(t -> t.getItemIds().contains(itemId)
				|| t.getDestinations().stream().anyMatch(d -> d.getItemIds().contains(itemId)))
			.map(Teleport::getId).findFirst().orElse(null);
	}

	/** A daily one counts only on the day it was used up (UTC); charges until a message shows some again. */
	private static boolean stillUsedUp(@Nullable String when)
	{
		return when != null && (when.equals(CHARGES) || when.equals(today()));
	}

	private static String today()
	{
		return LocalDate.now(ZoneOffset.UTC).toString();
	}

	private void save()
	{
		if (loadedFor != null)
		{
			configManager.setRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY, gson.toJson(usedUp));
		}
	}

	private void ensureLoaded()
	{
		if (!everLoaded || !Objects.equals(configManager.getRSProfileKey(), loadedFor))
		{
			load();
		}
	}
}
