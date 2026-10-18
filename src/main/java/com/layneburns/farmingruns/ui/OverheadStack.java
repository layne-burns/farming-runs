/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.util.HashMap;
import java.util.Map;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Asks the Overhead Stack plugin where this frame's line over your head goes, so it sits above other plugins' lines
 * instead of on top of them. It answers by writing "offset" into the message before the post returns; without it
 * installed nothing answers and the line stays where it always was.
 */
final class OverheadStack
{
	private static final String NAMESPACE = "overheadstack";

	private OverheadStack()
	{
	}

	/** How many pixels to raise a line of the given height. Call once a frame, and only when drawing it. */
	static int raise(EventBus eventBus, int height)
	{
		Map<String, Object> claim = new HashMap<>();
		claim.put("height", height);
		eventBus.post(new PluginMessage(NAMESPACE, "claim", claim));
		Object offset = claim.get("offset");
		return offset instanceof Integer ? (Integer) offset : 0;
	}
}
