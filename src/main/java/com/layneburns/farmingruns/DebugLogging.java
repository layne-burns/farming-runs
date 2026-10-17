/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

/**
 * The "Debug logging" setting: turns the plugin's detailed log lines ([farmrun], [sp], [seedlog]...) on or off in the
 * client log, without touching any other plugin's logging.
 */
final class DebugLogging
{
	private static final String PACKAGE = "com.layneburns.farmingruns";

	private DebugLogging()
	{
	}

	static void apply(boolean on)
	{
		org.slf4j.Logger logger = LoggerFactory.getLogger(PACKAGE);
		if (logger instanceof Logger)
		{
			// Null goes back to the client's own level.
			((Logger) logger).setLevel(on ? Level.DEBUG : null);
		}
	}
}
