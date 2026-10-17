/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import lombok.Value;

/**
 * How many of each seed to keep back (for farming contracts and retries): never planted by the auto-picker
 * and not counted as spare. For trees it applies to saplings, which are what gets planted.
 */
@Value
public class BufferSettings
{
	int allotment;
	int sapling;
	int other;

	/** The current settings from the plugin's configuration. */
	public static BufferSettings from(FarmingRunsConfig config)
	{
		return new BufferSettings(config.allotmentBuffer(), config.saplingBuffer(), config.otherBuffer());
	}

	public int bufferFor(Seed seed)
	{
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
