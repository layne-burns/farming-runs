/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import com.layneburns.farmingruns.data.ItemCatalog;
import com.layneburns.farmingruns.data.Pack;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.inventory.Requirement.Importance;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * Payment making: packs loose fruit into baskets and vegetables into sacks, as many as what's in your bank and
 * inventory makes. Part-filled ones are topped up first, fullest first (they take the least), then empty ones are
 * filled; when empty baskets or sacks run short, the payments higher in the list get them. Fruit isn't moved out of a
 * part-filled one to fill another. The bank shows one inventory's worth at a time. Pure: unit tested.
 */
public final class PaymentKit
{
	/** Inventory slots: produce doesn't stack, and neither do the containers. */
	static final int SLOTS = 28;

	/** What payment making comes to for the ticked payments. */
	@Value
	public static class Plan
	{
		public static final Plan NONE = new Plan(Map.of(), Map.of(), List.of());

		/** Full ones you can make of each ticked payment, in list order (0 when none). */
		Map<Pack, Integer> made;
		/** More empty containers that would make more, by container name ("Basket"). */
		Map<String, Integer> containersShort;
		/** What to take out for this trip: containers, part-filled ones and produce. */
		List<Requirement> trip;
	}

	/** One container to fill: an empty one, or one holding some already. */
	@Value
	private static class Fill
	{
		Pack pack;
		String label;
		Set<Integer> itemIds;
		/** How many of these. */
		int count;
		/** Produce each one takes to fill. */
		int produceEach;
	}

	private PaymentKit()
	{
	}

	/**
	 * @param packs  the ticked payments, in list order
	 * @param ledger your items; only the bank and inventory count
	 */
	public static Plan plan(List<Pack> packs, ItemLedger ledger, ItemCatalog items)
	{
		Map<Pack, Integer> made = new LinkedHashMap<>();
		Map<String, Integer> containersShort = new LinkedHashMap<>();
		Map<String, Integer> empties = new HashMap<>();
		List<Fill> fills = new ArrayList<>();
		for (Pack pack : packs)
		{
			int loose = have(ledger, Set.of(pack.getProduceId()));
			int count = 0;
			for (int held = pack.size() - 1; held >= 1; held--)
			{
				int each = pack.size() - held;
				int topped = Math.min(have(ledger, Set.of(pack.fillId(held))), loose / each);
				if (topped > 0)
				{
					fills.add(new Fill(pack, pack.fillName(held), Set.of(pack.fillId(held)), topped, each));
					loose -= topped * each;
					count += topped;
				}
			}
			Set<Integer> containerIds = items.group(pack.getContainer());
			String containerName = containerName(pack, items);
			int spare = empties.computeIfAbsent(pack.getContainer(), c -> have(ledger, containerIds));
			int filled = Math.min(spare, loose / pack.size());
			if (filled > 0)
			{
				fills.add(new Fill(pack, containerName, containerIds, filled, pack.size()));
				empties.put(pack.getContainer(), spare - filled);
				loose -= filled * pack.size();
				count += filled;
			}
			if (loose >= pack.size())
			{
				containersShort.merge(containerName, loose / pack.size(), Integer::sum);
			}
			made.put(pack, count);
		}
		return new Plan(Collections.unmodifiableMap(made), Collections.unmodifiableMap(containersShort), trip(fills));
	}

	/** The first inventory's worth of fills: each takes a slot for the container and one per produce. */
	private static List<Requirement> trip(List<Fill> fills)
	{
		RequirementList list = new RequirementList();
		int slots = SLOTS;
		for (Fill fill : fills)
		{
			int take = Math.min(fill.getCount(), slots / (1 + fill.getProduceEach()));
			if (take == 0)
			{
				continue;
			}
			slots -= take * (1 + fill.getProduceEach());
			Pack pack = fill.getPack();
			String use = "packing " + pack.getName();
			list.add(fill.getLabel(), fill.getItemIds(), take, Importance.NEED, false, use, true);
			list.add(pack.getProduceName(), Set.of(pack.getProduceId()), take * fill.getProduceEach(), Importance.NEED,
				false, use, true);
		}
		return list.toList();
	}

	/** The crops a payment protects, by seed name without "seed": ["Maple"] for Oranges(5). */
	public static List<String> paysFor(Pack pack, List<Seed> seeds)
	{
		return seeds.stream()
			.filter(s -> s.getPayment().stream().anyMatch(p -> p.getItemId() == pack.fullId()))
			.map(s -> s.getName().replaceFirst(" seed$", ""))
			.collect(Collectors.toList());
	}

	private static String containerName(Pack pack, ItemCatalog items)
	{
		String name = items.displayName(items.group(pack.getContainer()).iterator().next());
		return name == null ? pack.getContainer() : name;
	}

	private static int have(ItemLedger ledger, Set<Integer> ids)
	{
		int total = 0;
		for (int id : ids)
		{
			total += ledger.count(id, ItemSource.BANK) + ledger.count(id, ItemSource.INVENTORY);
		}
		return total;
	}
}
