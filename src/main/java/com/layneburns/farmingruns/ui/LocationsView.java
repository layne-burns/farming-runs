/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.state.LocationStatus;
import com.layneburns.farmingruns.state.PatchStatus;
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
	/**
	 * One section per chosen run type (or one for all, by {@link LocationSort}), then "Locked" last; empty sections
	 * are left out.
	 */
	List<Section> sections;
	/** True when RuneLite's Time Tracking plugin is off, so patch states aren't being updated. */
	boolean timeTrackingOff;

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
		/** The patches at a glance, e.g. "3 ready" or "in 1 h 20 min"; and how to colour it. */
		String readySummary;
		PatchStatus.Readiness summaryReadiness;
		/** For sorting: how many of those patches are ready, and when the soonest growing one will be (0 if none). */
		long readyCount;
		long soonestReadyAt;
		/** E.g. "Unlocked", "Locked: Quest: Song of the Elves", "Unlocked (your override)". */
		String statusText;
		/** Teleports in order of preference: the first one you can use is used. */
		List<TeleportLine> teleports;
		/** Every teleport, grouped for the "Add a teleport" menu. */
		List<TeleportMenuGroup> addMenu;
		/** True when you've changed the order from the default. */
		boolean customTeleports;
		List<PatchLine> patches;
		@Nullable
		String note;
		/** The wiki's suggested teleports, best first. */
		List<String> wikiTeleports;
	}

	@Value
	public static class TeleportLine
	{
		/** "method" or "method:destination". */
		String ref;
		/** E.g. "Skills necklace → Fishing Guild" or "Fairy ring CIR". */
		String name;
		/** More about where it lands (a fairy ring's area); null if none. */
		@Nullable
		String detail;
		/** A problem with this teleport, e.g. it needs another spellbook; null if none. */
		@Nullable
		String warning;
	}

	/** A heading in the "Add a teleport" menu, e.g. "Jewellery". */
	@Value
	public static class TeleportMenuGroup
	{
		String title;
		List<TeleportMenuMethod> methods;
	}

	/** A method in that menu: one item if it goes to one place, else a submenu of its destinations. */
	@Value
	public static class TeleportMenuMethod
	{
		String name;
		List<TeleportOption> options;
	}

	@Value
	public static class TeleportOption
	{
		String ref;
		/** The destination's name ("Fishing Guild", "CIR"), or the method's when it has one destination. */
		String name;
		@Nullable
		String detail;
		/** Already in this location's list, so it can't be added again. */
		boolean listed;
	}

	@Value
	public static class PatchLine
	{
		/** E.g. "Allotment N" or "Herb". */
		String name;
		/** What's growing, e.g. "Ranarr"; null when empty or not seen. */
		@Nullable
		String crop;
		/** Short state: "ready", "empty", "dead", "1 h 20 m", "not seen". */
		String state;
		PatchStatus.Readiness readiness;
		boolean usable;
		/** Why it isn't usable, e.g. "Farming 85"; null if it is. */
		@Nullable
		String reason;
	}
}
