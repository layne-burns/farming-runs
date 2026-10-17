/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.state.LocationStatus;
import java.util.List;
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
	private static LocationsView build(String query)
	{
		return LocationsViewBuilder.build(data,
			l -> l.getId().equals("prifddinas") ? LocationStatus.of(true, List.of("Quest: Song of the Elves"), null) : OPEN,
			p -> "farming_guild_advanced".equals(p.getUnlockRuleId())
				? LocationStatus.of(true, List.of("Farming 85"), null) : OPEN,
			Location::getDefaultTeleportId, "Normal", query);
	}

	@Test
	public void lockedLocationsAreListedFirstWithTheReason()
	{
		LocationsView view = build("");
		assertEquals(1, view.getLocked().size());
		LocationsView.Entry prif = view.getLocked().get(0);
		assertEquals("Prifddinas", prif.getName());
		assertEquals("Locked: Quest: Song of the Elves", prif.getStatusText());
		assertEquals((data.locations().size() - 1) + " of " + data.locations().size() + " locations unlocked",
			view.getSummary());
	}

	@Test
	public void patchRulesShowPerPatch()
	{
		LocationsView.Entry guild = entry(build("guild"), "Farming Guild");
		LocationsView.PatchLine fruit = guild.getPatches().stream()
			.filter(p -> p.getName().equals("Fruit tree")).findFirst().orElseThrow();
		assertEquals(false, fruit.isUsable());
		assertEquals("Farming 85", fruit.getReason());
		assertTrue(guild.getPatches().stream().anyMatch(p -> p.getName().equals("Herb") && p.isUsable()));
	}

	@Test
	public void kindsAreSummarised()
	{
		assertEquals("2 allotment, flower, herb", entry(build("catherby"), "Catherby").getKinds());
	}

	@Test
	public void teleportsListTheDefaultFirstAndWarnAboutSpellbooks()
	{
		LocationsView.Entry catherby = entry(build("catherby"), "Catherby");
		assertEquals("camelot_teleport", catherby.getTeleportId());
		assertTrue(catherby.getTeleports().get(0).isDefault());
		assertNull(catherby.getTeleportWarning());
		assertEquals("Needs the Lunar spellbook (you're on Normal)",
			LocationsViewBuilder.teleportWarning(data.teleport("catherby_teleport"), "Normal"));
	}

	@Test
	public void searchMatchesNamesAndKinds()
	{
		List<String> herbs = build("herb").getUnlocked().stream().map(LocationsView.Entry::getName)
			.collect(Collectors.toList());
		assertTrue(herbs.contains("Troll Stronghold"));
		assertTrue(herbs.stream().noneMatch(n -> n.equals("Lumbridge tree")));
	}

	private static LocationsView.Entry entry(LocationsView view, String name)
	{
		return view.getUnlocked().stream().filter(e -> e.getName().equals(name)).findFirst()
			.orElseThrow(() -> new AssertionError("no " + name));
	}
}
