/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/**
 * Reads the data files under resources/com/layneburns/farmingruns/ into {@link GameData}, checking every
 * reference between them on the way. Anything wrong throws a {@link DataException} naming the file and entry,
 * so a bad edit shows up the moment the plugin starts (and in DataIntegrityTest), not halfway through a run.
 */
@RequiredArgsConstructor
public final class DataLoader
{
	private final Gson gson;

	public GameData load()
	{
		Map<String, UnlockRule> rules = loadRules();
		Map<String, Teleport> teleports = loadTeleports();
		RawData.PatchLocations generated = read("patch_locations.json", RawData.PatchLocations.class);
		Map<String, Location> locations = loadLocations(generated, rules, teleports);
		CostTable costs = loadCosts(locations);
		List<Seed> seeds = loadSeeds();
		return new GameData(locations, seeds, teleports, rules, costs, loadItems(), generated.runeliteTag,
			loadGoals(seeds));
	}

	// ------------------------------------------------------------------ locations and patches

	private Map<String, Location> loadLocations(RawData.PatchLocations generated, Map<String, UnlockRule> rules,
		Map<String, Teleport> teleports)
	{
		Map<String, RawData.LocationSettings> settings =
			read("location_settings.json", RawData.LocationSettingsFile.class).locations;

		Map<String, Location> locations = new LinkedHashMap<>();
		for (RawData.PatchLocation raw : generated.locations)
		{
			String where = "patch_locations.json " + raw.id;
			RawData.LocationSettings set = settings.get(raw.id);
			if (set == null)
			{
				throw new DataException("location_settings.json has no entry for location " + raw.id);
			}
			if (set.teleports == null || set.teleports.isEmpty())
			{
				throw new DataException("location_settings.json " + raw.id + ": no teleports");
			}
			for (String ref : set.teleports)
			{
				if (GameData.resolve(teleports, ref) == null)
				{
					throw new DataException("location_settings.json " + raw.id + ": unknown teleport " + ref
						+ " (a method id from teleports.json, plus :destination when it has several)");
				}
			}
			if (set.unlock != null)
			{
				requireKey(rules, set.unlock, "location_settings.json " + raw.id + ": unlock");
			}
			if (set.diseaseFree != null)
			{
				requireKey(rules, set.diseaseFree, "location_settings.json " + raw.id + ": diseaseFree");
			}
			Map<String, String> patchUnlocks = set.patchUnlocks == null ? Map.of() : set.patchUnlocks;

			List<Patch> patches = new ArrayList<>();
			for (RawData.RawPatch p : raw.patches)
			{
				String rule = patchUnlocks.get(p.id);
				if (rule != null)
				{
					requireKey(rules, rule, "location_settings.json " + raw.id + ": patchUnlocks " + p.id);
				}
				patches.add(new Patch(p.id, raw.id, p.name, kind(p.kind, where), raw.regionId,
					GameIds.varbit(p.varbit, where + " " + p.id),
					p.gardener == null ? Patch.NO_GARDENER : GameIds.npc(p.gardener, where + " " + p.id),
					p.tile == null ? null : point(p.tile), p.stand == null ? null : point(p.stand), rule));
			}
			for (String patchId : patchUnlocks.keySet())
			{
				if (patches.stream().noneMatch(p -> p.getId().equals(patchId)))
				{
					throw new DataException("location_settings.json " + raw.id + ": patchUnlocks names unknown patch "
						+ patchId);
				}
			}
			if (raw.target == null)
			{
				throw new DataException(where + ": no target tile");
			}
			// Shortest Path is pointed at the target, so use the walkable tile beside it when there is one.
			locations.put(raw.id, new Location(raw.id, raw.name, raw.regionId, List.copyOf(raw.extraRegionIds),
				point(raw.targetStand != null ? raw.targetStand : raw.target), List.copyOf(patches), List.copyOf(set.teleports), set.unlock, set.note,
				set.diseaseFree, set.approach == null ? null : point(set.approach), List.copyOf(raw.wikiTeleports)));
		}
		for (String id : settings.keySet())
		{
			requireKey(locations, id, "location_settings.json: location");
		}
		return locations;
	}

