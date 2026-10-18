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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Learns the way you actually go to each stop: for every teleport-sized jump on the way, what you clicked just
 * before it ("Sailors' amulet", "Charter Trader Crewmember", "Travel Captain Barnaby"). When you get there by more
 * than one step, the chain is saved for that stop and planned teleport ({@link LocationPrefs#setChain}), and the
 * overhead line shows it on later runs ({@link TravelChain}). Client thread.
 */
@Slf4j
@Singleton
public class RouteLearner
{
	/** A move this far in one tick is a step of the way (as {@link LegTimer} counts them). */
	private static final int JUMP_TILES = 30;
	/** A click counts for a jump this many ticks later at most (a teleport's cast, a boat's crossing). */
	private static final int CLICK_TICKS = 20;
	/** Clicks that never move you anywhere far. */
	private static final Set<String> IGNORED = Set.of("walk here", "cancel", "examine", "use", "wear", "wield",
		"remove", "drop", "take", "continue");

	private final Client client;
	private final GameData data;
	private final RunSession session;
	private final LocationPrefs prefs;
	/** The stop being travelled to, the teleport planned for it, and the steps taken so far. */
	@Nullable
	private String stopId;
	private String ref;
	private final List<String> steps = new ArrayList<>();
	private boolean saved;
	@Nullable
	private String lastClick;
	private int lastClickTick;
	@Nullable
	private WorldPoint lastPosition;

	@Inject
	RouteLearner(Client client, GameData data, RunSession session, LocationPrefs prefs)
	{
		this.client = client;
		this.data = data;
		this.session = session;
		this.prefs = prefs;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (session.active() == null)
		{
			return;
		}
		String label = label(event.getMenuOption(), Text.removeTags(event.getMenuTarget()), MenuItems.itemId(event));
		if (label != null)
		{
			lastClick = label;
			lastClickTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		RunSession.ActiveRun run = session.active();
		Player me = client.getLocalPlayer();
		RunProgress.Stop stop = run == null ? null : run.getProgress().currentStop();
		if (stop == null || me == null)
		{
			stopId = null;
			return;
		}
		if (!stop.getLocationId().equals(stopId))
		{
			// A new leg.
			stopId = stop.getLocationId();
			TeleportChoice teleport = run.getTeleports().get(stopId);
			ref = teleport == null ? TravelChain.WALK : teleport.getRef();
			steps.clear();
			saved = false;
			lastClick = null;
		}
		WorldPoint here = me.getWorldLocation();
		WorldPoint before = lastPosition;
		lastPosition = here;
		if (saved)
		{
			return;
		}
		if (before != null && before.distanceTo2D(here) > JUMP_TILES)
		{
			boolean recent = lastClick != null && client.getTickCount() - lastClickTick <= CLICK_TICKS;
			steps.add(recent ? lastClick : "Teleport");
			lastClick = null;
		}
		Location location = data.location(stopId);
		int region = here.getRegionID();
		if (location.getRegionId() == region || location.getExtraRegionIds().contains(region))
		{
			saved = true;
			if (steps.size() > 1)
			{
				prefs.setChain(stopId, ref, List.copyOf(steps));
				log.debug("[farmrun] way to {} via {}: {}", stopId, ref, steps);
			}
		}
	}

	/** What a click did, for the chain: a teleport's name, or the option and what it was on; null if it's nothing. */
	@Nullable
	private String label(String option, String target, int itemId)
	{
		String opt = Text.removeTags(option).trim();
		if (opt.isEmpty() || IGNORED.contains(opt.toLowerCase(Locale.ROOT)))
		{
			return null;
		}
		String recognised = TeleportRecognizer.recognise(opt, target, itemId, data);
		if (recognised != null)
		{
			TeleportChoice choice = data.teleportChoice(recognised);
			return choice != null ? choice.displayName() : data.teleport(recognised).getName();
		}
		return target.isEmpty() ? opt : opt + " " + target;
	}
}
