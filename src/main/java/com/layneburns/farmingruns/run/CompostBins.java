/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.vendor.timetracking.CropState;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import com.layneburns.farmingruns.vendor.timetracking.PatchState;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * What a compost bin needs on a run, from its state: collect what's in it when it's ready, then fill it (with produce
 * that makes supercompost, {@link CompostBinFill}) and close the lid. A bin that's rotting needs nothing. Pure: unit
 * tested.
 */
public final class CompostBins
{
	/** The Farming Guild's big bin holds 30; the others 15. */
	private static final int BIG = 30;
	private static final int SMALL = 15;

	/** A bin's work: items to take out (a bucket each), items to put in, and whether to visit it at all. */
	@Value
	public static class Work
	{
		public static final Work NONE = new Work(0, 0, false);

		int collect;
		int fill;
		/** Something to do: collect, fill or close it. */
		boolean needed;
	}

	private CompostBins()
	{
	}

	public static int capacity(PatchImplementation kind)
	{
		return kind == PatchImplementation.BIG_COMPOST ? BIG : SMALL;
	}

	/**
	 * @param state  the bin as last seen; null when never seen (then nothing is planned)
	 * @param rotted a closed bin has finished rotting (Time Tracking's prediction)
	 */
	public static Work work(@Nullable PatchState state, PatchImplementation kind, boolean rotted)
	{
		if (state == null)
		{
			return Work.NONE;
		}
		int capacity = capacity(kind);
		// The stage counts the items in the bin, less one.
		int items = state.getStage() + 1;
		switch (state.getCropState())
		{
			case EMPTY:
				return new Work(0, capacity, true);
			case FILLING:
				return new Work(0, Math.max(0, capacity - items), true);
			case HARVESTABLE:
				return new Work(items, capacity, true);
			case GROWING:
				// A closed bin is full; once rotted, open it, collect the lot and fill it again.
				return rotted ? new Work(capacity, capacity, true) : Work.NONE;
			default:
				return Work.NONE;
		}
	}

	/** Done for this run: refilled and closed, rotting again. */
	public static boolean isDone(@Nullable PatchState state)
	{
		return state != null && state.getCropState() == CropState.GROWING;
	}
}
