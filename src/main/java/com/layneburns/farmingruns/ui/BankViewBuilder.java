/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.ItemAmount;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.data.Teleport;
import com.layneburns.farmingruns.data.TeleportType;
import com.layneburns.farmingruns.state.BufferSettings;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.gameval.ItemID;

/**
 * Builds the Bank tab's contents (SPEC §2.2) from the game data and where your items are. Pure: no client, no
 * Swing, so it's unit tested.
 */
public final class BankViewBuilder
{
	/** How the seed page is organised: section title, then the patch kinds listed under it. */
	private static final Map<String, List<PatchImplementation>> SEED_SECTIONS = new LinkedHashMap<>();

	static
	{
		SEED_SECTIONS.put("Herbs & flowers", List.of(PatchImplementation.HERB, PatchImplementation.FLOWER));
		SEED_SECTIONS.put("Allotments & hops", List.of(PatchImplementation.ALLOTMENT, PatchImplementation.HOPS));
		SEED_SECTIONS.put("Bushes & cacti", List.of(PatchImplementation.BUSH, PatchImplementation.CACTUS));
		SEED_SECTIONS.put("Trees", List.of(PatchImplementation.TREE, PatchImplementation.FRUIT_TREE,
			PatchImplementation.HARDWOOD_TREE, PatchImplementation.CALQUAT, PatchImplementation.CELASTRUS,
			PatchImplementation.REDWOOD, PatchImplementation.SPIRIT_TREE, PatchImplementation.CRYSTAL_TREE));
		SEED_SECTIONS.put("Special", List.of(PatchImplementation.MUSHROOM, PatchImplementation.BELLADONNA,
			PatchImplementation.GRAPES, PatchImplementation.SEAWEED, PatchImplementation.CORAL,
			PatchImplementation.HESPORI, PatchImplementation.ANIMA));
	}

	/** Places seeds are counted from (not the leprechaun, which can't hold them). */
	private static final List<ItemSource> SEED_SOURCES = List.of(
		ItemSource.BANK, ItemSource.SEED_VAULT, ItemSource.SEED_BOX, ItemSource.INVENTORY);

	private static final List<ItemSource> ALL_SOURCES = List.of(ItemSource.values());

	private static final List<Integer> COMPOSTS = List.of(
		ItemID.BUCKET_COMPOST, ItemID.BUCKET_SUPERCOMPOST, ItemID.BUCKET_ULTRACOMPOST);

	private BankViewBuilder()
	{
	}

	public static BankView build(GameData data, ItemLedger ledger, BufferSettings buffers, int bottomlessUses,
		long nowMillis)
	{
		List<BankView.Section> sections = new ArrayList<>(seedSections(data, ledger, buffers));
		if (sections.stream().allMatch(BankView.Section::isEmpty))
		{
			sections.add(new BankView.Section(BankView.Page.SEEDS, "Seeds", List.of(),
				"No seeds seen yet. Open your bank and seed vault to fill the seed log."));
		}
		sections.add(single(BankView.Page.SUPPLIES, "Compost", compost(data, ledger, bottomlessUses),
			"No compost seen."));
		sections.add(single(BankView.Page.SUPPLIES, "Protection payments", payments(data, ledger),
			"No payment items seen."));
		sections.add(single(BankView.Page.SUPPLIES, "Teleport items", teleports(data, ledger),
			"No teleport items seen."));
		sections.add(single(BankView.Page.LEPRECHAUN, "Tool leprechaun", leprechaun(data, ledger),
			"Nothing stored, or not logged in."));
		return new BankView(seen(ledger, nowMillis), sections);
	}

	// ------------------------------------------------------------------ seeds

	private static List<BankView.Section> seedSections(GameData data, ItemLedger ledger, BufferSettings buffers)
	{
		List<BankView.Section> sections = new ArrayList<>();
		SEED_SECTIONS.forEach((title, kinds) ->
		{
			List<BankView.Group> groups = new ArrayList<>();
			for (PatchImplementation kind : kinds)
			{
				List<Seed> seeds = data.seedsFor(kind);
				if (seeds.stream().anyMatch(Seed::isPlantedAsSapling))
				{
					// Trees: saplings (what gets planted) and seeds (still to be potted) are listed apart.
					addGroup(groups, kindName(kind) + " saplings", seeds.stream()
						.map(seed -> saplingRow(seed, ledger, buffers)));
					addGroup(groups, kindName(kind) + " seeds", seeds.stream()
						.map(seed -> treeSeedRow(seed, ledger)));
				}
				else
				{
					addGroup(groups, kindName(kind), seeds.stream().map(seed -> seedRow(seed, ledger, buffers)));
				}
			}
			sections.add(new BankView.Section(BankView.Page.SEEDS, title, groups, null));
		});
		return sections;
	}

