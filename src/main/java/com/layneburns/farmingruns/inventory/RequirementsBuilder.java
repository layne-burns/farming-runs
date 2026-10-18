/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.ItemAmount;
import com.layneburns.farmingruns.data.ItemCatalog;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.data.Spell;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.inventory.Requirement.Importance;
import com.layneburns.farmingruns.run.BountyAmulet;
import com.layneburns.farmingruns.run.CompostChoice;
import com.layneburns.farmingruns.run.KindPlan;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.run.Protection;
import com.layneburns.farmingruns.run.RunSetup;
import com.layneburns.farmingruns.run.TreeRoutine;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.PlayerFacts;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Works out what a run needs (SPEC §2.4): the seeds and saplings, compost, the gardeners' payments, coins or an axe
 * for clearing trees, tools, and the items or runes for each stop's teleport. Recommended extras (magic secateurs,
 * a herb sack on herb runs, the farmer's outfit) are only listed when you own them; diving gear when a patch is
 * underwater; a rake only without autoweed. Pure: unit tested.
 */
public final class RequirementsBuilder
{
	private static final int REMOVAL_COINS = 200;
	/** Patches under the sea: you need diving gear to reach them. */
	private static final Set<PatchImplementation> UNDERWATER = Set.of(PatchImplementation.SEAWEED,
		PatchImplementation.CORAL);
	/** Farmer's outfit groups in items.json, with their names. */
	private static final Map<String, String> OUTFIT = new LinkedHashMap<>();

	static
	{
		OUTFIT.put("farmers_strawhat", "Farmer's strawhat");
		OUTFIT.put("farmers_jacket", "Farmer's jacket or shirt");
		OUTFIT.put("farmers_boro_trousers", "Farmer's boro trousers");
		OUTFIT.put("farmers_boots", "Farmer's boots");
	}

	private RequirementsBuilder()
	{
	}

	/**
	 * @param plan         what goes in each patch the run visits
	 * @param teleportRefs your ranked teleports for each location, by location id
	 * @param ledger       your items everywhere, to pick teleports you own and the extras you have
	 * @param treeGrown    whether a tree patch holds a grown tree to clear (unknown should count as grown)
	 * @param autoweed     autoweed is on, so weeds don't grow back and no rake is needed
	 */
	public static RunRequirements build(RunSetup setup, PlantingPlan plan, Map<String, List<String>> teleportRefs,
		GameData data, PlayerFacts facts, ItemLedger ledger, Predicate<Patch> treeGrown, boolean autoweed)
	{
		List<PlantingPlan.Assignment> assignments = plan.getAssignments();
		if (assignments.isEmpty())
		{
			return RunRequirements.NONE;
		}
		RequirementList list = new RequirementList();
		List<String> notes = new ArrayList<>();
		ItemCatalog items = data.getItems();

		addSeeds(list, assignments, plan.getSaplingsToGrow());
		addCompost(list, notes, setup.getCompost(), (int) plan.plantedCount(), items, facts);
		addPayments(list, setup, assignments, data, facts);
		addTreeClearing(list, setup, assignments, data, items, treeGrown);

		list.add("Spade", items.group("spade"), 1, Importance.NEED, true, "harvesting and clearing", false);
		if (!autoweed)
		{
			list.add("Rake", items.group("rake"), 1, Importance.RECOMMENDED, true, "weeds", false);
		}
		addIfOwned(list, ledger, "Magic secateurs", items.group("magic_secateurs"), true,
			"+10% herbs, allotment crops and hops");
		if (assignments.stream().anyMatch(a -> a.getPatch().getKind() == PatchImplementation.HERB))
		{
			addIfOwned(list, ledger, "Herb sack", items.group("herb_sack"), false, "picking herbs");
		}
		if (assignments.stream().anyMatch(a -> UNDERWATER.contains(a.getPatch().getKind())))
		{
			list.add("Diving apparatus", items.group("diving_apparatus"), 1, Importance.NEED, false,
				"breathing underwater", false);
			list.add("Fishbowl helmet", items.group("fishbowl_helmet"), 1, Importance.NEED, false,
				"breathing underwater", false);
		}
		List<PlantingPlan.Assignment> allotments = assignments.stream()
			.filter(a -> a.getPatch().getKind() == PatchImplementation.ALLOTMENT && a.getSeed() != null)
			.collect(Collectors.toList());
		if (!allotments.isEmpty()
			&& BountyAmulet.wanted(setup.kindPlan(PatchImplementation.ALLOTMENT), allotments, data))
		{
			list.add("Amulet of bounty", items.group("amulet_of_bounty"), 1, Importance.RECOMMENDED, false,
				"planting allotments", false);
		}
		OUTFIT.forEach((group, name) -> addIfOwned(list, ledger, name, items.group(group), false, "Farming XP"));

		Map<String, TeleportChoice> teleports = addTeleports(list, notes, assignments, teleportRefs, data, facts,
			ledger);
		return new RunRequirements(list.toList(), Map.copyOf(teleports), List.copyOf(notes));
	}

