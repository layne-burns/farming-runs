/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import java.util.List;
import java.util.Map;
import lombok.Value;

/** How one run went, for the Live tab's summary and the lifetime stats. */
@Value
public class RunSummary
{
	/** True when every stop was done or skipped; false when stopped part way. */
	boolean completed;
	long millis;
	int xpGained;
	int stopsDone;
	int stops;
	int patchesPlanted;
	/** Produce gathered, by item name. */
	Map<String, Integer> harvested;
	/** The most of each produce gathered at a single stop, by item name. */
	Map<String, Integer> bestAtOneStop;
	/** New lifetime bests set by this run, e.g. "Grimy ranarr weed: 14 at one stop (was 11)". */
	List<String> records;
}
