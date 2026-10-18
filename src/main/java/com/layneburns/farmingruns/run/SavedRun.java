/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.data.TeleportChoice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A run in progress as text, so it can be picked up again after a restart or a new login: the setup, the plan, each
 * stop's teleport, and how far it got. Patches, seeds and teleports are saved by id and looked up in the game data
 * when it's read back; a run that no longer fits the data (a patch or seed gone) reads as null. Pure: unit tested.
 */
public final class SavedRun
{
	/** Bumped if the format changes; older saves are ignored. */
	private static final int VERSION = 1;

	/** What's written: plain fields only. */
	static class Saved
	{
		int version;
		String setup;
		long startedAtMillis;
		long savedAtMillis;
		List<SavedAssignment> plan;
		List<String> warnings;
		Map<Integer, Integer> saplingsToGrow;
		Map<String, String> teleports;
		List<SavedStop> stops;
		Set<String> worked;
		Set<String> awaitingPayment;
		Set<String> awaitingCompost;
		Set<String> harvestFirst;
		Set<String> harvested;
		Set<String> skipped;
		int current;
		Set<String> waitFor;
	}

	static class SavedStop
	{
		String locationId;
		List<SavedAssignment> patches;
	}

	static class SavedAssignment
	{
		String patch;
		/** 0 when nothing is planted. */
		int seed;
		boolean contract;
		boolean kept;
		int standing;
		boolean unprotected;
	}

	private SavedRun()
	{
	}

	public static String toJson(Gson gson, RunSession.ActiveRun run, long nowMillis)
	{
		Saved saved = new Saved();
		saved.version = VERSION;
		saved.setup = SetupJson.toJson(gson, run.getSetup());
		saved.startedAtMillis = run.getStartedAtMillis();
		saved.savedAtMillis = nowMillis;
		saved.plan = assignments(run.getPlan().getAssignments());
		saved.warnings = run.getPlan().getWarnings();
		saved.saplingsToGrow = new HashMap<>();
		run.getPlan().getSaplingsToGrow().forEach((seed, count) -> saved.saplingsToGrow.put(seed.getItemId(), count));
		saved.teleports = new LinkedHashMap<>();
		run.getTeleports().forEach((location, teleport) -> saved.teleports.put(location, teleport.getRef()));
		RunProgress progress = run.getProgress();
		saved.stops = new ArrayList<>();
		for (RunProgress.Stop stop : progress.getStops())
		{
			SavedStop s = new SavedStop();
			s.locationId = stop.getLocationId();
			s.patches = assignments(stop.getPatches());
			saved.stops.add(s);
		}
		saved.worked = progress.getWorkedPatches();
		saved.awaitingPayment = progress.getAwaitingPayment();
		saved.awaitingCompost = progress.getAwaitingCompost();
		saved.harvestFirst = progress.getHarvestFirst();
		saved.harvested = progress.getHarvested();
		saved.skipped = progress.getSkippedStops();
		saved.current = progress.getCurrent();
		saved.waitFor = run.getWaitFor();
		return gson.toJson(saved);
	}

	/** When a saved run was last saved (millis), or 0 if it can't be read. */
	public static long savedAt(Gson gson, @Nullable String json)
	{
		Saved saved = read(gson, json);
		return saved == null ? 0 : saved.savedAtMillis;
	}

	/** The run as saved, or null when there's none, it can't be read, or it no longer fits the game data. */
	@Nullable
	public static RunSession.ActiveRun fromJson(Gson gson, @Nullable String json, GameData data)
	{
		Saved saved = read(gson, json);
		if (saved == null)
		{
			return null;
		}
		try
		{
			RunSetup setup = SetupJson.fromJson(gson, saved.setup);
			if (setup == null)
			{
				return null;
			}
			Map<Seed, Integer> toGrow = new LinkedHashMap<>();
			for (Map.Entry<Integer, Integer> e : orEmpty(saved.saplingsToGrow).entrySet())
			{
				toGrow.put(seed(data, e.getKey()), e.getValue());
			}
			PlantingPlan plan = new PlantingPlan(assignments(saved.plan, data),
				saved.warnings == null ? List.of() : List.copyOf(saved.warnings), Map.copyOf(toGrow));
			Map<String, TeleportChoice> teleports = new LinkedHashMap<>();
			for (Map.Entry<String, String> e : orEmpty(saved.teleports).entrySet())
			{
				TeleportChoice choice = data.teleportChoice(e.getValue());
				if (choice != null)
				{
					teleports.put(e.getKey(), choice);
				}
			}
			List<RunProgress.Stop> stops = new ArrayList<>();
			for (SavedStop s : saved.stops)
			{
				data.location(s.locationId);
				stops.add(new RunProgress.Stop(s.locationId, assignments(s.patches, data)));
			}
			int current = saved.current >= stops.size() ? RunProgress.FINISHED : saved.current;
			RunProgress progress = new RunProgress(List.copyOf(stops), set(saved.worked), set(saved.awaitingPayment),
				set(saved.awaitingCompost), set(saved.harvestFirst), set(saved.harvested), set(saved.skipped), current);
			return new RunSession.ActiveRun(setup, plan, Map.copyOf(teleports), saved.startedAtMillis, progress,
				set(saved.waitFor));
		}
		catch (RuntimeException e)
		{
			// A patch, seed or location that's no longer in the data, or a damaged save.
			return null;
		}
	}

	@Nullable
	private static Saved read(Gson gson, @Nullable String json)
	{
		if (json == null || json.isEmpty())
		{
			return null;
		}
		try
		{
			Saved saved = gson.fromJson(json, Saved.class);
			return saved == null || saved.version != VERSION || saved.stops == null || saved.plan == null ? null : saved;
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return null;
		}
	}

	private static List<SavedAssignment> assignments(List<PlantingPlan.Assignment> assignments)
	{
		List<SavedAssignment> out = new ArrayList<>();
		for (PlantingPlan.Assignment a : assignments)
		{
			SavedAssignment s = new SavedAssignment();
			s.patch = a.getPatch().getId();
			s.seed = a.getSeed() == null ? 0 : a.getSeed().getItemId();
			s.contract = a.isContract();
			s.kept = a.isKeptStanding();
			s.standing = a.getStanding() == null ? 0 : a.getStanding().getItemId();
			s.unprotected = a.isUnprotected();
			out.add(s);
		}
		return out;
	}

	private static List<PlantingPlan.Assignment> assignments(List<SavedAssignment> saved, GameData data)
	{
		List<PlantingPlan.Assignment> out = new ArrayList<>();
		for (SavedAssignment s : saved)
		{
			// Throws for a patch that's gone from the data: the run is then dropped.
			Patch patch = data.patch(s.patch);
			out.add(new PlantingPlan.Assignment(patch, s.seed == 0 ? null : seed(data, s.seed), s.contract, s.kept,
				s.standing == 0 ? null : seed(data, s.standing), s.unprotected));
		}
		return List.copyOf(out);
	}

	private static Seed seed(GameData data, int itemId)
	{
		Seed seed = data.seedByItem(itemId);
		if (seed == null)
		{
			throw new IllegalStateException("no seed " + itemId);
		}
		return seed;
	}

	private static Set<String> set(@Nullable Set<String> saved)
	{
		return saved == null ? Set.of() : Set.copyOf(saved);
	}

	private static <K, V> Map<K, V> orEmpty(@Nullable Map<K, V> map)
	{
		return map == null ? Map.of() : map;
	}
}
