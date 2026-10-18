/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.state.LocationPrefs;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * During a run, notices which teleport took you to each stop ({@link TeleportRecognizer}) and, when it isn't the
 * stop's first choice, offers it in the Run tab ({@link TeleportSuggestions}) with a note in the chat. It only
 * suggests; your teleport lists change only when you say so. Client thread.
 */
@Slf4j
@Singleton
public class TeleportWatcher
{
	/** A teleport counts for a stop reached within this many ticks (about a minute, for the walk after landing). */
	private static final int WINDOW_TICKS = 100;

	private final Client client;
	private final GameData data;
	private final RunSession session;
	private final LocationPrefs prefs;
	private final TeleportSuggestions suggestions;
	/** The last teleport you clicked during the run, as recognised, and when. */
	@Nullable
	private String lastTeleport;
	private int lastTeleportTick;
	private int lastRegion = -1;
	/** Stops reached so far in this run, and which run that was (by its start time). */
	private final Set<String> reached = new HashSet<>();
	private long runStartedAt;

	@Inject
	TeleportWatcher(Client client, GameData data, RunSession session, LocationPrefs prefs,
		TeleportSuggestions suggestions)
	{
		this.client = client;
		this.data = data;
		this.session = session;
		this.prefs = prefs;
		this.suggestions = suggestions;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (session.active() == null)
		{
			return;
		}
		String recognised = TeleportRecognizer.recognise(event.getMenuOption(), Text.removeTags(event.getMenuTarget()),
			MenuItems.itemId(event), data);
		if (recognised != null)
		{
			lastTeleport = recognised;
			lastTeleportTick = client.getTickCount();
			log.debug("[farmrun] teleport clicked: {}", recognised);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		RunSession.ActiveRun run = session.active();
		Player player = client.getLocalPlayer();
		if (run == null || player == null)
		{
			return;
		}
		if (run.getStartedAtMillis() != runStartedAt)
		{
			runStartedAt = run.getStartedAtMillis();
			reached.clear();
			lastTeleport = null;
		}
		int region = player.getWorldLocation().getRegionID();
		if (region == lastRegion)
		{
			return;
		}
		lastRegion = region;
		for (RunProgress.Stop stop : run.getProgress().getStops())
		{
			Location location = data.location(stop.getLocationId());
			if ((location.getRegionId() == region || location.getExtraRegionIds().contains(region))
				&& reached.add(location.getId()))
			{
				arrived(location);
			}
		}
	}

	/** First time at a stop this run: was it by a teleport other than its first choice? */
	private void arrived(Location location)
	{
		String used = lastTeleport;
		boolean recent = used != null && client.getTickCount() - lastTeleportTick <= WINDOW_TICKS;
		lastTeleport = null;
		if (!recent)
		{
			return;
		}
		List<String> refs = prefs.teleportsFor(location);
		String ref = TeleportRecognizer.resolve(used, refs, data);
		if (ref == null || (!refs.isEmpty() && refs.get(0).equals(ref)))
		{
			return;
		}
		TeleportChoice choice = data.teleportChoice(ref);
		if (choice != null && suggestions.offer(new TeleportSuggestions.Suggestion(location.getId(), ref)))
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Farming Runs: you reached " + location.getName()
				+ " with " + choice.displayName() + ". The Run tab can make it this stop's first teleport.", null);
		}
	}
}
