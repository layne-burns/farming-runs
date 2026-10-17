/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * A stop on a run: one or more patches close together (Catherby's allotments, flower and herb), the tile
 * Shortest Path is sent to, and the hand-chosen settings (default teleport, unlock rule, note).
 */
@Value
public class Location
{
	String id;
	String name;
	int regionId;
	List<Integer> extraRegionIds;
	/** Where Shortest Path is pointed for this stop. */
	WorldPoint target;
	List<Patch> patches;
	/** Teleports in order of preference: the first one you can use is used. */
	List<String> defaultTeleportIds;
	/** The fairy ring code here, e.g. "CIR", when a fairy ring is one of its teleports; else null. */
	@Nullable
	String fairyRingCode;
	@Nullable
	String unlockRuleId;
	@Nullable
	String note;
	/** The wiki's suggested teleports, best first, shown as hints in the panel. */
	List<String> wikiTeleports;
}
