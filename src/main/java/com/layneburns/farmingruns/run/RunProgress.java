/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Value;
import lombok.With;

/**
 * Where a run is up to (SPEC §2.5–2.6): its stops in order, which patches are worked (planted, picked or cleared,
 * as their varbits show), which still wait for compost or the gardener's payment, which had a grown crop to harvest
 * first and whether it has been, which stops you skipped, and the current stop. A patch is done when it's worked,
 * composted and paid for (where it's to be). When every patch at the current stop is done it moves on by itself to
 * the next stop that isn't done or skipped. Immutable: each change returns a new one. Pure: unit tested.
 */
@Value
@With
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
	/** Patches to be composted that the game hasn't said were treated yet. */
	Set<String> awaitingCompost;
	/** Patches with a grown crop to harvest before replanting, when the run started. */
	Set<String> harvestFirst;
	/** Of those, the ones harvested so far. */
	Set<String> harvested;
	Set<String> skippedStops;
	/** Index into {@link #stops}, or {@link #FINISHED}. */
	int current;

	/** A fresh run with nothing to pay for, compost or harvest first. */
	public static RunProgress start(List<Stop> stops)
	{
		return start(stops, Set.of(), Set.of(), Set.of());
	}

	/**
	 * A fresh run at its first stop (moving past any stop that's already done).
	 *
	 * @param payFor       patch ids whose crop the gardener is to be paid to protect
	 * @param compostFor   patch ids to be treated with compost (none for Fertile Soil, which can't be seen)
	 * @param harvestFirst patch ids with a grown crop to harvest before planting
	 */
	public static RunProgress start(List<Stop> stops, Set<String> payFor, Set<String> compostFor,
		Set<String> harvestFirst)
	{
		return settle(new RunProgress(List.copyOf(stops), Set.of(), Set.copyOf(payFor), Set.copyOf(compostFor),
			Set.copyOf(harvestFirst), Set.of(), Set.of(), stops.isEmpty() ? FINISHED : 0));
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

	/** Worked, and composted and paid for where it's to be. */
	public boolean isPatchDone(String patchId)
	{
		return workedPatches.contains(patchId) && !awaitingPayment.contains(patchId)
			&& !awaitingCompost.contains(patchId);
	}

	public boolean isAwaitingCompost(String patchId)
	{
		return awaitingCompost.contains(patchId);
	}

	public boolean isAwaitingPayment(String patchId)
	{
		return awaitingPayment.contains(patchId);
	}

	/** The patch had a grown crop when the run started. */
	public boolean hasHarvestFirst(String patchId)
	{
		return harvestFirst.contains(patchId);
	}

	/** Its grown crop has been harvested (or it has been replanted, which means the same). */
	public boolean isHarvested(String patchId)
	{
		return harvested.contains(patchId) || workedPatches.contains(patchId);
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
		return workedPatches.contains(patchId) ? this : settle(withWorkedPatches(plus(workedPatches, patchId)));
	}

	/** The game says a patch was treated with compost; moves on if that finishes the current stop. */
	public RunProgress markComposted(String patchId)
	{
		return awaitingCompost.contains(patchId) ? settle(withAwaitingCompost(minus(awaitingCompost, patchId))) : this;
	}

	/** A patch's payment has been seen; moves on if that finishes the current stop. */
	public RunProgress markPaid(String patchId)
	{
		return awaitingPayment.contains(patchId) ? settle(withAwaitingPayment(minus(awaitingPayment, patchId))) : this;
	}

	/** A patch's grown crop has been harvested. */
	public RunProgress markHarvested(String patchId)
	{
		return harvested.contains(patchId) ? this : withHarvested(plus(harvested, patchId));
	}

	/** Leaves a stop out; moves on if it was the current one. */
	public RunProgress skip(String locationId)
	{
		RunProgress next = withSkippedStops(plus(skippedStops, locationId));
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
				return withSkippedStops(minus(skippedStops, locationId)).withCurrent(i);
			}
		}
		return this;
	}

	/**
	 * You went somewhere else first: the stops still to do take this order (the first becomes current), after the
	 * stops already done or skipped, which keep their places.
	 *
	 * @param order location ids of the stops still to do, the one to make current first
	 */
	public RunProgress reroute(List<String> order)
	{
		List<Stop> ordered = new ArrayList<>();
		for (Stop stop : stops)
		{
			if (!order.contains(stop.getLocationId()))
			{
				ordered.add(stop);
			}
		}
		int at = ordered.size();
		for (String id : order)
		{
			stops.stream().filter(s -> s.getLocationId().equals(id)).findFirst().ifPresent(ordered::add);
		}
		return withStops(List.copyOf(ordered)).withSkippedStops(minus(skippedStops, order.isEmpty() ? "" : order.get(0)))
			.withCurrent(at < ordered.size() ? at : current);
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
				return withCurrent(i);
			}
		}
		return withCurrent(FINISHED);
	}

	private static Set<String> plus(Set<String> set, String id)
	{
		Set<String> copy = new HashSet<>(set);
		copy.add(id);
		return Set.copyOf(copy);
	}

	private static Set<String> minus(Set<String> set, String id)
	{
		Set<String> copy = new HashSet<>(set);
		copy.remove(id);
		return Set.copyOf(copy);
	}
}
