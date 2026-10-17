/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Run setups to and from JSON, for saving. Anything missing or unreadable (an older save, a renamed kind) falls back
 * to the default instead of failing, so a bad save never breaks the Run tab. Pure: unit tested.
 */
public final class SetupJson
{
	private SetupJson()
	{
	}

	public static String toJson(Gson gson, RunSetup setup)
	{
		return gson.toJson(setup);
	}

	/** The setup in {@code json}, or null if there's nothing readable. */
	@Nullable
	public static RunSetup fromJson(Gson gson, @Nullable String json)
	{
		if (json == null || json.isBlank())
		{
			return null;
		}
		try
		{
			RunSetup raw = gson.fromJson(json, RunSetup.class);
			return raw == null ? null : tidy(raw);
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return null;
		}
	}

	/** Fills gaps left by an older or partial save with defaults, and makes every collection immutable. */
	static RunSetup tidy(RunSetup raw)
	{
		Set<RunType> types = new HashSet<>();
		if (raw.getRunTypes() != null)
		{
			raw.getRunTypes().stream().filter(t -> t != null).forEach(types::add);
		}
		Map<PatchImplementation, KindPlan> kinds = new EnumMap<>(PatchImplementation.class);
		if (raw.getKinds() != null)
		{
			raw.getKinds().forEach((kind, plan) ->
			{
				if (kind != null && plan != null)
				{
					kinds.put(kind, new KindPlan(plan.isAuto(),
						plan.getCrops() == null ? List.of() : List.copyOf(plan.getCrops()), plan.isPay(),
						plan.getTreeRoutine() == null ? TreeRoutine.PAY_TO_REMOVE : plan.getTreeRoutine(),
						plan.isSkipHarvest()));
				}
			});
		}
		Set<String> skipped = raw.getSkippedPatches() == null ? Set.of() : Set.copyOf(raw.getSkippedPatches());
		CompostChoice compost = raw.getCompost() == null ? CompostChoice.SUPERCOMPOST : raw.getCompost();
		return new RunSetup(Set.copyOf(types), Map.copyOf(kinds), skipped, compost);
	}
}
