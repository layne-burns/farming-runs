/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.pathing.ShortestPathClient;
import com.layneburns.farmingruns.state.ItemStore;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/**
 * A trip to buy pineapples for the compost bin, started from the Run tab when the check is short: Shortest Path
 * goes to the Trader Crewmember at Deepfin Point (the trading post sells them; hop worlds when it runs out), the
 * overhead line counts what's still to buy, and it ends by itself once you have enough. Starting a run ends it too.
 * Client thread, except where noted.
 */
@Slf4j
@Singleton
public class PineappleErrand
{
	/** The Trader Crewmember on Deepfin Point's dock (where the Sailors' amulet lands too). */
	static final WorldPoint DEEPFIN_TRADER = new WorldPoint(1943, 2757, 0);

	private final Client client;
	private final ClientThread clientThread;
	private final GameData data;
	private final ItemStore items;
	private final ShortestPathClient shortestPath;
	private final FarmingRunsConfig config;
	private final RunSession session;
	/** Pineapples wanted in all (bank and carried); 0 when there's no errand. */
	private volatile int wanted;

	@Inject
	PineappleErrand(Client client, ClientThread clientThread, GameData data, ItemStore items,
		ShortestPathClient shortestPath, FarmingRunsConfig config, RunSession session)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.data = data;
		this.items = items;
		this.shortestPath = shortestPath;
		this.config = config;
		this.session = session;
	}

	/** Starts the trip, to have {@code total} pineapples in all. Any thread. */
	public void start(int total)
	{
		clientThread.invoke(() ->
		{
			wanted = total;
			log.debug("[farmrun] pineapple errand: {} wanted, {} held", total, held());
			shortestPath.target(Set.of(DEEPFIN_TRADER), config.pathCarriedOnly(), false);
		});
	}

	/** Ends the trip and its path. Any thread. */
	public void stop()
	{
		clientThread.invoke(() ->
		{
			if (wanted > 0)
			{
				wanted = 0;
				shortestPath.clear();
			}
		});
	}

	public boolean isActive()
	{
		return wanted > 0;
	}

	/** How many more to buy; 0 when there's no errand. Any thread. */
	public int stillToBuy()
	{
		return wanted == 0 ? 0 : Math.max(0, wanted - held());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (wanted == 0)
		{
			return;
		}
		if (session.active() != null)
		{
			// The run points the path now.
			wanted = 0;
			return;
		}
		if (held() >= wanted)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Farming Runs: you have your " + wanted
				+ " pineapples.", null);
			wanted = 0;
			shortestPath.clear();
		}
	}

	private int held()
	{
		return items.ledger().countAny(data.getItems().group("pineapple"));
	}
}
