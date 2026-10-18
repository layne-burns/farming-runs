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
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.state.LocationStatus;
import com.layneburns.farmingruns.state.PatchStatus;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;

public class LocationsViewBuilderTest
{
	private static final LocationStatus OPEN = LocationStatus.of(true, List.of(), null);
	private static final long NOW = 1_800_000_000L;

	/** Catherby's herb is ready; everything else is growing, ready in an hour. */
	private static PatchStatus state(Patch patch)
	{
		return patch.getId().equals("catherby.herb")
			? new PatchStatus(PatchStatus.Readiness.READY, "Ranarr", 0, 4, 5)
			: new PatchStatus(PatchStatus.Readiness.GROWING, "Guam", NOW + 3600, 1, 5);
	}
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	/** Everything unlocked except Prifddinas (Song of the Elves) and the guild's advanced tier (Farming 80). */
	private static LocationsView build(String query, Set<RunType> types)
	{
		return build(query, types, LocationSort.NAME);
	}

	private static LocationsView build(String query, Set<RunType> types, LocationSort sort)
	{
		LocationsContext context = LocationsContext.builder()
			.statusOf(l -> l.getId().equals("prifddinas")
				? LocationStatus.of(true, List.of("Quest: Song of the Elves"), null) : OPEN)
			.patchRuleOf(p -> "farming_guild_advanced".equals(p.getUnlockRuleId())
				? LocationStatus.of(true, List.of("Farming 85"), null) : OPEN)
			.teleportsOf(Location::getDefaultTeleports)
			.customTeleports(l -> false)
			.spellbook("Normal")
			.patchStateOf(LocationsViewBuilderTest::state)
			.nowSeconds(NOW)
			.timeTrackingOn(true)
			.build();
		return LocationsViewBuilder.build(data, context, query, types, sort);
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
	public void patchStatesShowAtAGlanceAndPerPatch()
	{
		LocationsView view = buildAll("catherby");
		LocationsView.Entry catherby = section(view, "HERB").getEntries().get(0);
		assertEquals("1 ready", catherby.getReadySummary());
		assertEquals(PatchStatus.Readiness.READY, catherby.getSummaryReadiness());
		LocationsView.PatchLine herb = catherby.getPatches().stream()
			.filter(p -> p.getName().equals("Herb")).findFirst().orElseThrow();
		assertEquals("Ranarr", herb.getCrop());
		assertEquals("ready", herb.getState());

		LocationsView.Entry fruit = section(view, "FRUIT_TREE").getEntries().get(0);
		assertEquals("in 1 h", fruit.getReadySummary());
		assertEquals(PatchStatus.Readiness.GROWING, fruit.getSummaryReadiness());
	}

	@Test
	public void patchNamesAreShortAndNotRepeated()
	{
		assertEquals("Allotment N", LocationsViewBuilder.patchName(data.patch("catherby.allotment_north")));
		assertEquals("Allotment NW", LocationsViewBuilder.patchName(data.patch("falador.allotment_north_west")));
		assertEquals("Anima", LocationsViewBuilder.patchName(data.patch("farming_guild.anima")));
		assertEquals("Herb", LocationsViewBuilder.patchName(data.patch("catherby.herb")));
	}

	@Test
	public void kindsAreSummarised()
	{
		LocationsView.Entry catherby = section(buildAll("catherby"), "HERB").getEntries().get(0);
		assertEquals("2 allotment, flower, herb", catherby.getKinds());
	}

	@Test
	public void teleportsAreRankedAndWarnAboutSpellbooks()
	{
		LocationsView.Entry catherby = section(buildAll("catherby"), "HERB").getEntries().get(0);
		assertEquals(List.of("camelot_teleport", "catherby_teleport", "camelot_tablet"), catherby.getTeleports().stream()
			.map(LocationsView.TeleportLine::getRef).collect(Collectors.toList()));
		assertNull(catherby.getTeleports().get(0).getWarning());
		assertEquals("Needs the Lunar spellbook (you're on Normal)", catherby.getTeleports().get(1).getWarning());
	}

	@Test
	public void addMenuGroupsMethodsAndMarksListedOnes()
	{
		LocationsView.Entry ardougne = section(buildAll("ardougne"), "HERB").getEntries().get(0);
		List<String> groups = ardougne.getAddMenu().stream().map(LocationsView.TeleportMenuGroup::getTitle)
			.collect(Collectors.toList());
		assertEquals("Jewellery", groups.get(0));
		assertTrue(groups.contains("Fairy ring"));

		LocationsView.TeleportMenuMethod skills = ardougne.getAddMenu().get(0).getMethods().stream()
			.filter(m -> m.getName().equals("Skills necklace")).findFirst().orElseThrow();
		assertEquals(6, skills.getOptions().size());
		LocationsView.TeleportOption fishing = skills.getOptions().stream()
			.filter(o -> o.getRef().equals("skills_necklace:fishing_guild")).findFirst().orElseThrow();
		assertTrue("already in Ardougne's list", fishing.isListed());
	}

	@Test
	public void fairyRingsShowTheLocationsCode()
	{
		LocationsView.Entry guild = section(buildAll("guild"), "HERB").getEntries().stream()
			.filter(e -> e.getName().equals("Farming Guild")).findFirst().orElseThrow();
		LocationsView.TeleportLine fairy = guild.getTeleports().stream()
			.filter(t -> t.getRef().equals("fairy_ring:cir")).findFirst().orElseThrow();
		assertEquals("Fairy ring CIR", fairy.getName());
		assertTrue(fairy.getDetail().contains("Mount Karuulm"));
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

	@Test
	public void theGlanceOnlyCallsFullyGrownPatchesReady()
	{
		long now = 1_000_000;
		LocationsView.PatchLine growing = new LocationsView.PatchLine("Herb", "Ranarr", "24 m",
			PatchStatus.Readiness.GROWING, true, null);
		LocationsView.PatchLine empty = new LocationsView.PatchLine("Allotment", null, "empty",
			PatchStatus.Readiness.EMPTY, true, null);
		LocationsView.PatchLine ready = new LocationsView.PatchLine("Herb", "Ranarr", "ready",
			PatchStatus.Readiness.READY, true, null);
		PatchStatus herbIn24 = new PatchStatus(PatchStatus.Readiness.GROWING, "Ranarr", now + 24 * 60, 2, 4);

		// The herb section looks at the herb patch only: growing, so when it's ready.
		assertEquals("in 24 m", LocationsViewBuilder.readySummary(List.of(growing), List.of(herbIn24), now));
		// Empty isn't "ready".
		assertEquals("2 empty", LocationsViewBuilder.readySummary(List.of(empty, empty), List.of(), now));
		assertEquals("1 ready", LocationsViewBuilder.readySummary(List.of(ready, empty), List.of(), now));
	}

	@Test
	public void readyFirstPutsReadyLocationsOnTopWithOrWithoutSections()
	{
		LocationsView bySection = build("", EnumSet.of(RunType.HERB), LocationSort.READY);
		assertEquals("catherby", bySection.getSections().get(0).getEntries().get(0).getId());
		LocationsView all = build("", EnumSet.of(RunType.HERB, RunType.ALLOTMENT), LocationSort.READY_ALL);
		assertEquals("ALL", all.getSections().get(0).getKey());
		assertEquals("catherby", all.getSections().get(0).getEntries().get(0).getId());
	}
}
