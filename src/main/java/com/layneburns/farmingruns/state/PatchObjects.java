/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Patch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.client.eventbus.Subscribe;

/**
 * The farming patches' objects in the loaded scene, for outlining them. A patch's object changes with its varbit (the
 * one that reports its state), so objects are matched to patches by that varbit, and by region, since the same
 * varbit is reused in other places. Client thread.
 */
@Singleton
public class PatchObjects
{
	private final Client client;
	/** Varbits that drive some patch, so other objects are ignored. */
	private final Set<Integer> patchVarbits;
	private final Map<Integer, List<TileObject>> byVarbit = new HashMap<>();

	@Inject
	PatchObjects(Client client, GameData data)
	{
		this.client = client;
		this.patchVarbits = data.patches().stream().map(Patch::getVarbitId).collect(Collectors.toUnmodifiableSet());
	}

	/** The patch's objects in the scene (usually one), or none if it isn't loaded. */
	public List<TileObject> objects(Patch patch, Set<Integer> regions)
	{
		List<TileObject> found = new ArrayList<>();
		for (TileObject object : byVarbit.getOrDefault(patch.getVarbitId(), List.of()))
		{
			if (regions.contains(object.getWorldLocation().getRegionID()))
			{
				found.add(object);
			}
		}
		return found;
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		add(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		remove(event.getGameObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		add(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		remove(event.getGroundObject());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			byVarbit.clear();
		}
	}

	private void add(TileObject object)
	{
		int varbit = varbit(object);
		if (patchVarbits.contains(varbit))
		{
			byVarbit.computeIfAbsent(varbit, v -> new ArrayList<>()).add(object);
		}
	}

	private void remove(TileObject object)
	{
		int varbit = varbit(object);
		List<TileObject> objects = byVarbit.get(varbit);
		if (objects != null)
		{
			objects.remove(object);
		}
	}

	private int varbit(TileObject object)
	{
		ObjectComposition composition = client.getObjectDefinition(object.getId());
		return composition == null ? -1 : composition.getVarbitId();
	}
}
