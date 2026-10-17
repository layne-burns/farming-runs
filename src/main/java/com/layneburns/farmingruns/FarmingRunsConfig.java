/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Settings shown in RuneLite's configuration panel. Everyday choices (seeds, skips, payment, presets) live in
 * the side panel instead; this holds the rarely changed ones.
 */
@ConfigGroup(FarmingRunsConfig.GROUP)
public interface FarmingRunsConfig extends Config
{
	String GROUP = "farmingruns";

	@ConfigSection(
		name = "Seed buffers",
		description = "Seeds kept back for farming contracts and retries: never planted automatically, not counted as spare",
		position = 0
	)
	String buffers = "buffers";

	@ConfigItem(
		keyName = "allotmentBuffer",
		name = "Allotment seeds",
		description = "Allotment seeds of each type to keep back",
		position = 1,
		section = buffers
	)
	@Range(max = 1000)
	default int allotmentBuffer()
	{
		return 9;
	}

	@ConfigItem(
		keyName = "saplingBuffer",
		name = "Saplings",
		description = "Saplings of each tree type to keep back",
		position = 2,
		section = buffers
	)
	@Range(max = 1000)
	default int saplingBuffer()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "otherBuffer",
		name = "Other seeds",
		description = "Seeds of each other type (herbs, flowers, hops...) to keep back",
		position = 3,
		section = buffers
	)
	@Range(max = 1000)
	default int otherBuffer()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "debugLogging",
		name = "Debug logging",
		description = "Write detailed [farmrun] lines to the client log, for tracking down problems",
		position = 100
	)
	default boolean debugLogging()
	{
		return false;
	}
}
