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
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;

/**
 * Builds the Bank tab's contents (SPEC §2.2) from the game data and where your items are. Pure: no client, no
 * Swing, so it's unit tested.
 */
public final class BankViewBuilder
{
	/** Kinds in the order the seed log lists them. */
	private static final List<PatchImplementation> KIND_ORDER = List.of(
		PatchImplementation.HERB, PatchImplementation.ALLOTMENT, PatchImplementation.FLOWER, PatchImplementation.HOPS,
		PatchImplementation.BUSH, PatchImplementation.TREE, PatchImplementation.FRUIT_TREE,
		PatchImplementation.HARDWOOD_TREE, PatchImplementation.CALQUAT, PatchImplementation.CELASTRUS,
		PatchImplementation.REDWOOD, PatchImplementation.SPIRIT_TREE, PatchImplementation.CRYSTAL_TREE,
		PatchImplementation.CACTUS, PatchImplementation.MUSHROOM, PatchImplementation.BELLADONNA,
		PatchImplementation.GRAPES, PatchImplementation.SEAWEED, PatchImplementation.CORAL,
		PatchImplementation.HESPORI, PatchImplementation.ANIMA);

	/** Places seeds are counted from (not the leprechaun, which can't hold them). */
	private static final List<ItemSource> SEED_SOURCES = List.of(
		ItemSource.BANK, ItemSource.SEED_VAULT, ItemSource.SEED_BOX, ItemSource.INVENTORY);

	private static final List<Integer> COMPOSTS = List.of(
		ItemID.BUCKET_COMPOST, ItemID.BUCKET_SUPERCOMPOST, ItemID.BUCKET_ULTRACOMPOST);

	private BankViewBuilder()
	{
	}

	public static BankView build(GameData data, ItemLedger ledger, BufferSettings buffers, int bottomlessUses,
		long nowMillis)
	{
		List<BankView.Section> sections = new ArrayList<>(seedSections(data, ledger, buffers));
		if (sections.isEmpty())
		{
			sections.add(new BankView.Section("Seeds", List.of(),
				"No seeds seen yet. Open your bank and seed vault to fill the seed log."));
		}
		sections.add(compost(data, ledger, bottomlessUses));
		sections.add(payments(data, ledger));
		sections.add(teleports(data, ledger));
		sections.add(leprechaun(data, ledger));
		return new BankView(seenLine(ledger, nowMillis), sections);
	}

	// ------------------------------------------------------------------ sections

	private static List<BankView.Section> seedSections(GameData data, ItemLedger ledger, BufferSettings buffers)
	{
		List<BankView.Section> sections = new ArrayList<>();
		for (PatchImplementation kind : KIND_ORDER)
		{
			List<BankView.Row> rows = new ArrayList<>();
			for (Seed seed : data.seedsFor(kind))
			{
				int seeds = count(ledger, seed.getItemId(), SEED_SOURCES);
				int saplings = seed.isPlantedAsSapling() ? count(ledger, seed.getSaplingItemId(), SEED_SOURCES) : 0;
				if (seeds + saplings == 0)
				{
					continue;
				}
				if (seed.isPlantedAsSapling())
				{
					String value = saplings > 0
						? saplings + " saplings (" + buffers.spare(seed, saplings) + " spare)" : "no saplings";
					if (seeds > 0)
					{
						value += " · " + seeds + " seeds";
					}
					rows.add(new BankView.Row(saplings > 0 ? seed.getSaplingItemId() : seed.getItemId(),
						seed.getName(), value, "Saplings: " + where(ledger, seed.getSaplingItemId(), SEED_SOURCES)
						+ " · Seeds: " + where(ledger, seed.getItemId(), SEED_SOURCES)));
				}
				else
				{
					rows.add(new BankView.Row(seed.getItemId(), seed.getName(),
						seeds + " (" + buffers.spare(seed, seeds) + " spare)", where(ledger, seed.getItemId(), SEED_SOURCES)));
				}
			}
			if (!rows.isEmpty())
			{
				sections.add(new BankView.Section(kindName(kind) + " seeds", rows, null));
			}
		}
		return sections;
	}

