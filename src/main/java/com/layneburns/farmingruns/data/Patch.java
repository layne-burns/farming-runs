/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/** One farming patch: what grows there, which varbit reports its state, and who protects it. */
@Value
public class Patch
{
	/** Value of {@link #gardenerNpcId} for patches without a gardener (herbs, flowers...). */
	public static final int NO_GARDENER = -1;

	/** E.g. "catherby.herb" or "farming_guild.allotment_north". */
	String id;
	String locationId;
	/** RuneLite's patch name, e.g. "North"; often empty. */
	String name;
	PatchImplementation kind;
	/** The region RuneLite files this patch under; with the varbit, it keys Time Tracking's saved state. */
	int regionId;
	int varbitId;
	int gardenerNpcId;
	/** The patch's tile from the wiki, or null if the wiki has none. */
	@Nullable
	WorldPoint tile;
	/** Unlock rule for just this patch (e.g. a Farming Guild tier), or null to follow its location. */
	@Nullable
	String unlockRuleId;

	public boolean hasGardener()
	{
		return gardenerNpcId != NO_GARDENER;
	}
}
