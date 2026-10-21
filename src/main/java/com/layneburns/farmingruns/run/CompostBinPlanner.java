/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.state.PatchStates;
import com.layneburns.farmingruns.state.PatchStatus;
import com.layneburns.farmingruns.vendor.timetracking.CropState;
import com.layneburns.farmingruns.vendor.timetracking.PatchState;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The compost bins (the Farming Guild's big one) at a run's stops that need work, from Time Tracking's last look:
 * the run adds them to their stop ({@link CompostBins}). Any thread.
 */
@Singleton
public class CompostBinPlanner
{
	private final GameData data;
	private final PatchStates states;

	@Inject
	CompostBinPlanner(GameData data, PatchStates states)
	{
		this.data = data;
		this.states = states;
	}

	/** Each bin at these locations with something to do, and what. */
	public Map<Patch, CompostBins.Work> work(Collection<String> locationIds)
	{
		Map<Patch, CompostBins.Work> work = new LinkedHashMap<>();
		for (String id : locationIds)
		{
			for (Patch bin : data.location(id).getPatches())
			{
				if (!bin.isCompostBin())
				{
					continue;
				}
				CompostBins.Work need = work(bin);
				if (need.isNeeded())
				{
					work.put(bin, need);
				}
			}
		}
		return work;
	}

	/** What this bin needs now, from Time Tracking's last look. */
	public CompostBins.Work work(Patch bin)
	{
		PatchState state = states.lastSeen(bin);
		boolean rotted = state != null && state.getCropState() == CropState.GROWING
			&& states.status(bin, System.currentTimeMillis() / 1000).getReadiness() == PatchStatus.Readiness.READY;
		return CompostBins.work(state, bin.getKind(), rotted);
	}
}
