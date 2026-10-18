/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.state.PatchStates;
import com.layneburns.farmingruns.state.PatchStatus;
import com.layneburns.farmingruns.state.TravelLog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * For a run that includes patches still growing ({@link OnTheWay}): when each stop is ready, so the route gets there
 * no sooner, and a better order when you'd reach the next stop before it's ready. Only the patches the run chose
 * to wait for count; ones you ticked to "visit anyway" don't hold a stop back. Travel and work times are your own where
 * known ({@link TravelLog}).
 */
@Singleton
public class GrowingWait
{
	private final TravelLog travelLog;
	private final PatchStates states;

	@Inject
	GrowingWait(TravelLog travelLog, PatchStates states)
	{
		this.travelLog = travelLog;
		this.states = states;
	}

	/** Each stop's readiness and time there, for these stops' patches, with these travel and work times. */
	public RoutePlanner.Timing timing(Map<String, List<Patch>> byStop, Set<String> waitFor, LearnedCosts costs)
	{
		long now = System.currentTimeMillis() / 1000;
		return OnTheWay.timing(byStop, p -> waitFor.contains(p.getId()) ? readyIn(p, now) : 0, costs::work);
	}

	/**
	 * The stops still to do in a better order when you'd get to the current one before it's ready and something else
	 * should come first; null to keep going as planned.
	 *
	 * @param from the run location you're at, if any
	 */
	@Nullable
	public List<String> betterOrder(RunSession.ActiveRun run, @Nullable String from)
	{
		RunProgress progress = run.getProgress();
		RunProgress.Stop current = progress.currentStop();
		if (current == null || run.getWaitFor().isEmpty())
		{
			return null;
		}
		Map<String, List<Patch>> rest = new LinkedHashMap<>();
		rest.put(current.getLocationId(), patches(current));
		progress.getStops().stream().filter(s -> !progress.isDone(s) && !progress.isSkipped(s))
			.forEach(s -> rest.putIfAbsent(s.getLocationId(), patches(s)));
		LearnedCosts costs = travelLog.costs();
		RoutePlanner.Timing timing = timing(rest, run.getWaitFor(), costs);
		if (timing.readyAt(current.getLocationId()) <= costs.cost(from, current.getLocationId()))
		{
			return null;
		}
		List<String> order = RoutePlanner.order(from, rest.keySet(), costs, timing);
		return order.isEmpty() || order.get(0).equals(current.getLocationId()) ? null : order;
	}

	private int readyIn(Patch patch, long now)
	{
		PatchStatus status = states.status(patch, now);
		return status.getReadiness() == PatchStatus.Readiness.GROWING && status.getReadyAtSeconds() > now
			? OnTheWay.ticks(status.getReadyAtSeconds() - now) : 0;
	}

	private static List<Patch> patches(RunProgress.Stop stop)
	{
		return stop.getPatches().stream().map(PlantingPlan.Assignment::getPatch).collect(Collectors.toList());
	}
}
