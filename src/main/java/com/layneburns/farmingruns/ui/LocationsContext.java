/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.state.LocationStatus;
import com.layneburns.farmingruns.state.PatchStatus;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

/**
 * Your situation, as the Locations view needs it: unlock answers and your teleport choices. Passed in as functions
 * so {@link LocationsViewBuilder} stays pure (tests give simple lambdas; the tab gives the real services).
 */
@Value
@Builder
public class LocationsContext
{
	Function<Location, LocationStatus> statusOf;
	/** A patch's own rule status (Farming Guild tiers), ignoring its location. */
	Function<Patch, LocationStatus> patchRuleOf;
	/** The location's teleports in order of preference. */
	Function<Location, List<String>> teleportsOf;
	/** True when you've changed the location's teleport order from the default. */
	Predicate<Location> customTeleports;
	/** Your spellbook ("Normal", "Lunar"...), or null if not known. */
	@Nullable
	String spellbook;
	/** What's in a patch and when it'll be ready (from Time Tracking). */
	Function<Patch, PatchStatus> patchStateOf;
	/** "Now", in epoch seconds, for ready-in times. */
	long nowSeconds;
	boolean timeTrackingOn;
}
