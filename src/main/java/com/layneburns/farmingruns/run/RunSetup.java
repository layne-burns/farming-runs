/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import lombok.With;

/**
 * Everything you choose for a run (SPEC §2.3): which run types, a plan per patch kind, patches to skip, and the
 * compost. Immutable (change it with the {@code with...} methods) and saved as JSON for presets.
 */
@Value
@With
public class RunSetup
{
	public static final RunSetup EMPTY = new RunSetup(Set.of(), Map.of(), Set.of(), CompostChoice.SUPERCOMPOST, Set.of());

	Set<RunType> runTypes;
	/** Plans for the kinds you've changed; any other kind is {@link KindPlan#AUTO}. */
	Map<PatchImplementation, KindPlan> kinds;
	/** Patch ids to leave out of the run. */
	Set<String> skippedPatches;
	CompostChoice compost;
	/** Kinds left out of the run though a chosen run type covers them (spirit trees on a special trees run). */
	Set<PatchImplementation> leftOutKinds;

	public KindPlan kindPlan(PatchImplementation kind)
	{
		return kinds.getOrDefault(kind, KindPlan.AUTO);
	}

	public RunSetup withKindPlan(PatchImplementation kind, KindPlan plan)
	{
		Map<PatchImplementation, KindPlan> copy = new EnumMap<>(PatchImplementation.class);
		copy.putAll(kinds);
		copy.put(kind, plan);
		return withKinds(Map.copyOf(copy));
	}

	public RunSetup withSkipped(String patchId, boolean skipped)
	{
		Set<String> copy = new HashSet<>(skippedPatches);
		if (skipped)
		{
			copy.add(patchId);
		}
		else
		{
			copy.remove(patchId);
		}
		return withSkippedPatches(Set.copyOf(copy));
	}

	/** True when a run type you chose covers this kind, and you haven't left it out. */
	public boolean includes(PatchImplementation kind)
	{
		return !leftOutKinds.contains(kind) && runTypes.stream().anyMatch(t -> t.covers(kind));
	}

	/** Leaves a kind out of the run, or puts it back. */
	public RunSetup withLeftOut(PatchImplementation kind, boolean leftOut)
	{
		Set<PatchImplementation> copy = new HashSet<>(leftOutKinds);
		if (leftOut)
		{
			copy.add(kind);
		}
		else
		{
			copy.remove(kind);
		}
		return withLeftOutKinds(Set.copyOf(copy));
	}
}
