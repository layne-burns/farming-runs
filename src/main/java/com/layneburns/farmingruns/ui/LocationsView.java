/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.state.LocationStatus;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * What the Locations tab shows, as plain data: built by {@link LocationsViewBuilder}, drawn by {@link LocationsTab}.
 */
@Value
public class LocationsView
{
	/** E.g. "41 of 43 locations unlocked", or a prompt to log in. */
	String summary;
	/** One section per chosen run type, then "Locked" last; empty sections are left out. */
	List<Section> sections;

	@Value
	public static class Section
	{
		/** E.g. "Herb (9)" or "Locked (1)". */
		String title;
		/** Stays the same when the count changes: remembers collapsed/opened state across redraws. */
		String key;
		List<Entry> entries;
	}

	@Value
	public static class Entry
	{
		String id;
		String name;
		/** The patches there, e.g. "2 allotment, flower, herb". */
		String kinds;
		LocationStatus status;
		/** E.g. "Unlocked", "Locked: Quest: Song of the Elves", "Unlocked (your override)". */
		String statusText;
		/** The teleport in use (your choice or the default), and every choice, default first. */
		String teleportId;
		List<TeleportOption> teleports;
		/** A problem with the chosen teleport, e.g. it needs another spellbook; null if none. */
		@Nullable
		String teleportWarning;
		List<PatchLine> patches;
		@Nullable
		String note;
		/** The wiki's suggested teleports, best first. */
		List<String> wikiTeleports;
	}

	@Value
	public static class TeleportOption
	{
		String id;
		String name;
		boolean isDefault;

		@Override
		public String toString()
		{
			return isDefault ? name + " (default)" : name;
		}
	}

	@Value
	public static class PatchLine
	{
		/** E.g. "Allotment (North)". */
		String name;
		boolean usable;
		/** Why it isn't usable, e.g. "Farming 85"; null if it is. */
		@Nullable
		String reason;
	}
}
