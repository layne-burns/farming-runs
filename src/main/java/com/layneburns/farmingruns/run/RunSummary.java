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
	/** Produce that landed in your inventory, by item name. */
	Map<String, Integer> harvested;
	/** The most herbs picked from one patch this run (counted from the game's pick messages), and which herb. */
	int mostHerbs;
	String mostHerbsName;
	/** New lifetime bests set by this run, e.g. "Most herbs from one patch: 14 Ranarr (was 11)". */
	List<String> records;
}
