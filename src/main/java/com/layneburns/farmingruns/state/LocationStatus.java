/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

/** Whether a location (or patch) can be used, and why: what the rules say, and your override if you set one. */
@Value
public class LocationStatus
{
	public enum Access
	{
		UNLOCKED,
		LOCKED,
		/** Not logged in yet, so the rules can't be judged. */
		UNKNOWN
	}

	/** What applies: your override if you set one, else what the rules say. */
	Access access;
	/** What the rules say, whatever your override. */
	Access byRules;
	/** Requirements not met yet, e.g. "Quest: Song of the Elves"; empty if none. */
	List<String> missing;
	boolean overridden;

	/** Pure: combines the rule's missing requirements, whether facts are known, and your override. */
	public static LocationStatus of(boolean factsKnown, List<String> missing, @Nullable Boolean override)
	{
		Access byRules = !factsKnown ? Access.UNKNOWN : missing.isEmpty() ? Access.UNLOCKED : Access.LOCKED;
		if (override != null)
		{
			return new LocationStatus(override ? Access.UNLOCKED : Access.LOCKED, byRules, List.copyOf(missing), true);
		}
		return new LocationStatus(byRules, byRules, List.copyOf(missing), false);
	}

	/** Counts as usable: unlocked, or not known yet (so nothing is hidden before login). */
	public boolean isUsable()
	{
		return access != Access.LOCKED;
	}
}
