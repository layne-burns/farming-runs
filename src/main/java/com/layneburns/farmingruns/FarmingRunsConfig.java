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

/**
 * Settings shown in RuneLite's configuration panel. Everyday choices (seeds, skips, payment, presets) live in
 * the side panel instead; this holds the rarely changed ones. Later phases add the seed buffers and reminder look.
 */
@ConfigGroup(FarmingRunsConfig.GROUP)
public interface FarmingRunsConfig extends Config
{
	String GROUP = "farmingruns";

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
