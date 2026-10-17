/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Teleport;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.data.TeleportDestination;
import com.layneburns.farmingruns.data.TeleportType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Recognises a teleport from what you clicked: an item's teleport option, a spell cast, a fairy ring's last
 * destination, or a house portal. Gives a "method:destination" ref, or just "method" when the destination is chosen
 * later in a dialogue (rubbing a talisman); {@link #resolve} fills that in from a location's list. Pure: unit tested.
 */
public final class TeleportRecognizer
{
	/** Item options that never teleport. */
	private static final Set<String> NOT_TELEPORTS = Set.of("wear", "wield", "remove", "drop", "examine", "use",
		"check", "check-charges", "destroy", "empty", "cancel", "take", "deposit", "withdraw", "inspect");
	private static final Pattern LAST_DESTINATION = Pattern.compile("last-destination \\(([a-z]{3})\\)");
	/** Words that don't tell destinations apart. */
	private static final Set<String> FILLER = Set.of("teleport", "to", "the", "of");

	private TeleportRecognizer()
	{
	}

	/**
	 * @param option the menu option, e.g. "Rub", "Farming Guild", "Cast", "Last-destination (CIR)"
	 * @param target the menu target with its colour tags removed, e.g. "Skills necklace(4)", "Varrock Teleport"
	 * @param itemId the clicked item, or -1 when it wasn't an item
	 * @return a teleport ref, a bare method id, or null when it wasn't a teleport we know
	 */
	@Nullable
	public static String recognise(String option, String target, int itemId, GameData data)
	{
		String opt = option.toLowerCase(Locale.ROOT).trim();
		String tgt = target.toLowerCase(Locale.ROOT).trim();
		if (itemId > 0)
		{
			return NOT_TELEPORTS.contains(opt) ? null : fromItem(opt, itemId, data);
		}
		if (opt.equals("cast"))
		{
			return data.teleports().stream()
				.filter(t -> t.getType() == TeleportType.SPELL && t.getName().equalsIgnoreCase(tgt))
				.map(Teleport::getId).findFirst().orElse(null);
		}
		if (tgt.equals("fairy ring"))
		{
			Matcher code = LAST_DESTINATION.matcher(opt);
			return code.find() ? valid("fairy_ring:" + code.group(1), data) : null;
		}
		Teleport portal = houseMethod(data);
		if (portal != null && tgt.endsWith(" portal"))
		{
			// A portal chamber portal, e.g. "Weiss Portal".
			return destinationRef(portal, tgt.substring(0, tgt.length() - " portal".length()));
		}
		if (portal != null && tgt.equals("portal nexus"))
		{
			return destinationRef(portal, opt);
		}
		return null;
	}

	/**
	 * A full ref for a stop: a recognised "method:destination" as it is; for a bare method, the destination of it
	 * that the location's list uses. Null when that can't be told.
	 */
	@Nullable
	public static String resolve(String recognised, List<String> locationRefs, GameData data)
	{
		if (recognised.contains(":") || data.teleportChoice(recognised) != null)
		{
			return recognised;
		}
		return locationRefs.stream().filter(r -> r.startsWith(recognised + ":")).findFirst().orElse(null);
	}

	/** The item's teleport method, with the destination when the option names one ("Farming Guild"). */
	@Nullable
	private static String fromItem(String option, int itemId, GameData data)
	{
		for (Teleport method : data.teleports())
		{
			boolean ours = method.getItemIds().contains(itemId)
				|| method.getDestinations().stream().anyMatch(d -> d.getItemIds().contains(itemId));
			if (!ours || method.getType() == TeleportType.FAIRY_RING || method.getType() == TeleportType.HOUSE)
			{
				continue;
			}
			if (method.getDestinations().isEmpty())
			{
				return method.getId();
			}
			String ref = destinationRef(method, option);
			return ref != null ? ref : method.getId();
		}
		return null;
	}

	/** "method:destination" for the destination the words name, e.g. "Farm Teleport" → Ardougne farm. */
	@Nullable
	private static String destinationRef(Teleport method, String words)
	{
		List<String> wanted = Arrays.stream(words.split("[^a-z']+")).filter(w -> !w.isEmpty() && !FILLER.contains(w))
			.collect(Collectors.toList());
		if (wanted.isEmpty())
		{
			return null;
		}
		for (TeleportDestination destination : method.getDestinations())
		{
			String name = destination.getName().toLowerCase(Locale.ROOT);
			if (wanted.stream().allMatch(name::contains))
			{
				return TeleportChoice.ref(method.getId(), destination.getId());
			}
		}
		return null;
	}

	@Nullable
	private static Teleport houseMethod(GameData data)
	{
		return data.teleports().stream().filter(t -> t.getType() == TeleportType.HOUSE).findFirst().orElse(null);
	}

	@Nullable
	private static String valid(String ref, GameData data)
	{
		return data.teleportChoice(ref) == null ? null : ref;
	}
}
