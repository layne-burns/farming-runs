/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.ItemAmount;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.data.Teleport;
import com.layneburns.farmingruns.data.TeleportType;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;

/**
 * The rows of the Bank tab's Supplies and Leprechaun pages: compost, protection payments (and what they protect),
 * teleport items, and what the tool leprechaun holds. Pure, like {@link BankViewBuilder}, which calls it.
 */
final class SuppliesBuilder
{
	private static final List<ItemSource> ALL_SOURCES = List.of(ItemSource.values());

	private static final List<Integer> COMPOSTS = List.of(
		ItemID.BUCKET_COMPOST, ItemID.BUCKET_SUPERCOMPOST, ItemID.BUCKET_ULTRACOMPOST);

	private SuppliesBuilder()
	{
	}

	static List<BankView.Row> compost(GameData data, ItemLedger ledger, int bottomlessUses)
	{
		List<BankView.Row> rows = new ArrayList<>();
		for (int id : COMPOSTS)
		{
			int total = ledger.count(id);
			if (total > 0)
			{
				int stored = ledger.count(id, ItemSource.LEPRECHAUN);
				rows.add(new BankView.Row(id, data.getItems().displayName(id), String.valueOf(total),
					stored > 0 ? stored + " in leprechaun" : null,
					BankTooltips.supply(data.getItems().description(id), BankViewBuilder.where(ledger, id, ALL_SOURCES))));
			}
		}
		Set<Integer> bucket = data.getItems().group("bottomless_compost_bucket");
		if (ledger.countAny(bucket) > 0)
		{
			int id = bucket.iterator().next();
			rows.add(new BankView.Row(id, "Bottomless bucket", bottomlessUses > 0 ? String.valueOf(bottomlessUses) : "-",
				bottomlessUses > 0 ? "uses left (leprechaun)" : "filled",
				BankTooltips.supply(data.getItems().description(id), BankViewBuilder.where(ledger, id, ALL_SOURCES))));
		}
		return rows;
	}

	static List<BankView.Row> payments(GameData data, ItemLedger ledger)
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
					.add(cropName(seed) + ": " + payment.getAmount());
			}
		}
		return names.entrySet().stream()
			.filter(e -> ledger.count(e.getKey()) > 0)
			.sorted(Map.Entry.comparingByValue())
			.map(e ->
			{
				List<String> crops = paysFor.get(e.getKey());
				return new BankView.Row(e.getKey(), e.getValue(), String.valueOf(ledger.count(e.getKey())),
					"for " + String.join(", ", crops), BankTooltips.payment(crops, BankViewBuilder.where(ledger, e.getKey(), ALL_SOURCES)));
			})
			.collect(Collectors.toList());
	}

	static List<BankView.Row> teleports(GameData data, ItemLedger ledger)
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
				List<String> usedFor = locationsWhere(data, l -> l.getDefaultTeleports().stream()
					.anyMatch(ref -> data.teleportChoice(ref).getMethod().getId().equals(teleport.getId())));
				rows.add(new BankView.Row(icon, teleport.getName(), String.valueOf(total), null,
					BankTooltips.teleport(usedFor, whereAny(ledger, teleport.getItemIds()))));
			}
		}
		return rows;
	}

	static List<BankView.Row> leprechaun(GameData data, ItemLedger ledger)
	{
		return ledger.snapshot(ItemSource.LEPRECHAUN).getItems().entrySet().stream()
			.map(e -> new BankView.Row(e.getKey(), leprechaunItemName(data, e.getKey()), String.valueOf(e.getValue()),
				null, BankTooltips.leprechaun(data.getItems().description(e.getKey()))))
			.sorted(Comparator.comparing(BankView.Row::getLabel))
			.collect(Collectors.toList());
	}

	private static List<String> locationsWhere(GameData data, Predicate<Location> test)
	{
		return data.locations().stream().filter(test).map(Location::getName).sorted().collect(Collectors.toList());
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
}
