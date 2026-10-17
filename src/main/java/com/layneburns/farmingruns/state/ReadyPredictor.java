/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.layneburns.farmingruns.vendor.timetracking.CropState;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import com.layneburns.farmingruns.vendor.timetracking.PatchState;
import com.layneburns.farmingruns.vendor.timetracking.Produce;
import javax.annotation.Nullable;

/**
 * Works out a patch's state and ready time from what Time Tracking saved: "varbitValue:epochSeconds". A port of
 * Time Tracking's own predictPatch and getTickTime (RuneLite, FarmingTracker), so both agree. Pure: unit tested.
 */
public final class ReadyPredictor
{
	private ReadyPredictor()
	{
	}

	/**
	 * @param stored             Time Tracking's saved value for the patch, or null if it has none
	 * @param offsetPrecisionMin Time Tracking's "farmTickOffsetPrecision", or null
	 * @param offsetMin          Time Tracking's "farmTickOffset", or null
	 * @param autoweed           true when autoweed is on (then weeds count as empty)
	 */
	public static PatchStatus predict(@Nullable String stored, PatchImplementation kind,
		@Nullable Integer offsetPrecisionMin, @Nullable Integer offsetMin, boolean autoweed, long nowSeconds)
	{
		if (stored == null)
		{
			return PatchStatus.UNKNOWN;
		}
		String[] parts = stored.split(":");
		int value;
		long seenAt;
		try
		{
			value = Integer.parseInt(parts[0]);
			seenAt = parts.length == 2 ? Long.parseLong(parts[1]) : 0;
		}
		catch (NumberFormatException e)
		{
			return PatchStatus.UNKNOWN;
		}
		PatchState state = seenAt > 0 ? kind.forVarbitValue(value) : null;
		if (state == null)
		{
			return PatchStatus.UNKNOWN;
		}

		boolean weeds = state.getProduce() == Produce.WEEDS || state.getProduce() == Produce.SCARECROW;
		int stage = state.getStage();
		int stages = state.getStages();
		int tickRate = state.getTickRate();
		if (weeds && autoweed)
		{
			tickRate = 0;
		}

		long readyAt = 0;
		if (tickRate > 0)
		{
			long tickNow = tickTime(tickRate, 0, nowSeconds, offsetPrecisionMin, offsetMin);
			long tickSeen = tickTime(tickRate, 0, seenAt, offsetPrecisionMin, offsetMin);
			int grown = (int) ((tickNow - tickSeen) / (tickRate * 60L));
			readyAt = tickTime(tickRate, stages - 1 - stage, tickSeen, offsetPrecisionMin, offsetMin);
			stage = Math.min(stage + grown, stages - 1);
		}

		String crop = weeds ? null : state.getProduce().getName();
		return new PatchStatus(readiness(state.getCropState(), weeds, readyAt, nowSeconds), crop, readyAt, stage, stages);
	}

	private static PatchStatus.Readiness readiness(CropState cropState, boolean weeds, long readyAt, long nowSeconds)
	{
		if (weeds || cropState == CropState.EMPTY)
		{
			return PatchStatus.Readiness.EMPTY;
		}
		switch (cropState)
		{
			case HARVESTABLE:
				return PatchStatus.Readiness.READY;
			case DISEASED:
				return PatchStatus.Readiness.DISEASED;
			case DEAD:
				return PatchStatus.Readiness.DEAD;
			default:
				return readyAt > 0 && readyAt <= nowSeconds ? PatchStatus.Readiness.READY : PatchStatus.Readiness.GROWING;
		}
	}

	/**
	 * The time of a farming tick: the tick {@code ticks} after the one containing {@code atSeconds}. Farming ticks are
	 * shifted per account by an offset Time Tracking measures; it's used once it's been measured precisely enough.
	 */
	static long tickTime(int tickRateMin, int ticks, long atSeconds, @Nullable Integer offsetPrecisionMin,
		@Nullable Integer offsetMin)
	{
		long offset = 0;
		if (offsetPrecisionMin != null && offsetMin != null
			&& (offsetPrecisionMin >= tickRateMin || offsetPrecisionMin >= 40))
		{
			offset = (offsetMin % tickRateMin) * 60L;
		}
		long shifted = atSeconds + offset;
		long currentTick = shifted - shifted % (tickRateMin * 60L);
		return currentTick + ticks * tickRateMin * 60L - offset;
	}
}
