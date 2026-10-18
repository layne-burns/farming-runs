/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.RunType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class SavedRunTest
{
	private final Gson gson = new Gson();
	private final GameData data = new DataLoader(gson).load();

	private RunSession.ActiveRun run()
	{
		PlantingPlan.Assignment herb = new PlantingPlan.Assignment(data.patch("catherby.herb"), data.seedByItem(5295),
			false);
		PlantingPlan.Assignment allotment = new PlantingPlan.Assignment(data.patch("catherby.allotment_north"),
			data.seedByItem(5321), false, false, null, true);
		PlantingPlan.Assignment weiss = new PlantingPlan.Assignment(data.patch("weiss.herb"), data.seedByItem(5295),
			false);
		PlantingPlan plan = new PlantingPlan(List.of(herb, allotment, weiss), List.of("a warning"),
			Map.of(data.seedByItem(5288), 2));
		RunProgress progress = RunProgress.start(List.of(new RunProgress.Stop("catherby", List.of(herb, allotment)),
			new RunProgress.Stop("weiss", List.of(weiss))), Set.of("catherby.herb"), Set.of("weiss.herb"), Set.of())
			.markWorked("catherby.herb").markWorked("catherby.allotment_north");
		return new RunSession.ActiveRun(RunSetup.EMPTY.withRunTypes(Set.of(RunType.HERB, RunType.ALLOTMENT)), plan,
			Map.of("catherby", data.teleportChoice("camelot_teleport"), "weiss", data.teleportChoice("house_portal:weiss")),
			1_000L, progress, Set.of("weiss.herb"));
	}

	@Test
	public void aRunComesBackAsItWas()
	{
		RunSession.ActiveRun run = run();
		String json = SavedRun.toJson(gson, run, 5_000L);
		assertEquals(run, SavedRun.fromJson(gson, json, data));
		assertEquals(5_000L, SavedRun.savedAt(gson, json));
	}

	@Test
	public void aDamagedOrStaleSaveReadsAsNothing()
	{
		assertNull(SavedRun.fromJson(gson, null, data));
		assertNull(SavedRun.fromJson(gson, "not json {", data));
		String json = SavedRun.toJson(gson, run(), 5_000L).replace("weiss.herb", "nowhere.herb");
		assertNull(SavedRun.fromJson(gson, json, data));
	}
}
