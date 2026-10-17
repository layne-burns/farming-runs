/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.Teleport;
import com.layneburns.farmingruns.data.TeleportType;
import com.layneburns.farmingruns.state.LocationStatus;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Builds the Locations tab's contents (SPEC §2.1) from the game data and the unlock results. Pure: the unlock
 * answers and teleport choices come in as functions, so it's tested without the client.
 */
public final class LocationsViewBuilder
{
	private LocationsViewBuilder()
	{
	}

	/**
	 * @param statusOf    a location's unlock status
	 * @param patchRuleOf a patch's own rule status (Farming Guild tiers), ignoring its location
	 * @param teleportOf  the teleport id in use at a location
	 * @param spellbook   your spellbook ("Normal", "Lunar"...), or null if not known
	 * @param query       search text (name or patch kind); empty for all
	 */
	public static LocationsView build(GameData data, Function<Location, LocationStatus> statusOf,
		Function<Patch, LocationStatus> patchRuleOf, Function<Location, String> teleportOf, @Nullable String spellbook,
		String query)
	{
		List<LocationsView.Entry> locked = new ArrayList<>();
		List<LocationsView.Entry> unlocked = new ArrayList<>();
		boolean known = true;
		int usable = 0;
		List<Location> locations = data.locations().stream()
			.sorted(Comparator.comparing(Location::getName))
			.collect(Collectors.toList());
		for (Location location : locations)
		{
			LocationStatus status = statusOf.apply(location);
			known &= status.getByRules() != LocationStatus.Access.UNKNOWN;
			usable += status.getAccess() == LocationStatus.Access.LOCKED ? 0 : 1;
			LocationsView.Entry entry = entry(data, location, status, patchRuleOf, teleportOf.apply(location), spellbook);
			if (matches(entry, query))
			{
				(status.getAccess() == LocationStatus.Access.LOCKED ? locked : unlocked).add(entry);
			}
		}
		String summary = known ? usable + " of " + locations.size() + " locations unlocked"
			: "Log in to check which locations you've unlocked.";
		return new LocationsView(summary, locked, unlocked);
	}

	private static LocationsView.Entry entry(GameData data, Location location, LocationStatus status,
		Function<Patch, LocationStatus> patchRuleOf, String teleportId, @Nullable String spellbook)
	{
		List<LocationsView.PatchLine> patches = location.getPatches().stream()
			.map(p ->
			{
				LocationStatus rule = patchRuleOf.apply(p);
				return new LocationsView.PatchLine(patchName(p), rule.isUsable(),
					rule.isUsable() ? null : String.join(", ", rule.getMissing()));
			})
			.collect(Collectors.toList());
		return new LocationsView.Entry(location.getId(), location.getName(), kinds(location), status,
			statusText(status), teleportId, teleportOptions(data, location),
			teleportWarning(data.teleport(teleportId), spellbook), patches, location.getNote(),
			location.getWikiTeleports());
	}

	static String statusText(LocationStatus status)
	{
		String text;
		switch (status.getAccess())
		{
			case UNLOCKED:
				text = "Unlocked";
				break;
			case LOCKED:
				text = status.getMissing().isEmpty() ? "Locked" : "Locked: " + String.join(", ", status.getMissing());
				break;
			default:
				return "Log in to check";
		}
		return status.isOverridden() ? text + " (your override)" : text;
	}

	/** "2 allotment, flower, herb": each kind once, with a count when there's more than one. */
	static String kinds(Location location)
	{
		Map<PatchImplementation, Integer> counts = new LinkedHashMap<>();
		location.getPatches().forEach(p -> counts.merge(p.getKind(), 1, Integer::sum));
		return counts.entrySet().stream()
			.map(e -> (e.getValue() > 1 ? e.getValue() + " " : "") + BankViewBuilder.kindName(e.getKey()).toLowerCase(Locale.ROOT))
			.collect(Collectors.joining(", "));
	}

	private static String patchName(Patch patch)
	{
		String kind = BankViewBuilder.kindName(patch.getKind());
		return patch.getName().isEmpty() ? kind : kind + " (" + patch.getName() + ")";
	}

	/** The location's default first, then every other teleport by name. */
	private static List<LocationsView.TeleportOption> teleportOptions(GameData data, Location location)
	{
		List<LocationsView.TeleportOption> options = new ArrayList<>();
		Teleport fallback = data.teleport(location.getDefaultTeleportId());
		options.add(new LocationsView.TeleportOption(fallback.getId(), fallback.getName(), true));
		data.teleports().stream()
			.filter(t -> !t.getId().equals(fallback.getId()))
			.sorted(Comparator.comparing(Teleport::getName))
			.forEach(t -> options.add(new LocationsView.TeleportOption(t.getId(), t.getName(), false)));
		return options;
	}

	/** A warning when the teleport can't work as things stand: a spell from another spellbook. */
	@Nullable
	static String teleportWarning(Teleport teleport, @Nullable String spellbook)
	{
		if (teleport.getType() == TeleportType.SPELL && spellbook != null && !spellbook.equals(teleport.getSpellbook()))
		{
			return "Needs the " + teleport.getSpellbook() + " spellbook (you're on " + spellbook + ")";
		}
		return null;
	}

	/** Every word of the query appears in the name or the patch kinds. */
	private static boolean matches(LocationsView.Entry entry, String query)
	{
		String text = (entry.getName() + " " + entry.getKinds()).toLowerCase(Locale.ROOT);
		for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+"))
		{
			if (!word.isEmpty() && !text.contains(word))
			{
				return false;
			}
		}
		return true;
	}
}
