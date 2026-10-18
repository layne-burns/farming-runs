/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.Patch;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Times each stop of a run, to tune the travel costs (phase 9): you leave at your first teleport towards it (a
 * recognised teleport click, or any jump in position too big to be running), or when the stop before is finished if
 * you walk; you arrive at your first click on one of its patches, its gardener or the tool leprechaun; and you're done
 * when the stop is finished. So travel = arrive − leave and work = done − arrive.
 * Each stop is logged as "[farmrun] leg ..." (with Debug logging on), and the last run's legs go in the debug info.
 * Client thread.
 */
@Slf4j
@Singleton
public class LegTimer
{
	/** One stop's timing; travel is -1 when the arrival wasn't seen. */
	@Value
	public static class Leg
	{
		String locationId;
		long travelMillis;
		long workMillis;
		@Nullable
		String teleport;
	}

	private static final Set<MenuAction> OBJECT_ACTIONS = EnumSet.of(MenuAction.GAME_OBJECT_FIRST_OPTION,
		MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION,
		MenuAction.GAME_OBJECT_FIFTH_OPTION, MenuAction.WIDGET_TARGET_ON_GAME_OBJECT);
	private static final Set<MenuAction> NPC_ACTIONS = EnumSet.of(MenuAction.NPC_FIRST_OPTION,
		MenuAction.NPC_SECOND_OPTION, MenuAction.NPC_THIRD_OPTION, MenuAction.NPC_FOURTH_OPTION,
		MenuAction.NPC_FIFTH_OPTION, MenuAction.WIDGET_TARGET_ON_NPC);

	/** Further than running goes in a tick: a teleport. */
	private static final int TELEPORT_TILES = 30;
	/** About how long a teleport takes from the click to the jump (three ticks). */
	private static final long CAST_MILLIS = 1800;

	private final Client client;
	private final GameData data;
	private final RunSession session;
	private final Set<Integer> patchVarbits;
	private final Set<Integer> gardeners;

	private long run;
	@Nullable
	private String stopId;
	private long leaveAt;
	/** Travel to the current stop has started (a teleport was clicked or seen). */
	private boolean left;
	@Nullable
	private WorldPoint lastPosition;
	private long arriveAt = -1;
	@Nullable
	private String teleport;
	private final List<Leg> legs = new ArrayList<>();
	private volatile List<Leg> lastRun = List.of();

	@Inject
	LegTimer(Client client, GameData data, RunSession session)
	{
		this.client = client;
		this.data = data;
		this.session = session;
		this.patchVarbits = data.patches().stream().map(Patch::getVarbitId).collect(Collectors.toUnmodifiableSet());
		this.gardeners = data.patches().stream().map(Patch::getGardenerNpcId).filter(id -> id != Patch.NO_GARDENER)
			.collect(Collectors.toUnmodifiableSet());
		session.addListener(this::sessionChanged);
	}

	/** The legs of the current run so far, or of the last one when none is going. */
	public List<Leg> legs()
	{
		return run != 0 ? List.copyOf(legs) : lastRun;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (run == 0 || stopId == null || arriveAt >= 0)
		{
			return;
		}
		long now = System.currentTimeMillis();
		String recognised = TeleportRecognizer.recognise(event.getMenuOption(), Text.removeTags(event.getMenuTarget()),
			MenuItems.itemId(event), data);
		if (recognised != null)
		{
			// Travel starts at the first teleport; the last one named is the one that took you there.
			if (!left)
			{
				leaveAt = now;
				left = true;
			}
			teleport = recognised;
			return;
		}
		if (isHere() && isPatchWork(event))
		{
			arriveAt = now;
		}
	}

	/** Called by the session (client thread) whenever the run starts, moves on, finishes or stops. */
	private void sessionChanged()
	{
		RunSession.ActiveRun active = session.active();
		long now = System.currentTimeMillis();
		if (active == null || active.getStartedAtMillis() != run)
		{
			if (run != 0)
			{
				lastRun = List.copyOf(legs);
			}
			legs.clear();
			stopId = null;
			run = active == null ? 0 : active.getStartedAtMillis();
		}
		if (active == null)
		{
			return;
		}
		RunProgress.Stop stop = active.getProgress().currentStop();
		String next = stop == null ? null : stop.getLocationId();
		if (Objects.equals(next, stopId))
		{
			return;
		}
		if (stopId != null && active.getProgress().getStops().stream()
			.anyMatch(s -> s.getLocationId().equals(stopId) && active.getProgress().isDone(s)))
		{
			Leg leg = new Leg(stopId, arriveAt < 0 ? -1 : arriveAt - leaveAt, now - (arriveAt < 0 ? leaveAt : arriveAt),
				teleport);
			legs.add(leg);
			log.debug("[farmrun] leg {}: travel {}, work {}s{}", leg.getLocationId(),
				leg.getTravelMillis() < 0 ? "?" : leg.getTravelMillis() / 1000 + "s", leg.getWorkMillis() / 1000,
				teleport == null ? "" : " via " + teleport);
		}
		// The next leg starts now; the first teleport after this (a click or a jump) moves its start there.
		stopId = next;
		leaveAt = now;
		left = false;
		arriveAt = -1;
		teleport = null;
	}

	/**
	 * A jump of more than {@link #TELEPORT_TILES} in one tick (or to another level or instance) is a teleport, whatever
	 * kind: if no teleport click was seen first, travel starts there (less the cast, {@link #CAST_MILLIS}).
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldPoint here = player.getWorldLocation();
		WorldPoint before = lastPosition;
		lastPosition = here;
		if (run == 0 || stopId == null || arriveAt >= 0 || left || before == null)
		{
			return;
		}
		if (before.getPlane() != here.getPlane() || before.distanceTo2D(here) > TELEPORT_TILES)
		{
			leaveAt = System.currentTimeMillis() - CAST_MILLIS;
			left = true;
		}
	}

	/** True when you're in the current stop's area. */
	private boolean isHere()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return false;
		}
		Location location = data.location(stopId);
		int region = player.getWorldLocation().getRegionID();
		return location.getRegionId() == region || location.getExtraRegionIds().contains(region);
	}

	/** A click on a farming patch (or using an item on one), a gardener, or the tool leprechaun. */
	private boolean isPatchWork(MenuOptionClicked event)
	{
		if (OBJECT_ACTIONS.contains(event.getMenuAction()))
		{
			ObjectComposition object = client.getObjectDefinition(event.getId());
			return object != null && patchVarbits.contains(object.getVarbitId());
		}
		if (NPC_ACTIONS.contains(event.getMenuAction()))
		{
			NPC npc = event.getMenuEntry().getNpc();
			return npc != null && (gardeners.contains(npc.getId())
				|| (npc.getName() != null && npc.getName().toLowerCase().contains("leprechaun")));
		}
		return false;
	}
}
