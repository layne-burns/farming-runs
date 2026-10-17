/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * One requirement in an unlock rule: a quest (finished, or started if {@link #started}), a varbit at 1 or
 * more (a diary tier), or a real skill level. Exactly one of quest, varbit or skill is set.
 */
@Value
public class UnlockCondition
{
	public static final int NO_VARBIT = -1;

	@Nullable
	Quest quest;
	boolean started;
	int varbitId;
	/** The varbit's RuneLite name, for messages. */
	@Nullable
	String varbitName;
	@Nullable
	Skill skill;
	int level;

	/** Readable form for the panel, e.g. "Quest: Bone Voyage" or "Farming 65". */
	public String describe()
	{
		if (quest != null)
		{
			return "Quest: " + quest.getName() + (started ? " (started)" : "");
		}
		if (skill != null)
		{
			return skill.getName() + " " + level;
		}
		return "Varbit " + varbitName;
	}
}
