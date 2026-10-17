/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * Everything the plugin knows about the game, loaded once from the data files by {@link DataLoader}.
 * Read-only, so it's safe to share between threads.
 */
public final class GameData
{
	private final Map<String, Location> locations;
	private final Map<String, Patch> patches;
	private final List<Seed> seeds;
	/** Seeds by their own item id and, for trees, their sapling's. */
	private final Map<Integer, Seed> seedsByItem;
	private final Map<String, Teleport> teleports;
	private final Map<String, UnlockRule> rules;
	@Getter
	private final CostTable costs;
	@Getter
	private final ItemCatalog items;
	/** The RuneLite release the patch table was generated from. */
	@Getter
	private final String runeliteTag;

	GameData(Map<String, Location> locations, List<Seed> seeds, Map<String, Teleport> teleports,
		Map<String, UnlockRule> rules, CostTable costs, ItemCatalog items, String runeliteTag)
	{
		this.locations = locations;
		this.patches = locations.values().stream()
			.flatMap(l -> l.getPatches().stream())
			.collect(Collectors.toUnmodifiableMap(Patch::getId, p -> p));
		this.seeds = List.copyOf(seeds);
		Map<Integer, Seed> byItem = new HashMap<>();
		for (Seed seed : seeds)
		{
			byItem.put(seed.getItemId(), seed);
			if (seed.isPlantedAsSapling())
			{
				byItem.put(seed.getSaplingItemId(), seed);
			}
		}
		this.seedsByItem = Map.copyOf(byItem);
		this.teleports = teleports;
		this.rules = rules;
		this.costs = costs;
		this.items = items;
		this.runeliteTag = runeliteTag;
	}

	public Collection<Location> locations()
	{
		return locations.values();
	}

	public Location location(String id)
	{
		return require(locations, id, "location");
	}

	public Collection<Patch> patches()
	{
		return patches.values();
	}

	public Patch patch(String id)
	{
		return require(patches, id, "patch");
	}

	public List<Seed> seeds()
	{
		return seeds;
	}

	/** The seed with this item id (or whose sapling has it), or null if it isn't a seed. */
	public Seed seedByItem(int itemId)
	{
		return seedsByItem.get(itemId);
	}

	/** Seeds for a kind of patch, lowest level first. */
	public List<Seed> seedsFor(PatchImplementation kind)
	{
		return seeds.stream()
			.filter(s -> s.getKind() == kind)
			.sorted((a, b) -> Integer.compare(a.getLevel(), b.getLevel()))
			.collect(Collectors.toList());
	}

	public Collection<Teleport> teleports()
	{
		return teleports.values();
	}

	public Teleport teleport(String id)
	{
		return require(teleports, id, "teleport");
	}

	public UnlockRule rule(String id)
	{
		return require(rules, id, "unlock rule");
	}

	private static <K, V> V require(Map<K, V> map, K key, String what)
	{
		V value = map.get(key);
		if (value == null)
		{
			throw new IllegalArgumentException("no " + what + " " + key);
		}
		return value;
	}
}
