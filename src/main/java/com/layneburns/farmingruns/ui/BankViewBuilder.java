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
import com.layneburns.farmingruns.state.BufferSettings;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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


	private BankViewBuilder()
	{
	}

	/**
	 * @param patchesPerKind how many patches of each kind a full run visits
	 */
	public static BankView build(GameData data, ItemLedger ledger, BufferSettings buffers,
		Map<PatchImplementation, Integer> patchesPerKind, int bottomlessUses, long nowMillis)
	{
		List<BankView.Section> sections = new ArrayList<>(seedSections(data, ledger, buffers, patchesPerKind));
		if (sections.stream().allMatch(BankView.Section::isEmpty))
		{
			sections.add(new BankView.Section(BankView.Page.SEEDS, "Seeds", List.of(),
				"No seeds seen yet. Open your bank and seed vault to fill the seed log."));
		}
		sections.add(single(BankView.Page.SUPPLIES, "Compost", SuppliesBuilder.compost(data, ledger, bottomlessUses),
			"No compost seen."));
		sections.add(single(BankView.Page.SUPPLIES, "Protection payments", SuppliesBuilder.payments(data, ledger),
			"No payment items seen."));
		sections.add(single(BankView.Page.SUPPLIES, "Teleport items", SuppliesBuilder.teleports(data, ledger),
			"No teleport items seen."));
		sections.add(single(BankView.Page.LEPRECHAUN, "Tool leprechaun", SuppliesBuilder.leprechaun(data, ledger),
			"Nothing stored, or not logged in."));
		return new BankView(seen(ledger, nowMillis), sections);
	}

	// ------------------------------------------------------------------ seeds

	private static List<BankView.Section> seedSections(GameData data, ItemLedger ledger, BufferSettings buffers,
		Map<PatchImplementation, Integer> patchesPerKind)
	{
		List<BankView.Section> sections = new ArrayList<>();
		SEED_SECTIONS.forEach((title, kinds) ->
		{
			List<BankView.Group> groups = new ArrayList<>();
			for (PatchImplementation kind : kinds)
			{
				List<Seed> seeds = data.seedsFor(kind);
				int patches = patchesPerKind.getOrDefault(kind, 0);
				if (seeds.stream().anyMatch(Seed::isPlantedAsSapling))
				{
					// Trees: saplings (what gets planted) and seeds (still to be potted) are listed apart.
					addGroup(groups, kindName(kind) + " saplings", seeds.stream()
						.map(seed -> saplingRow(seed, ledger, buffers, patches)));
					addGroup(groups, kindName(kind) + " seeds", seeds.stream()
						.map(seed -> treeSeedRow(seed, ledger)));
				}
				else
				{
					addGroup(groups, kindName(kind), seeds.stream()
						.map(seed -> seedRow(seed, ledger, buffers, patches)));
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
	private static BankView.Row seedRow(Seed seed, ItemLedger ledger, BufferSettings buffers, int patches)
	{
		int seeds = count(ledger, seed.getItemId(), SEED_SOURCES);
		if (seeds == 0)
		{
			return null;
		}
		int spare = buffers.spare(seed, seeds);
		return new BankView.Row(seed.getItemId(), seed.getName(), String.valueOf(seeds), spare + " spare", null,
			BankTooltips.plantable(seed, where(ledger, seed.getItemId(), SEED_SOURCES)),
			plantable(seed, spare, patches, ledger));
	}

	/** A tree's saplings, with the sapling buffer applied, or null if you have none. */
	private static BankView.Row saplingRow(Seed seed, ItemLedger ledger, BufferSettings buffers, int patches)
	{
		int saplings = count(ledger, seed.getSaplingItemId(), SEED_SOURCES);
		if (saplings == 0)
		{
			return null;
		}
		int spare = buffers.spare(seed, saplings);
		return new BankView.Row(seed.getSaplingItemId(), seed.getSaplingName(), String.valueOf(saplings),
			spare + " spare", null, BankTooltips.plantable(seed, where(ledger, seed.getSaplingItemId(), SEED_SOURCES)),
			plantable(seed, spare, patches, ledger));
	}

	/** The facts the filters need: patches the spare seeds fill, and patches the payment you hold protects. */
	private static BankView.Plantable plantable(Seed seed, int spare, int patches, ItemLedger ledger)
	{
		int paidPatches = 0;
		if (seed.canBeProtected())
		{
			paidPatches = Integer.MAX_VALUE;
			for (ItemAmount payment : seed.getPayment())
			{
				paidPatches = Math.min(paidPatches, ledger.count(payment.getItemId()) / payment.getAmount());
			}
		}
		String perPatch = seed.getPayment().stream()
			.map(p -> p.getAmount() + " x " + p.getName())
			.collect(Collectors.joining(" + "));
		return new BankView.Plantable(seed.getLevel(), patches, spare / seed.getPerPatch(), seed.canBeProtected(),
			paidPatches, perPatch);
	}

	/** A tree's unpotted seeds (no buffer: the buffer applies to saplings), or null if you have none. */
	private static BankView.Row treeSeedRow(Seed seed, ItemLedger ledger)
	{
		int seeds = count(ledger, seed.getItemId(), SEED_SOURCES);
		return seeds == 0 ? null : new BankView.Row(seed.getItemId(), seed.getName(), String.valueOf(seeds), null,
			BankTooltips.unpottedTreeSeed(seed, where(ledger, seed.getItemId(), SEED_SOURCES)));
	}

	// ------------------------------------------------------------------ helpers

	private static BankView.Section single(BankView.Page page, String title, List<BankView.Row> rows, String empty)
	{
		return new BankView.Section(page, title, List.of(new BankView.Group(null, rows)), empty);
	}



	static int count(ItemLedger ledger, int itemId, List<ItemSource> sources)
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