	private static void addGroup(List<BankView.Group> groups, String title, Stream<BankView.Row> rows)
	{
		List<BankView.Row> kept = rows.filter(Objects::nonNull).collect(Collectors.toList());
		if (!kept.isEmpty())
		{
			groups.add(new BankView.Group(title, kept));
		}
	}

	/** A seed planted directly (herbs, allotments...), or null if you have none. */
	private static BankView.Row seedRow(Seed seed, ItemLedger ledger, BufferSettings buffers)
	{
		int seeds = count(ledger, seed.getItemId(), SEED_SOURCES);
		return seeds == 0 ? null : new BankView.Row(seed.getItemId(), seed.getName(), String.valueOf(seeds),
			buffers.spare(seed, seeds) + " spare", where(ledger, seed.getItemId(), SEED_SOURCES));
	}

	/** A tree's saplings, with the sapling buffer applied, or null if you have none. */
	private static BankView.Row saplingRow(Seed seed, ItemLedger ledger, BufferSettings buffers)
	{
		int saplings = count(ledger, seed.getSaplingItemId(), SEED_SOURCES);
		return saplings == 0 ? null : new BankView.Row(seed.getSaplingItemId(), seed.getSaplingName(),
			String.valueOf(saplings), buffers.spare(seed, saplings) + " spare",
			where(ledger, seed.getSaplingItemId(), SEED_SOURCES));
	}

	/** A tree's unpotted seeds (no buffer: the buffer applies to saplings), or null if you have none. */
	private static BankView.Row treeSeedRow(Seed seed, ItemLedger ledger)
	{
		int seeds = count(ledger, seed.getItemId(), SEED_SOURCES);
		return seeds == 0 ? null : new BankView.Row(seed.getItemId(), seed.getName(), String.valueOf(seeds), null,
			where(ledger, seed.getItemId(), SEED_SOURCES));
	}

	// ------------------------------------------------------------------ supplies

	private static List<BankView.Row> compost(GameData data, ItemLedger ledger, int bottomlessUses)
	{
		List<BankView.Row> rows = new ArrayList<>();
		for (int id : COMPOSTS)
		{
			int total = ledger.count(id);
			if (total > 0)
			{
				int stored = ledger.count(id, ItemSource.LEPRECHAUN);
				rows.add(new BankView.Row(id, data.getItems().displayName(id), String.valueOf(total),
					stored > 0 ? stored + " in leprechaun" : null, where(ledger, id, ALL_SOURCES)));
			}
		}
		Set<Integer> bucket = data.getItems().group("bottomless_compost_bucket");
		if (ledger.countAny(bucket) > 0)
		{
			int id = bucket.iterator().next();
			rows.add(new BankView.Row(id, "Bottomless bucket", bottomlessUses > 0 ? String.valueOf(bottomlessUses) : "-",
				bottomlessUses > 0 ? "uses left (leprechaun)" : "filled", where(ledger, id, ALL_SOURCES)));
		}
		return rows;
	}

	private static List<BankView.Row> payments(GameData data, ItemLedger ledger)
	{
		// Each payment item once, with the crops it protects.
		Map<Integer, List<String>> paysFor = new LinkedHashMap<>();
		Map<Integer, String> names = new LinkedHashMap<>();
		for (Seed seed : data.seeds())
		{
			for (ItemAmount payment : seed.getPayment())
			{
				names.putIfAbsent(payment.getItemId(), payment.getName());
				paysFor.computeIfAbsent(payment.getItemId(), k -> new ArrayList<>())
					.add(cropName(seed) + " (" + payment.getAmount() + ")");
			}
		}
		return names.entrySet().stream()
			.filter(e -> ledger.count(e.getKey()) > 0)
			.sorted(Map.Entry.comparingByValue())
			.map(e ->
			{
				String crops = String.join(", ", paysFor.get(e.getKey()));
				return new BankView.Row(e.getKey(), e.getValue(), String.valueOf(ledger.count(e.getKey())),
					"for " + crops, where(ledger, e.getKey(), ALL_SOURCES) + " · Pays for " + crops);
			})
			.collect(Collectors.toList());
	}

