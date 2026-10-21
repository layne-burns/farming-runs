/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.Compostable;
import com.layneburns.farmingruns.data.Seed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * What goes in the compost bin (SPEC §2.7). Each produce that makes supercompost has a keep: never, or how many to
 * hold back, and what you have above it is spare. By default shop-bought produce keeps none, produce that pays for a
 * crop at Farming level 65 or above is never composted, and anything else keeps 100. The ranking puts shop-bought
 * produce first, then the largest spare; your own order replaces it. The bin is filled from your default item while it
 * has spare, then down the ranking. Pure: unit tested.
 */
public final class CompostBinFill
{
	/** A keep meaning "never compost it". */
	public static final int NEVER = -1;
	/** Produce that pays for a crop at this Farming level or above is never composted by default. */
	static final int NEVER_AT_LEVEL = 65;
	/** Of other produce not sold in a shop, this many are kept back by default. */
	static final int DEFAULT_KEEP = 100;

	/** Your choices: a default item, your own order, and your own keeps. */
	@Value
	public static class Choices
	{
		public static final Choices NONE = new Choices(null, List.of(), Map.of());

		/** Item id used first while it has spare; null for none. */
		@Nullable
		Integer defaultItem;
		/** Your order of item ids; empty for the automatic ranking. */
		List<Integer> order;
		/** Your keeps by item id: {@link #NEVER} or how many to hold back. */
		Map<Integer, Integer> keeps;
	}

	/** One produce in the ranking. */
	@Value
	public static class Row
	{
		Compostable item;
		/** In the bank and inventory, less what the run uses otherwise (payments). */
		int held;
		/** {@link #NEVER} or how many to hold back. */
		int keep;
		/** The keep is yours, not the default. */
		boolean ownKeep;
		int spare;
		/** Why it's where it is: "364 spare (keep 100)", "33 spare, shop-bought", "never: pays for Magic tree". */
		String why;
	}

	@Value
	public static class Fill
	{
		/** Every produce in ranking order (your order, or the automatic one). */
		List<Row> ranking;
		/** How many of each go in, in the order they're used: the default first, then the ranking. */
		Map<Compostable, Integer> take;
		/** How many more the bin needs than you can spare. */
		int shortBy;

		/** E.g. "18 × Watermelon + 12 × Pineapple"; empty when nothing goes in. */
		public String words()
		{
			return take.entrySet().stream().map(e -> e.getValue() + " × " + e.getKey().getName())
				.collect(Collectors.joining(" + "));
		}
	}

	private CompostBinFill()
	{
	}

	/**
	 * @param held   item id to how many you have to use (bank and inventory, less what the run needs otherwise)
	 * @param needed how many the bin takes; 0 just ranks
	 */
	public static Fill fill(List<Compostable> items, List<Seed> seeds, Map<Integer, Integer> held, Choices choices,
		int needed)
	{
		List<Row> rows = items.stream()
			.map(i -> row(i, seeds, held.getOrDefault(i.getItemId(), 0), choices.getKeeps().get(i.getItemId())))
			.collect(Collectors.toList());
		List<Row> ranking = rank(rows, choices.getOrder());

		List<Row> use = new ArrayList<>(ranking);
		use.stream().filter(r -> Objects.equals(r.getItem().getItemId(), choices.getDefaultItem())).findFirst()
			.ifPresent(r ->
			{
				use.remove(r);
				use.add(0, r);
			});
		Map<Compostable, Integer> take = new LinkedHashMap<>();
		int left = needed;
		for (Row row : use)
		{
			int n = Math.min(row.getSpare(), left);
			if (n > 0)
			{
				take.put(row.getItem(), n);
				left -= n;
			}
		}
		return new Fill(ranking, Collections.unmodifiableMap(take), left);
	}

	/** The keep before your own: none if sold in a shop, never if it pays for a crop at level 65+, else 100. */
	static int defaultKeep(Compostable item, List<Seed> seeds)
	{
		if (item.isShopBought())
		{
			return 0;
		}
		Seed top = highestPaid(item, seeds);
		return top != null && top.getLevel() >= NEVER_AT_LEVEL ? NEVER : DEFAULT_KEEP;
	}

	private static Row row(Compostable item, List<Seed> seeds, int held, @Nullable Integer own)
	{
		int keep = own != null ? own : defaultKeep(item, seeds);
		int spare = keep == NEVER ? 0 : Math.max(0, held - keep);
		String why;
		if (keep == NEVER)
		{
			List<String> high = paidFor(item, seeds).stream().filter(s -> s.getLevel() >= NEVER_AT_LEVEL)
				.map(CompostBinFill::cropName).collect(Collectors.toList());
			why = own != null || high.isEmpty() ? "never (your choice)" : "never: pays for " + String.join(", ", high);
		}
		else if (held == 0)
		{
			why = "none held";
		}
		else
		{
			why = (spare == 0 ? "none" : String.valueOf(spare)) + " spare" + (keep > 0 ? " (keep " + keep + ")" : "")
				+ (item.isShopBought() ? ", shop-bought" : "");
		}
		return new Row(item, held, keep, own != null, spare, why);
	}

	/** Your order first (ids you've moved), then the rest automatically: spare before none, shop first, most spare. */
	private static List<Row> rank(List<Row> rows, List<Integer> order)
	{
		List<Row> automatic = rows.stream()
			.sorted(Comparator.comparing((Row r) -> r.getSpare() == 0)
				.thenComparing(r -> !r.getItem().isShopBought())
				.thenComparing(Comparator.comparingInt(Row::getSpare).reversed())
				.thenComparing(r -> r.getItem().getName()))
			.collect(Collectors.toList());
		List<Row> ranked = new ArrayList<>();
		for (int id : order)
		{
			automatic.stream().filter(r -> r.getItem().getItemId() == id && !ranked.contains(r)).findFirst()
				.ifPresent(ranked::add);
		}
		automatic.stream().filter(r -> !ranked.contains(r)).forEach(ranked::add);
		return ranked;
	}

	/** The highest-level crop this produce pays for, or null. */
	@Nullable
	private static Seed highestPaid(Compostable item, List<Seed> seeds)
	{
		return paidFor(item, seeds).stream().max(Comparator.comparingInt(Seed::getLevel)).orElse(null);
	}

	/** The crops this produce pays for, lowest level first. */
	private static List<Seed> paidFor(Compostable item, List<Seed> seeds)
	{
		return seeds.stream()
			.filter(s -> s.getPayment().stream().anyMatch(p -> p.getItemId() == item.getItemId()))
			.sorted(Comparator.comparingInt(Seed::getLevel))
			.collect(Collectors.toList());
	}

	/** "Magic tree" for a magic seed, "Snape grass" for snape grass seed. */
	private static String cropName(Seed seed)
	{
		return seed.isPlantedAsSapling() && seed.getSaplingName() != null
			? seed.getSaplingName().replaceFirst(" sapling$", " tree")
			: seed.getName().replaceFirst(" seed$", "");
	}
}