	/** Seeds and saplings by patches; saplings still to be grown from seed say so. */
	private static void addSeeds(RequirementList list, List<PlantingPlan.Assignment> assignments,
		Map<Seed, Integer> toGrow)
	{
		Map<Seed, Integer> patches = new LinkedHashMap<>();
		assignments.stream().filter(a -> a.getSeed() != null).forEach(a -> patches.merge(a.getSeed(), 1, Integer::sum));
		patches.forEach((seed, count) ->
		{
			String use = count + " " + kindName(seed.getKind()) + " patch" + (count == 1 ? "" : "es")
				+ (toGrow.containsKey(seed) ? " (grow " + toGrow.get(seed) + " from seed first)" : "");
			list.add(plantName(seed), Set.of(seed.plantItemId()), seed.getPerPatch() * count, Importance.NEED, false,
				use, true);
		});
	}

	private static void addCompost(RequirementList list, List<String> notes, CompostChoice compost, int planted,
		ItemCatalog items, PlayerFacts facts)
	{
		if (planted == 0)
		{
			return;
		}
		String use = planted + " patch" + (planted == 1 ? "" : "es");
		switch (compost)
		{
			case COMPOST:
				list.add("Compost", items.group("compost"), planted, Importance.NEED, true, use, true);
				break;
			case SUPERCOMPOST:
				list.add("Supercompost", items.group("supercompost"), planted, Importance.NEED, true, use, true);
				break;
			case ULTRACOMPOST:
				list.add("Ultracompost", items.group("ultracompost"), planted, Importance.NEED, true, use, true);
				break;
			case BOTTOMLESS_BUCKET:
				list.add("Bottomless compost bucket (filled)", items.group("bottomless_compost_bucket"), 1,
					Importance.NEED, true, use, false);
				break;
			case FERTILE_SOIL:
				Spell spell = items.spell("fertile_soil");
				String book = facts.spellbookName();
				if (book != null && !book.equals(spell.getSpellbook()))
				{
					notes.add(spell.getName() + " needs the " + spell.getSpellbook() + " spellbook (you're on " + book
						+ ")");
				}
				for (ItemAmount rune : spell.getRunes())
				{
					list.add(rune.getName(), Set.of(rune.getItemId()), rune.getAmount() * planted, Importance.NEED,
						false, spell.getName() + " × " + planted, true);
				}
				break;
			default:
				break;
		}
	}

	/** The gardener's payment for each patch that's to be paid for ({@link Protection}). */
	private static void addPayments(RequirementList list, RunSetup setup, List<PlantingPlan.Assignment> assignments,
		GameData data, PlayerFacts facts)
	{
		for (PlantingPlan.Assignment a : assignments)
		{
			Seed seed = a.getSeed();
			if (!Protection.shouldPay(a, setup, data, facts))
			{
				continue;
			}
			for (ItemAmount payment : seed.getPayment())
			{
				list.add(payment.getName(), Set.of(payment.getItemId()), payment.getAmount(), Importance.NEED, false,
					"protecting " + plantName(seed), true);
			}
		}
	}

	/** 200 coins per grown tree the gardener removes, or an axe to chop them yourself. Trees kept standing aren't cleared. */
	private static void addTreeClearing(RequirementList list, RunSetup setup, List<PlantingPlan.Assignment> assignments,
		GameData data, ItemCatalog items, Predicate<Patch> treeGrown)
	{
		int paid = 0;
		int chopped = 0;
		for (PlantingPlan.Assignment a : assignments)
		{
			Patch patch = a.getPatch();
			KindPlan plan = setup.kindPlan(patch.getKind());
			if (!isTree(patch.getKind(), data) || a.isKeptStanding() || !treeGrown.test(patch))
			{
				continue;
			}
			if (plan.getTreeRoutine() == TreeRoutine.CHOP_AND_DIG)
			{
				chopped++;
			}
			else if (patch.hasGardener())
			{
				paid++;
			}
		}
		if (paid > 0)
		{
			list.add("Coins", items.group("coins"), REMOVAL_COINS * paid, Importance.NEED, false,
				"removing " + paid + " tree" + (paid == 1 ? "" : "s"), true);
		}
		if (chopped > 0)
		{
			list.add("Axe", items.group("axe"), 1, Importance.NEED, false,
				"chopping " + chopped + " tree" + (chopped == 1 ? "" : "s"), false);
		}
	}

