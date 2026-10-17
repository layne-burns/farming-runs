/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.TeleportChoice;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * The run in progress, if any: what was started and when. Phase 7 adds the route and moving from stop to stop.
 * Safe to read from any thread.
 */
@Slf4j
@Singleton
public class RunSession
{
	@Value
	public static class ActiveRun
	{
		RunSetup setup;
		PlantingPlan plan;
		/** The teleport picked for each location, by location id. */
		Map<String, TeleportChoice> teleports;
		long startedAtMillis;

		public List<Patch> patches()
		{
			return plan.getAssignments().stream().map(PlantingPlan.Assignment::getPatch)
				.collect(Collectors.toList());
		}

		public long locationCount()
		{
			return patches().stream().map(Patch::getLocationId).distinct().count();
		}
	}

	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile ActiveRun active;

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	@Nullable
	public ActiveRun active()
	{
		return active;
	}

	public void start(ActiveRun run)
	{
		active = run;
		log.debug("[farmrun] started: {} patches at {} locations", run.patches().size(), run.locationCount());
		listeners.forEach(Runnable::run);
	}

	public void stop()
	{
		if (active != null)
		{
			active = null;
			log.debug("[farmrun] stopped");
			listeners.forEach(Runnable::run);
		}
	}
}
