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
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.data.Teleport;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.data.TeleportDestination;
import com.layneburns.farmingruns.data.TeleportType;
import com.layneburns.farmingruns.state.LocationStatus;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Builds the Locations tab's contents (SPEC §2.1) from the game data and the unlock results: one section per chosen
 * run type (a location appears under every type it has), then the locked locations last. Pure: the unlock answers
 * and teleport choices come in as functions, so it's tested without the client.
 */
public final class LocationsViewBuilder
{
	private LocationsViewBuilder()
	{
	}

	/**
	 * @param query    search text (name or patch kind); empty for all
	 * @param runTypes the run types to show a section for
	 */
	public static LocationsView build(GameData data, LocationsContext context, String query, Set<RunType> runTypes)
	{
		Function<Location, LocationStatus> statusOf = context.getStatusOf();
		Function<Patch, LocationStatus> patchRuleOf = context.getPatchRuleOf();
		List<Location> locations = data.locations().stream()
			.sorted(Comparator.comparing(Location::getName))
			.collect(Collectors.toList());
		Map<String, LocationStatus> statuses = new HashMap<>();
		boolean known = true;
		int usable = 0;
		for (Location location : locations)
		{
			LocationStatus status = statusOf.apply(location);
			statuses.put(location.getId(), status);
			known &= status.getByRules() != LocationStatus.Access.UNKNOWN;
			usable += status.getAccess() == LocationStatus.Access.LOCKED ? 0 : 1;
		}

		List<LocationsView.Section> sections = new ArrayList<>();
		List<LocationsView.Entry> locked = new ArrayList<>();
		for (RunType type : RunType.values())
		{
			if (!runTypes.contains(type))
			{
				continue;
			}
			List<LocationsView.Entry> entries = new ArrayList<>();
			for (Location location : locations)
			{
				LocationStatus status = statuses.get(location.getId());
				// Listed under a run type if it has a patch of that type whose own rule (a guild tier) allows it.
				boolean hasType = location.getPatches().stream()
					.anyMatch(p -> type.covers(p.getKind()) && patchRuleOf.apply(p).isUsable());
				if (!hasType || status.getAccess() == LocationStatus.Access.LOCKED)
				{
					continue;
				}
				LocationsView.Entry entry = entry(data, location, status, context);
				if (matches(entry, query))
				{
					entries.add(entry);
				}
			}
			if (!entries.isEmpty())
			{
				sections.add(new LocationsView.Section(type.getTitle() + " (" + entries.size() + ")", type.name(), entries));
			}
		}
		for (Location location : locations)
		{
			LocationStatus status = statuses.get(location.getId());
			boolean shown = location.getPatches().stream().anyMatch(p -> runTypes.stream().anyMatch(t -> t.covers(p.getKind())));
			if (status.getAccess() == LocationStatus.Access.LOCKED && shown)
			{
				LocationsView.Entry entry = entry(data, location, status, context);
				if (matches(entry, query))
				{
					locked.add(entry);
				}
			}
		}
		if (!locked.isEmpty())
		{
			sections.add(new LocationsView.Section("Locked (" + locked.size() + ")", "LOCKED", locked));
		}
		String summary = known ? usable + " of " + locations.size() + " locations unlocked"
			: "Log in to check which locations you've unlocked.";
		return new LocationsView(summary, sections);
	}

	private static LocationsView.Entry entry(GameData data, Location location, LocationStatus status,
		LocationsContext context)
	{
		List<LocationsView.PatchLine> patches = location.getPatches().stream()
			.map(p ->
			{
				LocationStatus rule = context.getPatchRuleOf().apply(p);
				return new LocationsView.PatchLine(patchName(p), rule.isUsable(),
					rule.isUsable() ? null : String.join(", ", rule.getMissing()));
			})
			.collect(Collectors.toList());
		List<String> order = context.getTeleportsOf().apply(location);
		List<LocationsView.TeleportLine> teleports = order.stream()
			.map(data::teleportChoice)
			.map(c -> new LocationsView.TeleportLine(c.getRef(), c.displayName(),
				c.getDestination() == null ? null : c.getDestination().getDetail(),
				teleportWarning(c.getMethod(), context.getSpellbook())))
			.collect(Collectors.toList());
		return new LocationsView.Entry(location.getId(), location.getName(), kinds(location), status,
			statusText(status), teleports, addMenu(data, order), context.getCustomTeleports().test(location), patches,
			location.getNote(), location.getWikiTeleports());
	}

	/** Every teleport, grouped as the data lists them (Jewellery, Items, spells...), marking those already listed. */
	static List<LocationsView.TeleportMenuGroup> addMenu(GameData data, List<String> listed)
	{
		Map<String, List<LocationsView.TeleportMenuMethod>> groups = new LinkedHashMap<>();
		for (Teleport method : data.teleports())
		{
			List<LocationsView.TeleportOption> options = new ArrayList<>();
			if (method.getDestinations().isEmpty())
			{
				options.add(new LocationsView.TeleportOption(method.getId(), method.getName(), null,
					listed.contains(method.getId())));
			}
			for (TeleportDestination d : method.getDestinations())
			{
				String ref = TeleportChoice.ref(method.getId(), d.getId());
				options.add(new LocationsView.TeleportOption(ref, d.getName(), d.getDetail(), listed.contains(ref)));
			}
			groups.computeIfAbsent(method.getGroup(), g -> new ArrayList<>())
				.add(new LocationsView.TeleportMenuMethod(method.getName(), options));
		}
		return groups.entrySet().stream()
			.map(e -> new LocationsView.TeleportMenuGroup(e.getKey(), e.getValue()))
			.collect(Collectors.toList());
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
