/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import javax.annotation.Nullable;
import lombok.Value;

/** What's in a patch now, as far as Time Tracking last saw it, and when it should be ready. */
@Value
public class PatchStatus
{
	public enum Readiness
	{
		/** Grown: harvest it (or check its health). */
		READY,
		/** Nothing growing (or only weeds): plant something. */
		EMPTY,
		/** Still growing; see {@link #readyAtSeconds}. */
		GROWING,
		DISEASED,
		DEAD,
		/** Time Tracking hasn't seen the patch (or isn't on). */
		UNKNOWN;

		/** Worth visiting on a run: there's something to do there. */
		public boolean needsVisit()
		{
			return this != GROWING;
		}
	}

	/** Never seen: nothing known. */
	public static final PatchStatus UNKNOWN = new PatchStatus(Readiness.UNKNOWN, null, 0, 0, 0);

	Readiness readiness;
	/** What's growing, e.g. "Ranarr"; null when empty or unknown. */
	@Nullable
	String crop;
	/** When it should be ready (epoch seconds); 0 when not growing. */
	long readyAtSeconds;
	int stage;
	int stages;

	/** E.g. "Ranarr: ready", "Ranarr: ready in 1 h 20 min", "Empty", "Diseased (Ranarr)", "Not seen yet". */
	public String describe(long nowSeconds)
	{
		String what = crop == null ? "" : crop + ": ";
		switch (readiness)
		{
			case READY:
				return what + "ready";
			case EMPTY:
				return "Empty";
			case GROWING:
				long left = readyAtSeconds - nowSeconds;
				return left <= 0 ? what + "ready (probably)" : what + "ready in " + duration(left);
			case DISEASED:
				return what + "diseased";
			case DEAD:
				return what + "dead";
			default:
				return "Not seen yet";
		}
	}

	/** Short, for a table column: "ready", "empty", "dead", "diseased", "1 h 20 m", "not seen". */
	public String shortState(long nowSeconds)
	{
		switch (readiness)
		{
			case READY:
				return "ready";
			case EMPTY:
				return "empty";
			case GROWING:
				long left = readyAtSeconds - nowSeconds;
				return left <= 0 ? "ready?" : shortDuration(left);
			case DISEASED:
				return "diseased";
			case DEAD:
				return "dead";
			default:
				return "not seen";
		}
	}

	/** "1 h 20 m", "45 m", "<1 m". */
	public static String shortDuration(long seconds)
	{
		long minutes = (seconds + 59) / 60;
		if (minutes < 1)
		{
			return "<1 m";
		}
		long hours = minutes / 60;
		long mins = minutes % 60;
		return hours == 0 ? mins + " m" : mins == 0 ? hours + " h" : hours + " h " + mins + " m";
	}

	/** "1 h 20 min", "45 min", "under a minute". */
	public static String duration(long seconds)
	{
		long minutes = (seconds + 59) / 60;
		if (minutes < 1)
		{
			return "under a minute";
		}
		long hours = minutes / 60;
		long mins = minutes % 60;
		if (hours == 0)
		{
			return mins + " min";
		}
		return mins == 0 ? hours + " h" : hours + " h " + mins + " min";
	}
}
