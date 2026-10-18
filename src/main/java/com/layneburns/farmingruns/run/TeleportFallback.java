/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.inventory.RunCheckService;
import com.layneburns.farmingruns.inventory.TeleportPicker;
import com.layneburns.farmingruns.state.ItemStore;
import com.layneburns.farmingruns.state.LocationPrefs;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.state.TeleportLimits;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

/**
 * Mid-run, when a teleport gets used up ("You have used 3 of your 3 ... teleports for today"), switches the stops
 * still to come that use it to the next teleport in their list, so the Live tab, the overhead line and Shortest Path
 * follow along.
 */
@Slf4j
@Singleton
public class TeleportFallback
{
	private final GameData data;
	private final RunSession session;
	private final LocationPrefs prefs;
	private final PlayerInfo player;
	private final ItemStore items;
	private final TeleportLimits limits;
	private final RunCheckService checks;

	@Inject
	TeleportFallback(GameData data, RunSession session, LocationPrefs prefs, PlayerInfo player, ItemStore items,
		TeleportLimits limits, RunCheckService checks, ClientThread clientThread)
	{
		this.data = data;
		this.session = session;
		this.prefs = prefs;
		this.player = player;
		this.items = items;
		this.limits = limits;
		this.checks = checks;
		limits.addListener(() -> clientThread.invokeLater(this::repick));
	}

	private void repick()
	{
		RunSession.ActiveRun run = session.active();
		if (run == null)
		{
			return;
		}
		Map<String, TeleportChoice> teleports = new HashMap<>(run.getTeleports());
		for (RunProgress.Stop stop : run.getProgress().getStops())
		{
			String id = stop.getLocationId();
			TeleportChoice now = teleports.get(id);
			if (now == null || !limits.isUsedUp(now.getRef()) || run.getProgress().isDone(stop))
			{
				continue;
			}
			TeleportChoice next = TeleportPicker.pick(checks.usable(prefs.teleportsFor(data.location(id))), data,
				player.getFacts(), items.ledger());
			log.debug("[farmrun] {} used up: {} now {}", now.getRef(), id, next == null ? "walk" : next.getRef());
			if (next == null)
			{
				teleports.remove(id);
			}
			else
			{
				teleports.put(id, next);
			}
		}
		session.replaceTeleports(teleports);
	}
}
