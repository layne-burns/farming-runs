/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.util.List;
import java.util.Map;

/**
 * The shapes of the JSON files, exactly as written on disk, for Gson to fill. No logic lives here:
 * {@link DataLoader} turns these into the checked, immutable classes the rest of the plugin uses.
 */
final class RawData
{
	private RawData()
	{
	}

	static class Tile
	{
		int x;
		int y;
		int plane;
	}

	/** patch_locations.json (generated). */
	static class PatchLocations
	{
		String runeliteTag;
		List<PatchLocation> locations;
	}

	static class PatchLocation
	{
		String id;
		String name;
		int regionId;
		List<Integer> extraRegionIds;
		Tile target;
		/** The nearest walkable tile to the target (added by scripts/snap_to_walkable.py). */
		Tile targetStand;
		List<String> wikiTeleports;
		List<RawPatch> patches;
	}

	static class RawPatch
	{
		String id;
		String name;
		String kind;
		String varbit;
		String gardener;
		Tile tile;
		/** The nearest walkable tile beside the patch (added by scripts/snap_to_walkable.py). */
		Tile stand;
	}

	/** location_settings.json (hand-edited). */
	static class LocationSettingsFile
	{
		Map<String, LocationSettings> locations;
	}

	static class LocationSettings
	{
		List<String> teleports;
		String unlock;
		/** A rule that, once met, makes the location's patches disease-free (no payment needed). */
		String diseaseFree;
		/** Where Shortest Path should take you first when the patches can't be walked to (underwater). */
		Tile approach;
		String note;
		Map<String, String> patchUnlocks;
	}

	/** seeds.json (generated). */
	static class Seeds
	{
		List<RawSeed> seeds;
	}

	static class RawSeed
	{
		String name;
		Integer itemId;
		String kind;
		int level;
		int perPatch;
		NamedItem sapling;
		List<Payment> payment;
		Map<String, Double> xp;
		String growth;
	}

	static class NamedItem
	{
		String name;
		Integer itemId;
		/** Saplings only: the watered seedling that grows into it. */
		Integer wateredSeedlingId;
	}

	static class Payment
	{
		String item;
		Integer itemId;
		int amount;
	}

	/** teleports.json (generated). */
	static class Teleports
	{
		List<RawTeleport> teleports;
	}

	static class RawTeleport
	{
		String id;
		String name;
		String group;
		String type;
		List<Integer> itemIds;
		String spellbook;
		List<Rune> runes;
		String waivedByVarbit;
		boolean usedUp;
		List<Destination> destinations;
	}

	static class Destination
	{
		String id;
		String name;
		String detail;
		List<Integer> itemIds;
		/** x, y, plane. */
		List<Integer> landing;
	}

	static class Rune
	{
		String rune;
		int itemId;
		int amount;
	}

	/** unlocks.json (hand-edited). */
	static class Unlocks
	{
		Map<String, Rule> rules;
	}

	static class Rule
	{
		String name;
		List<Condition> all;
	}

	static class Condition
	{
		String quest;
		Boolean started;
		String varbit;
		String skill;
		Integer level;
	}

	/** goal_presets.json (hand-reviewed research). */
	static class Goals
	{
		List<Goal> presets;
	}

	static class Goal
	{
		String id;
		String name;
		String category;
		String why;
		List<GoalCrop> crops;
		List<String> notFarmed;
		int minFarmingLevel;
	}

	static class GoalCrop
	{
		String seed;
		String kind;
		double perProduct;
		double avgYieldPerPatch;
	}

	/** items.json (generated). */
	static class Items
	{
		Map<String, List<Integer>> groups;
		Map<String, String> descriptions;
		Map<String, Integer> runes;
		Map<String, RawSpell> spells;
		List<RawPack> packs;
	}

	/** compost_bin.json (hand-edited). */
	static class CompostBin
	{
		List<RawCompostable> items;
	}

	static class RawCompostable
	{
		String name;
		String item;
		String shop;
	}

	static class RawPack
	{
		String name;
		String produce;
		Integer produceId;
		String container;
		List<Integer> fills;
	}

	static class RawSpell
	{
		String name;
		String spellbook;
		List<Rune> runes;
	}
}
