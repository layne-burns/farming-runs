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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private final Set<Integer> trackedItemIds;
	private final List<GoalPreset> goals;

	GameData(Map<String, Location> locations, List<Seed> seeds, Map<String, Teleport> teleports,
		Map<String, UnlockRule> rules, CostTable costs, ItemCatalog items, String runeliteTag, List<GoalPreset> goals)
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
		this.trackedItemIds = collectTracked(this.seeds, teleports, items);
		this.goals = goals;
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

	/** How many patches of each kind exist. (Phase 3 narrows this to the patches you have unlocked.) */
	public Map<PatchImplementation, Integer> patchCountsByKind()
	{
		Map<PatchImplementation, Integer> counts = new HashMap<>();
		for (Patch patch : patches.values())
		{
			counts.merge(patch.getKind(), 1, Integer::sum);
		}
		return counts;
	}

	/** The built-in goal presets, in file order (grouped by category). */
	public List<GoalPreset> goals()
	{
		return goals;
	}

	public Collection<Teleport> teleports()
	{
		return teleports.values();
	}

	public Teleport teleport(String id)
	{
		return require(teleports, id, "teleport");
	}

	/** A "method" or "method:destination" reference, resolved; null if it doesn't name a known teleport. */
	public TeleportChoice teleportChoice(String ref)
	{
		return resolve(teleports, ref);
	}

	/**
	 * Resolves a reference: "method" for a method with no destinations, "method:destination" otherwise. Null when the
	 * method or destination is unknown, or the destination is missing or extra.
	 */
	static TeleportChoice resolve(Map<String, Teleport> teleports, String ref)
	{
		int colon = ref.indexOf(':');
		Teleport method = teleports.get(colon < 0 ? ref : ref.substring(0, colon));
		if (method == null)
		{
			return null;
		}
		if (colon < 0)
		{
			return method.getDestinations().isEmpty() ? new TeleportChoice(ref, method, null) : null;
		}
		TeleportDestination destination = method.destination(ref.substring(colon + 1));
		return destination == null ? null : new TeleportChoice(ref, method, destination);
	}

	public Collection<UnlockRule> rules()
	{
		return rules.values();
	}

	public UnlockRule rule(String id)
	{
		return require(rules, id, "unlock rule");
	}

	/**
	 * Every item a farming run cares about: seeds, saplings, payments, tools, compost, outfit, teleport items and
	 * runes. Only these are recorded from the bank and other containers.
	 */
	public Set<Integer> trackedItemIds()
	{
		return trackedItemIds;
	}

	private static Set<Integer> collectTracked(List<Seed> seeds, Map<String, Teleport> teleports, ItemCatalog items)
	{
		Set<Integer> ids = new HashSet<>();
		for (Seed seed : seeds)
		{
			ids.add(seed.getItemId());
			if (seed.isPlantedAsSapling())
			{
				ids.add(seed.getSaplingItemId());
			}
			seed.getPayment().forEach(p -> ids.add(p.getItemId()));
		}
		for (Teleport teleport : teleports.values())
		{
			ids.addAll(teleport.getItemIds());
		}
		for (String group : items.groupNames())
		{
			ids.addAll(items.group(group));
		}
		ids.addAll(items.runes().values());
		return Set.copyOf(ids);
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