	/** Picks each visited location's teleport and adds what it takes. Returns the picks by location id. */
	private static Map<String, TeleportChoice> addTeleports(RequirementList list, List<String> notes,
		List<PlantingPlan.Assignment> assignments, Map<String, List<String>> teleportRefs, GameData data,
		PlayerFacts facts, ItemLedger ledger)
	{
		Set<String> visited = new LinkedHashSet<>();
		assignments.forEach(a -> visited.add(a.getPatch().getLocationId()));
		Map<String, TeleportChoice> picks = new LinkedHashMap<>();
		for (String locationId : visited)
		{
			Location location = data.location(locationId);
			TeleportChoice choice = TeleportPicker.pick(teleportRefs.getOrDefault(locationId, List.of()), data, facts,
				ledger);
			if (choice == null)
			{
				notes.add("No usable teleport for " + location.getName() + ": you'll walk there");
				continue;
			}
			picks.put(locationId, choice);
			switch (choice.getMethod().getType())
			{
				case ITEM:
					list.add(choice.getMethod().getName(), choice.itemIds(), 1, Importance.NEED, false,
						location.getName(), choice.getMethod().isUsedUp());
					break;
				case SPELL:
					for (ItemAmount rune : choice.getMethod().getRunes())
					{
						list.add(rune.getName(), Set.of(rune.getItemId()), rune.getAmount(), Importance.NEED, false,
							choice.displayName(), true);
					}
					break;
				case HOUSE:
					addHouseAccess(list, choice, data.getItems(), facts, ledger,
						choice.displayName() + " (" + location.getName() + ")");
					break;
				case FAIRY_RING:
					if (!TeleportPicker.waived(choice.getMethod(), facts))
					{
						list.add("Dramen or lunar staff", choice.itemIds(), 1, Importance.NEED, false,
							choice.displayName() + " (" + location.getName() + ")", false);
					}
					break;
				default:
					break;
			}
		}
		return picks;
	}

	/**
	 * A way into your house for a portal: your construction cape if you have one, else a tablet per use if you have
	 * any, else the spell's runes on the standard spellbook, else tablets (missing).
	 */
	private static void addHouseAccess(RequirementList list, TeleportChoice choice, ItemCatalog items,
		PlayerFacts facts, ItemLedger ledger, String use)
	{
		Set<Integer> cape = items.group("construction_cape");
		Set<Integer> tablets = items.group("house_tablet");
		if (ledger.countAny(cape) > 0)
		{
			list.add("Construction cape", cape, 1, Importance.NEED, false, use, false);
		}
		else if (ledger.countAny(tablets) == 0 && TeleportPicker.usableSpell(choice.getMethod(), facts)
			&& TeleportPicker.hasRunes(choice.getMethod(), ledger))
		{
			for (ItemAmount rune : choice.getMethod().getRunes())
			{
				list.add(rune.getName(), Set.of(rune.getItemId()), rune.getAmount(), Importance.NEED, false,
					"Teleport to House: " + use, true);
			}
		}
		else
		{
			list.add("Teleport to house tablet", tablets, 1, Importance.NEED, false, use, true);
		}
	}

	private static void addIfOwned(RequirementList list, ItemLedger ledger, String label, Set<Integer> ids,
		boolean leprechaun, String use)
	{
		if (ledger.countAny(ids) > 0)
		{
			list.add(label, ids, 1, Importance.RECOMMENDED, leprechaun, use, false);
		}
	}

	/** Kinds planted as saplings have a grown tree to clear. */
	private static boolean isTree(PatchImplementation kind, GameData data)
	{
		return data.seedsFor(kind).stream().anyMatch(Seed::isPlantedAsSapling);
	}

	private static String plantName(Seed seed)
	{
		return seed.isPlantedAsSapling() ? seed.getSaplingName() : seed.getName();
	}

	static String kindName(PatchImplementation kind)
	{
		return kind.name().toLowerCase().replace('_', ' ');
	}
}