	private CostTable loadCosts(Map<String, Location> locations)
	{
		CostTable costs;
		try (Reader reader = open("costs.tsv"))
		{
			costs = CostTable.parse(reader);
		}
		catch (IOException e)
		{
			throw new DataException("costs.tsv: can't read", e);
		}
		for (String id : locations.keySet())
		{
			if (!costs.locationsWithArriveCost().contains(id))
			{
				throw new DataException("costs.tsv: no arrive cost for location " + id);
			}
		}
		for (String id : costs.locationsWithArriveCost())
		{
			requireKey(locations, id, "costs.tsv arrive");
		}
		for (String pair : costs.linkedPairs())
		{
			for (String id : pair.split("\\|"))
			{
				requireKey(locations, id, "costs.tsv link " + pair);
			}
		}
		Map<String, WorldPoint> places = new HashMap<>();
		locations.forEach((id, location) -> places.put(id, location.getTarget()));
		return costs.withPlaces(places);
	}

	// ------------------------------------------------------------------ seeds, teleports, items, rules

	private List<Seed> loadSeeds()
	{
		List<Seed> seeds = new ArrayList<>();
		for (RawData.RawSeed raw : read("seeds.json", RawData.Seeds.class).seeds)
		{
			String where = "seeds.json " + raw.name;
			List<ItemAmount> payment = new ArrayList<>();
			for (RawData.Payment p : raw.payment)
			{
				payment.add(new ItemAmount(p.item, requireId(p.itemId, where + " payment " + p.item), p.amount));
			}
			int sapling = raw.sapling == null ? Seed.NO_SAPLING : requireId(raw.sapling.itemId, where + " sapling");
			seeds.add(new Seed(raw.name, requireId(raw.itemId, where), kind(raw.kind, where), raw.level,
				raw.perPatch, sapling, raw.sapling == null ? null : raw.sapling.name, List.copyOf(payment),
				SeedXp.from(raw.xp), raw.growth));
		}
		return seeds;
	}

	private List<GoalPreset> loadGoals(List<Seed> seeds)
	{
		Map<String, Seed> byName = new HashMap<>();
		seeds.forEach(s -> byName.put(s.getName(), s));
		List<GoalPreset> goals = new ArrayList<>();
		for (RawData.Goal raw : read("goal_presets.json", RawData.Goals.class).presets)
		{
			List<GoalPreset.Crop> crops = new ArrayList<>();
			for (RawData.GoalCrop c : raw.crops)
			{
				Seed seed = byName.get(c.seed);
				if (seed == null || !seed.getKind().name().equals(c.kind))
				{
					throw new DataException("goal_presets.json " + raw.id + ": no " + c.kind + " seed called " + c.seed);
				}
				crops.add(new GoalPreset.Crop(seed, c.perProduct, c.avgYieldPerPatch));
			}
			goals.add(new GoalPreset(raw.id, raw.name, raw.category, raw.why == null ? "" : raw.why, List.copyOf(crops),
				raw.notFarmed == null ? List.of() : List.copyOf(raw.notFarmed), raw.minFarmingLevel));
		}
		return List.copyOf(goals);
	}

	private Map<String, Teleport> loadTeleports()
	{
		Map<String, Teleport> teleports = new LinkedHashMap<>();
		for (RawData.RawTeleport raw : read("teleports.json", RawData.Teleports.class).teleports)
		{
			String where = "teleports.json " + raw.id;
			TeleportType type;
			try
			{
				type = TeleportType.valueOf(raw.type.toUpperCase());
			}
			catch (IllegalArgumentException e)
			{
				throw new DataException(where + ": unknown type " + raw.type, e);
			}
			List<ItemAmount> runes = raw.runes == null ? List.of() : raw.runes.stream()
				.map(r -> new ItemAmount(r.rune, r.itemId, r.amount))
				.collect(Collectors.toUnmodifiableList());
			Set<Integer> itemIds = raw.itemIds == null ? Set.of() : Set.copyOf(raw.itemIds);
			int waived = raw.waivedByVarbit == null ? Teleport.NOT_WAIVED : GameIds.varbit(raw.waivedByVarbit, where);
			List<TeleportDestination> destinations = raw.destinations == null ? List.of() : raw.destinations.stream()
				.map(d -> new TeleportDestination(d.id, d.name, d.detail,
					d.itemIds == null ? Set.of() : Set.copyOf(d.itemIds)))
				.collect(Collectors.toUnmodifiableList());
			teleports.put(raw.id, new Teleport(raw.id, raw.name, raw.group, type, itemIds, raw.spellbook, runes, waived,
				raw.usedUp, destinations));
		}
		return teleports;
	}