	private static BankView.Section compost(GameData data, ItemLedger ledger, int bottomlessUses)
	{
		List<BankView.Row> rows = new ArrayList<>();
		for (int id : COMPOSTS)
		{
			int total = ledger.count(id);
			if (total > 0)
			{
				rows.add(new BankView.Row(id, data.getItems().displayName(id), String.valueOf(total),
					where(ledger, id, List.of(ItemSource.values()))));
			}
		}
		Set<Integer> bucket = data.getItems().group("bottomless_compost_bucket");
		if (ledger.countAny(bucket) > 0)
		{
			String value = bottomlessUses > 0 ? bottomlessUses + " uses (leprechaun)" : "filled";
			int id = bucket.iterator().next();
			rows.add(new BankView.Row(id, "Bottomless compost bucket", value, where(ledger, id, List.of(ItemSource.values()))));
		}
		return new BankView.Section("Compost", rows, "No compost seen.");
	}

	private static BankView.Section payments(GameData data, ItemLedger ledger)
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
					.add(seed.getName() + " (" + payment.getAmount() + ")");
			}
		}
		List<BankView.Row> rows = names.entrySet().stream()
			.filter(e -> ledger.count(e.getKey()) > 0)
			.sorted(Map.Entry.comparingByValue())
			.map(e -> new BankView.Row(e.getKey(), e.getValue(), String.valueOf(ledger.count(e.getKey())),
				where(ledger, e.getKey(), List.of(ItemSource.values())) + " · Pays for: "
					+ String.join(", ", paysFor.get(e.getKey()))))
			.collect(Collectors.toList());
		return new BankView.Section("Protection payments", rows, "No payment items seen.");
	}

	private static BankView.Section teleports(GameData data, ItemLedger ledger)
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
				int icon = teleport.getItemIds().stream()
					.max(Comparator.comparingInt(ledger::count)).orElseThrow();
				rows.add(new BankView.Row(icon, teleport.getName(), String.valueOf(total),
					whereAny(ledger, teleport.getItemIds())));
			}
		}
		return new BankView.Section("Teleport items", rows, "No teleport items seen.");
	}

	private static BankView.Section leprechaun(GameData data, ItemLedger ledger)
	{
		List<BankView.Row> rows = ledger.snapshot(ItemSource.LEPRECHAUN).getItems().entrySet().stream()
			.map(e -> new BankView.Row(e.getKey(), leprechaunItemName(data, e.getKey()), String.valueOf(e.getValue()),
				"Stored with the tool leprechaun"))
			.sorted(Comparator.comparing(BankView.Row::getLabel))
			.collect(Collectors.toList());
		return new BankView.Section("Tool leprechaun", rows, "Nothing stored (or not logged in).");
	}

	// ------------------------------------------------------------------ helpers

	private static String leprechaunItemName(GameData data, int itemId)
	{
		if (itemId == ItemID.BOTTOMLESS_COMPOST_BUCKET)
		{
			return "Bottomless compost bucket (empty)";
		}
		String name = data.getItems().displayName(itemId);
		return name != null ? name : "Item " + itemId;
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

	static String seenLine(ItemLedger ledger, long nowMillis)
	{
		return List.of(ItemSource.BANK, ItemSource.SEED_VAULT, ItemSource.SEED_BOX).stream()
			.map(s -> s.getDisplayName() + ": " + age(ledger.snapshot(s).getCapturedAtMillis(), nowMillis))
			.collect(Collectors.joining(" · "));
	}

	static String age(long capturedAtMillis, long nowMillis)
	{
		if (capturedAtMillis <= 0)
		{
			return "never seen";
		}
		long minutes = Math.max(0, (nowMillis - capturedAtMillis) / 60_000);
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + " min ago";
		}
		long hours = minutes / 60;
		return hours < 48 ? hours + " h ago" : hours / 24 + " days ago";
	}
}
