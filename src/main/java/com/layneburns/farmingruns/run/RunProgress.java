/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Where a run is up to (SPEC §2.5–2.6): its stops in order, which patches are worked (planted, picked or cleared,
 * as their varbits show), which still wait for the gardener's payment, which stops you skipped, and the current
 * stop. A patch is done when it's worked and paid for (if it's to be paid for). When every patch at the current stop
 * is done it moves on by itself to the next stop that isn't done or skipped. Immutable: each change returns a new
 * one. Pure: unit tested.
 */
@Value
public class RunProgress
{
	/** A location to visit and its patches in this run. */
	@Value
	public static class Stop
	{
		String locationId;
		List<PlantingPlan.Assignment> patches;
	}

	/** Value of {@link #current} once every stop is done or skipped. */
	public static final int FINISHED = -1;

	List<Stop> stops;
	/** Patches whose varbit shows the work done. */
	Set<String> workedPatches;
	/** Patches to be paid for whose payment hasn't been seen yet. */
	Set<String> awaitingPayment;
	Set<String> skippedStops;
	/** Index into {@link #stops}, or {@link #FINISHED}. */
	int current;

	/** A fresh run with nothing to pay for. */
	public static RunProgress start(List<Stop> stops)
	{
		return start(stops, Set.of());
	}

	/**
	 * A fresh run at its first stop (moving past any stop that's already done).
	 *
	 * @param payFor patch ids whose crop the gardener is to be paid to protect
	 */
	public static RunProgress start(List<Stop> stops, Set<String> payFor)
	{
		RunProgress progress = new RunProgress(List.copyOf(stops), Set.of(), Set.copyOf(payFor), Set.of(),
			stops.isEmpty() ? FINISHED : 0);
		return settle(progress);
	}

	@Nullable
	public Stop currentStop()
	{
		return current == FINISHED ? null : stops.get(current);
	}

	public boolean isFinished()
	{
		return current == FINISHED;
	}

	public boolean isDone(Stop stop)
	{
		return stop.getPatches().stream().allMatch(a -> isPatchDone(a.getPatch().getId()));
	}

	/** Worked, and paid for if it's to be paid for. */
	public boolean isPatchDone(String patchId)
	{
		return workedPatches.contains(patchId) && !awaitingPayment.contains(patchId);
	}

	public boolean isAwaitingPayment(String patchId)
	{
		return awaitingPayment.contains(patchId);
	}

	public boolean isSkipped(Stop stop)
	{
		return skippedStops.contains(stop.getLocationId());
	}

	/** Stops done so far. */
	public long doneCount()
	{
		return stops.stream().filter(this::isDone).count();
	}

	/** A patch's varbit shows the work done; moves on if that finishes the current stop. */
	public RunProgress markWorked(String patchId)
	{
		if (workedPatches.contains(patchId))
		{
			return this;
		}
		Set<String> worked = new HashSet<>(workedPatches);
		worked.add(patchId);
		return settle(new RunProgress(stops, Set.copyOf(worked), awaitingPayment, skippedStops, current));
	}

	/** A patch's payment has been seen; moves on if that finishes the current stop. */
	public RunProgress markPaid(String patchId)
	{
		if (!awaitingPayment.contains(patchId))
		{
			return this;
		}
		Set<String> awaiting = new HashSet<>(awaitingPayment);
		awaiting.remove(patchId);
		return settle(new RunProgress(stops, workedPatches, Set.copyOf(awaiting), skippedStops, current));
	}

	/** Leaves a stop out; moves on if it was the current one. */
	public RunProgress skip(String locationId)
	{
		Set<String> skipped = new HashSet<>(skippedStops);
		skipped.add(locationId);
		RunProgress next = new RunProgress(stops, workedPatches, awaitingPayment, Set.copyOf(skipped), current);
		Stop at = currentStop();
		return at != null && at.getLocationId().equals(locationId) ? next.advanced() : next;
	}

	/** Makes a stop the current one (taking it off the skipped list). */
	public RunProgress goTo(String locationId)
	{
		for (int i = 0; i < stops.size(); i++)
		{
			if (stops.get(i).getLocationId().equals(locationId))
			{
				Set<String> skipped = new HashSet<>(skippedStops);
				skipped.remove(locationId);
				return new RunProgress(stops, workedPatches, awaitingPayment, Set.copyOf(skipped), i);
			}
		}
		return this;
	}

	/** Moves on when the current stop has just been finished. */
	private static RunProgress settle(RunProgress next)
	{
		Stop at = next.currentStop();
		return at != null && next.isDone(at) ? next.advanced() : next;
	}

	/** The next stop after the current one that's neither done nor skipped, looking from the top again if needed. */
	private RunProgress advanced()
	{
		int from = current == FINISHED ? -1 : current;
		for (int step = 1; step <= stops.size(); step++)
		{
			int i = (from + step) % stops.size();
			Stop stop = stops.get(i);
			if (!isDone(stop) && !isSkipped(stop))
			{
				return new RunProgress(stops, workedPatches, awaitingPayment, skippedStops, i);
			}
		}
		return new RunProgress(stops, workedPatches, awaitingPayment, skippedStops, FINISHED);
	}
}