	private ItemCatalog loadItems()
	{
		RawData.Items raw = read("items.json", RawData.Items.class);
		Map<String, Set<Integer>> groups = new HashMap<>();
		raw.groups.forEach((name, ids) -> groups.put(name, Set.copyOf(ids)));
		Map<String, Spell> spells = new HashMap<>();
		if (raw.spells != null)
		{
			raw.spells.forEach((id, s) -> spells.put(id, new Spell(id, s.name, s.spellbook, s.runes.stream()
				.map(r -> new ItemAmount(r.rune, r.itemId, r.amount))
				.collect(Collectors.toUnmodifiableList()))));
		}
		return new ItemCatalog(Map.copyOf(groups), raw.descriptions == null ? Map.of() : Map.copyOf(raw.descriptions),
			Map.copyOf(raw.runes), Map.copyOf(spells));
	}

	private Map<String, UnlockRule> loadRules()
	{
		Map<String, UnlockRule> rules = new LinkedHashMap<>();
		read("unlocks.json", RawData.Unlocks.class).rules.forEach((id, raw) ->
		{
			List<UnlockCondition> conditions = new ArrayList<>();
			for (RawData.Condition c : raw.all)
			{
				conditions.add(condition(c, "unlocks.json " + id));
			}
			rules.put(id, new UnlockRule(id, raw.name, List.copyOf(conditions)));
		});
		return rules;
	}

	private static UnlockCondition condition(RawData.Condition c, String where)
	{
		try
		{
			if (c.quest != null)
			{
				return new UnlockCondition(Quest.valueOf(c.quest), Boolean.TRUE.equals(c.started),
					UnlockCondition.NO_VARBIT, null, null, 0);
			}
			if (c.varbit != null)
			{
				return new UnlockCondition(null, false, GameIds.varbit(c.varbit, where), c.varbit, null, 0);
			}
			if (c.skill != null && c.level != null)
			{
				return new UnlockCondition(null, false, UnlockCondition.NO_VARBIT, null, Skill.valueOf(c.skill),
					c.level);
			}
		}
		catch (IllegalArgumentException e)
		{
			throw new DataException(where + ": unknown quest or skill name", e);
		}
		throw new DataException(where + ": a condition needs a quest, a varbit, or a skill and level");
	}

	// ------------------------------------------------------------------ helpers

	private <T> T read(String file, Class<T> type)
	{
		try (Reader reader = open(file))
		{
			T value = gson.fromJson(reader, type);
			if (value == null)
			{
				throw new DataException(file + ": empty");
			}
			return value;
		}
		catch (IOException | JsonParseException e)
		{
			throw new DataException(file + ": can't read: " + e.getMessage(), e);
		}
	}

	private static Reader open(String file)
	{
		InputStream in = DataLoader.class.getResourceAsStream("/com/layneburns/farmingruns/" + file);
		if (in == null)
		{
			throw new DataException(file + ": missing from the plugin's resources");
		}
		return new InputStreamReader(in, StandardCharsets.UTF_8);
	}

	private static PatchImplementation kind(String name, String where)
	{
		try
		{
			return PatchImplementation.valueOf(name);
		}
		catch (IllegalArgumentException | NullPointerException e)
		{
			throw new DataException(where + ": unknown patch kind " + name, e);
		}
	}

	private static int requireId(Integer itemId, String where)
	{
		if (itemId == null)
		{
			throw new DataException(where + ": no item id (re-run the script that generates this file)");
		}
		return itemId;
	}

	private static void requireKey(Map<String, ?> map, String key, String where)
	{
		if (key == null || !map.containsKey(key))
		{
			throw new DataException(where + ": unknown id " + key);
		}
	}

	private static WorldPoint point(RawData.Tile tile)
	{
		return new WorldPoint(tile.x, tile.y, tile.plane);
	}
}
