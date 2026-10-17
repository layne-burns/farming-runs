/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.layneburns.farmingruns.data.GameData;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * Reads the bank, seed vault, seed box, inventory and worn items whenever the game sends them, keeping only the
 * items a run cares about (see {@link GameData#trackedItemIds()}). Noted items and bank placeholders are
 * turned into their plain item. Client thread only.
 */
@Slf4j
@Singleton
public class ContainerWatcher
{
	private static final Map<Integer, ItemSource> CONTAINERS = Map.of(
		InventoryID.BANK, ItemSource.BANK,
		InventoryID.SEED_VAULT, ItemSource.SEED_VAULT,
		InventoryID.SEED_BOX, ItemSource.SEED_BOX,
		InventoryID.INV, ItemSource.INVENTORY,
		InventoryID.WORN, ItemSource.EQUIPMENT);

	private final Client client;
	private final ItemManager itemManager;
	private final ItemStore store;
	private final Set<Integer> tracked;

	@Inject
	ContainerWatcher(Client client, ItemManager itemManager, ItemStore store, GameData data)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.store = store;
		this.tracked = data.trackedItemIds();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		ItemSource source = CONTAINERS.get(event.getContainerId());
		if (source != null)
		{
			record(source, event.getItemContainer());
		}
	}

	/** Reads the containers the client already has (for when the plugin starts while logged in). */
	public void readCurrent()
	{
		CONTAINERS.forEach((id, source) ->
		{
			ItemContainer container = client.getItemContainer(id);
			if (container != null)
			{
				record(source, container);
			}
		});
	}

	private void record(ItemSource source, ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue; // empty slot or bank placeholder
			}
			int id = itemManager.canonicalize(item.getId());
			if (tracked.contains(id))
			{
				counts.merge(id, item.getQuantity(), Integer::sum);
			}
		}
		log.debug("[seedlog] {}: {} tracked item types", source, counts.size());
		store.update(source, new StorageSnapshot(counts, System.currentTimeMillis()));
	}
}
