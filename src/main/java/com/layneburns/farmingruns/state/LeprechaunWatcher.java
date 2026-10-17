/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Keeps the tool leprechaun's contents up to date from its varbits, which the game sends at login and whenever
 * you store or take something. Client thread only.
 */
@Slf4j
@Singleton
public class LeprechaunWatcher
{
	private static final Set<Integer> VARBITS = Arrays.stream(LeprechaunDecoder.VARBITS).boxed()
		.collect(Collectors.toUnmodifiableSet());

	private final Client client;
	private final ItemStore store;
	/** Uses left in a stored, filled bottomless bucket; 0 if none is stored. */
	@Getter
	private volatile int bottomlessBucketUses;

	@Inject
	LeprechaunWatcher(Client client, ItemStore store)
	{
		this.client = client;
		this.store = store;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (VARBITS.contains(event.getVarbitId()))
		{
			refresh();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			refresh();
		}
	}

	public void refresh()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Map<Integer, Integer> items = LeprechaunDecoder.decode(client::getVarbitValue);
		bottomlessBucketUses = LeprechaunDecoder.bottomlessBucketUses(client::getVarbitValue);
		// The bucket type's meaning beyond empty (1) / filled (>1) isn't confirmed yet; logged to check in game.
		log.debug("[leprechaun] {} item types; bottomless bucket type {} uses {}", items.size(),
			client.getVarbitValue(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE), bottomlessBucketUses);
		store.update(ItemSource.LEPRECHAUN, new StorageSnapshot(items, System.currentTimeMillis()));
	}
}
