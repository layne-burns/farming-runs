/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.state.LocationStatus;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;

public class LocationsViewBuilderTest
{
	private static final LocationStatus OPEN = LocationStatus.of(true, List.of(), null);
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	/** Everything unlocked except Prifddinas (Song of the Elves) and the guild's advanced tier (Farming 80). */
	private static LocationsView build(String query, Set<RunType> types)
	{
		return LocationsViewBuilder.build(data,
			l -> l.getId().equals("prifddinas") ? LocationStatus.of(true, List.of("Quest: Song of the Elves"), null) : OPEN,
			p -> "farming_guild_advanced".equals(p.getUnlockRuleId())
				? LocationStatus.of(true, List.of("Farming 85"), null) : OPEN,
			Location::getDefaultTeleportId, "Normal", query, types);
	}

	private static LocationsView buildAll(String query)
	{
		return build(query, EnumSet.allOf(RunType.class));
	}

	@Test
	public void sectionsFollowRunTypesWithLockedLast()
	{
		List<String> keys = buildAll("").getSections().stream().map(LocationsView.Section::getKey)
			.collect(Collectors.toList());
		assertEquals("HERB", keys.get(0));
		assertEquals("LOCKED", keys.get(keys.size() - 1));

		LocationsView.Section locked = section(buildAll(""), "LOCKED");
		assertEquals("Locked (1)", locked.getTitle());
		assertEquals("Locked: Quest: Song of the Elves", locked.getEntries().get(0).getStatusText());
	}

	@Test
	public void aLocationAppearsUnderEveryTypeItHas()
	{
		LocationsView view = buildAll("catherby");
		assertTrue(names(section(view, "HERB")).contains("Catherby"));
		assertTrue(names(section(view, "ALLOTMENT")).contains("Catherby"));
		assertTrue(names(section(view, "FRUIT_TREE")).contains("Catherby fruit tree"));
	}

	@Test
	public void onlyChosenTypesAreShown()
	{
		LocationsView view = build("", EnumSet.of(RunType.HERB));
		assertEquals(List.of("HERB"), view.getSections().stream().map(LocationsView.Section::getKey)
			.collect(Collectors.toList()));
		assertTrue(names(section(view, "HERB")).contains("Troll Stronghold"));
	}

	@Test
	public void aPatchLockedByItsOwnRuleLeavesItsTypeSection()
	{
		LocationsView view = buildAll("guild");
		// The fruit tree section has no guild at all (so, searching "guild", no section).
		assertFalse("guild fruit tree needs 85", view.getSections().stream()
			.filter(s -> s.getKey().equals("FRUIT_TREE"))
			.anyMatch(s -> names(s).contains("Farming Guild")));
		LocationsView.Entry guild = section(view, "HERB").getEntries().stream()
			.filter(e -> e.getName().equals("Farming Guild")).findFirst().orElseThrow();
		LocationsView.PatchLine fruit = guild.getPatches().stream()
			.filter(p -> p.getName().equals("Fruit tree")).findFirst().orElseThrow();
		assertEquals("Farming 85", fruit.getReason());
	}

	@Test
	public void kindsAreSummarised()
	{
		LocationsView.Entry catherby = section(buildAll("catherby"), "HERB").getEntries().get(0);
		assertEquals("2 allotment, flower, herb", catherby.getKinds());
	}

	@Test
	public void teleportsListTheDefaultFirstAndWarnAboutSpellbooks()
	{
		LocationsView.Entry catherby = section(buildAll("catherby"), "HERB").getEntries().get(0);
		assertEquals("camelot_teleport", catherby.getTeleportId());
		assertTrue(catherby.getTeleports().get(0).isDefault());
		assertNull(catherby.getTeleportWarning());
		assertEquals("Needs the Lunar spellbook (you're on Normal)",
			LocationsViewBuilder.teleportWarning(data.teleport("catherby_teleport"), "Normal"));
	}

	@Test
	public void summaryCountsUnlockedLocations()
	{
		assertEquals((data.locations().size() - 1) + " of " + data.locations().size() + " locations unlocked",
			buildAll("").getSummary());
	}

	private static LocationsView.Section section(LocationsView view, String key)
	{
		return view.getSections().stream().filter(s -> s.getKey().equals(key)).findFirst()
			.orElseThrow(() -> new AssertionError("no section " + key));
	}

	private static List<String> names(LocationsView.Section section)
	{
		return section.getEntries().stream().map(LocationsView.Entry::getName).collect(Collectors.toList());
	}
}
