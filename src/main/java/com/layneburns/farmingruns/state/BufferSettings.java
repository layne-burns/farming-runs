/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.Set;
import lombok.Value;

/**
 * How many of each seed to keep back (for farming contracts and retries): never planted by the auto-picker
 * and not counted as spare. For trees it applies to saplings, which are what gets planted. Only kinds a contract can
 * ask for keep any: hops, mushrooms, hardwood, spirit trees and the like are all spare.
 */
@Value
public class BufferSettings
{
	int allotment;
	int sapling;
	int other;
	/** Kinds the buffer applies to: those a farming contract can ask for ({@link GameData#contractKinds()}). */
	Set<PatchImplementation> contractKinds;

	/** The current settings from the plugin's configuration. */
	public static BufferSettings from(FarmingRunsConfig config, GameData data)
	{
		return new BufferSettings(config.allotmentBuffer(), config.saplingBuffer(), config.otherBuffer(),
			data.contractKinds());
	}

	public int bufferFor(Seed seed)
	{
		if (!contractKinds.contains(seed.getKind()))
		{
			return 0;
		}
		if (seed.getKind() == PatchImplementation.ALLOTMENT)
		{
			return allotment;
		}
		return seed.isPlantedAsSapling() ? sapling : other;
	}

	/** How many can be planted without dipping into the buffer. */
	public int spare(Seed seed, int plantable)
	{
		return Math.max(0, plantable - bufferFor(seed));
	}
}
