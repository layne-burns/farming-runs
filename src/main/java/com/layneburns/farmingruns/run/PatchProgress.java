/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.vendor.timetracking.CropState;
import com.layneburns.farmingruns.vendor.timetracking.PatchState;
import com.layneburns.farmingruns.vendor.timetracking.Produce;
import javax.annotation.Nullable;

/**
 * Whether a patch's part of the run is finished, from the state its varbit shows (SPEC §2.6). Payment can't be seen
 * this way, so it doesn't hold a patch up; the reminder covers it. Pure: unit tested.
 */
public final class PatchProgress
{
	private PatchProgress()
	{
	}

	/** The patch was grown and now isn't: its crop has been harvested (or cleared). */
	public static boolean harvested(@Nullable PatchState before, @Nullable PatchState after)
	{
		return before != null && after != null && before.getCropState() == CropState.HARVESTABLE
			&& after.getCropState() != CropState.HARVESTABLE;
	}

	/**
	 * <ul>
	 * <li>A seed to plant: something is growing there, freshly planted (stage 0).</li>
	 * <li>A tree kept standing: its crop has all been picked (harvestable, none left).</li>
	 * <li>Nothing to plant: the patch has been cleared, or something was planted anyway.</li>
	 * <li>A compost bin: filled and closed, rotting.</li>
	 * </ul>
	 * The run only ticks a patch when its state changes to done while you're there, not from a first look.
	 */
	public static boolean isDone(PlantingPlan.Assignment assignment, @Nullable PatchState state)
	{
		if (state == null)
		{
			return false;
		}
		if (assignment.getPatch().isCompostBin())
		{
			return CompostBins.isDone(state);
		}
		boolean freshlyPlanted = state.getProduce() != Produce.WEEDS && state.getCropState() == CropState.GROWING
			&& state.getStage() == 0;
		if (assignment.isKeptStanding())
		{
			return state.getCropState() == CropState.HARVESTABLE && state.getStage() == 0;
		}
		if (assignment.getSeed() != null)
		{
			return freshlyPlanted;
		}
		return state.getProduce() == Produce.WEEDS || freshlyPlanted;
	}
}
