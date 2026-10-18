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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;

/**
 * During a run, works out which stop and patch a click was on: one of its patches (or using an item on one, like
 * compost), its gardener, or the tool leprechaun there. So compost and payment go to the patch you actually worked on,
 * and the run can follow you when you go to a stop out of order (listeners hear every such click). Client thread.
 */
@Singleton
public class PatchClicks
{
	/** A click on something at one of the run's stops. */
	@Value
	public static class Click
	{
		String locationId;
		/** The patch clicked (or used an item on); null for a gardener or the leprechaun. */
		@Nullable
		String patchId;
		/** The gardener clicked, or -1. */
		int gardenerNpcId;
		int tick;
	}

	private static final Set<MenuAction> OBJECT_ACTIONS = EnumSet.of(MenuAction.GAME_OBJECT_FIRST_OPTION,
		MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION,
		MenuAction.GAME_OBJECT_FIFTH_OPTION, MenuAction.WIDGET_TARGET_ON_GAME_OBJECT);
	private static final Set<MenuAction> NPC_ACTIONS = EnumSet.of(MenuAction.NPC_FIRST_OPTION,
		MenuAction.NPC_SECOND_OPTION, MenuAction.NPC_THIRD_OPTION, MenuAction.NPC_FOURTH_OPTION,
		MenuAction.NPC_FIFTH_OPTION, MenuAction.WIDGET_TARGET_ON_NPC);

	private final Client client;
	private final GameData data;
	private final RunSession session;
	private final List<Consumer<Click>> listeners = new CopyOnWriteArrayList<>();
	@Nullable
	private Click last;

	@Inject
	PatchClicks(Client client, GameData data, RunSession session)
	{
		this.client = client;
		this.data = data;
		this.session = session;
	}

	public void addListener(Consumer<Click> listener)
	{
		listeners.add(listener);
	}

	/** The last click on something at a run stop, if it was within {@code ticks} ticks; else null. */
	@Nullable
	public Click recent(int ticks)
	{
		Click click = last;
		return click != null && client.getTickCount() - click.getTick() <= ticks ? click : null;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		Click click = classify(event);
		if (click != null)
		{
			last = click;
			listeners.forEach(l -> l.accept(click));
		}
	}

	/** What a click was on at one of the run's stops in the area you're in; null when it wasn't on one. */
	@Nullable
	public Click classify(MenuOptionClicked event)
	{
		RunSession.ActiveRun run = session.active();
		Player player = client.getLocalPlayer();
		if (run == null || player == null)
		{
			return null;
		}
		int region = player.getWorldLocation().getRegionID();
		for (RunProgress.Stop stop : run.getProgress().getStops())
		{
			Location location = data.location(stop.getLocationId());
			if (location.getRegionId() != region && !location.getExtraRegionIds().contains(region))
			{
				continue;
			}
			if (OBJECT_ACTIONS.contains(event.getMenuAction()))
			{
				ObjectComposition object = client.getObjectDefinition(event.getId());
				for (PlantingPlan.Assignment a : stop.getPatches())
				{
					if (object != null && a.getPatch().getVarbitId() == object.getVarbitId())
					{
						return new Click(stop.getLocationId(), a.getPatch().getId(), -1, client.getTickCount());
					}
				}
			}
			else if (NPC_ACTIONS.contains(event.getMenuAction()))
			{
				NPC npc = event.getMenuEntry().getNpc();
				if (npc == null)
				{
					continue;
				}
				boolean gardener = stop.getPatches().stream().map(PlantingPlan.Assignment::getPatch)
					.anyMatch(p -> p.getGardenerNpcId() == npc.getId() && p.getGardenerNpcId() != Patch.NO_GARDENER);
				boolean leprechaun = npc.getName() != null && npc.getName().toLowerCase(Locale.ROOT).contains("leprechaun");
				if (gardener || leprechaun)
				{
					return new Click(stop.getLocationId(), null, gardener ? npc.getId() : -1, client.getTickCount());
				}
			}
		}
		return null;
	}
}