	private static List<BankView.Row> teleports(GameData data, ItemLedger ledger)
	{
		List<BankView.Row> rows = new ArrayList<>();
		for (Teleport teleport : data.teleports())
		{
			if (teleport.getType() != TeleportType.ITEM && teleport.getType() != TeleportType.FAIRY_RING)
			{
				continue;
			}
			int total = ledger.countAny(teleport.getItemIds());
			if (total > 0)
			{
				int icon = teleport.getItemIds().stream().max(Comparator.comparingInt(ledger::count)).orElseThrow();
				rows.add(new BankView.Row(icon, teleport.getName(), String.valueOf(total), null,
					whereAny(ledger, teleport.getItemIds())));
			}
		}
		return rows;
	}

	private static List<BankView.Row> leprechaun(GameData data, ItemLedger ledger)
	{
		return ledger.snapshot(ItemSource.LEPRECHAUN).getItems().entrySet().stream()
			.map(e -> new BankView.Row(e.getKey(), leprechaunItemName(data, e.getKey()), String.valueOf(e.getValue()),
				null, "Stored with the tool leprechaun"))
			.sorted(Comparator.comparing(BankView.Row::getLabel))
			.collect(Collectors.toList());
	}

	// ------------------------------------------------------------------ helpers

	private static BankView.Section single(BankView.Page page, String title, List<BankView.Row> rows, String empty)
	{
		return new BankView.Section(page, title, List.of(new BankView.Group(null, rows)), empty);
	}

	private static String leprechaunItemName(GameData data, int itemId)
	{
		if (itemId == ItemID.BOTTOMLESS_COMPOST_BUCKET)
		{
			return "Bottomless bucket (empty)";
		}
		String name = data.getItems().displayName(itemId);
		return name != null ? name : "Item " + itemId;
	}

	/** "Apple tree seed" becomes "Apple tree", "Acorn" stays: the crop a payment protects. */
	private static String cropName(Seed seed)
	{
		return seed.getName().replaceAll(" (seed|spore)$", "");
	}

	private static int count(ItemLedger ledger, int itemId, List<ItemSource> sources)
	{
		int total = 0;
		for (ItemSource source : sources)
		{
			total += ledger.count(itemId, source);
		}
		return total;
	}

	/** E.g. "Bank 10, Seed vault 2", or "none" when there are none. */
	static String where(ItemLedger ledger, int itemId, Collection<ItemSource> sources)
	{
		String text = sources.stream()
			.filter(s -> ledger.count(itemId, s) > 0)
			.map(s -> s.getDisplayName() + " " + ledger.count(itemId, s))
			.collect(Collectors.joining(", "));
		return text.isEmpty() ? "none" : text;
	}

	private static String whereAny(ItemLedger ledger, Set<Integer> itemIds)
	{
		List<String> parts = new ArrayList<>();
		for (ItemSource source : ItemSource.values())
		{
			int total = 0;
			for (int id : itemIds)
			{
				total += ledger.count(id, source);
			}
			if (total > 0)
			{
				parts.add(source.getDisplayName() + " " + total);
			}
		}
		return String.join(", ", parts);
	}

	static String kindName(PatchImplementation kind)
	{
		String words = kind.name().toLowerCase().replace('_', ' ');
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	static List<String> seen(ItemLedger ledger, long nowMillis)
	{
		return List.of(
			"Bank: " + age(ledger.snapshot(ItemSource.BANK).getCapturedAtMillis(), nowMillis),
			"Vault: " + age(ledger.snapshot(ItemSource.SEED_VAULT).getCapturedAtMillis(), nowMillis),
			"Box: " + age(ledger.snapshot(ItemSource.SEED_BOX).getCapturedAtMillis(), nowMillis));
	}

	/** A short age: "now", "5m", "3h", "2d", or "never". */
	static String age(long capturedAtMillis, long nowMillis)
	{
		if (capturedAtMillis <= 0)
		{
			return "never";
		}
		long minutes = Math.max(0, (nowMillis - capturedAtMillis) / 60_000);
		if (minutes < 1)
		{
			return "now";
		}
		if (minutes < 60)
		{
			return minutes + "m";
		}
		long hours = minutes / 60;
		return hours < 48 ? hours + "h" : hours / 24 + "d";
	}
}
