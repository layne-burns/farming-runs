/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.layneburns.farmingruns.data.UnlockCondition;
import com.layneburns.farmingruns.data.UnlockRule;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.QuestState;

/**
 * Judges an unlock rule against what's known about the player. Pure: unit tested with made-up facts.
 */
public final class UnlockEvaluator
{
	private UnlockEvaluator()
	{
	}

	/** The conditions of the rule you don't meet yet, readable ("Quest: Bone Voyage"); empty if it passes. */
	public static List<String> missing(UnlockRule rule, PlayerFacts facts)
	{
		List<String> missing = new ArrayList<>();
		for (UnlockCondition condition : rule.getConditions())
		{
			if (!passes(condition, facts))
			{
				missing.add(condition.describe());
			}
		}
		return missing;
	}

	static boolean passes(UnlockCondition condition, PlayerFacts facts)
	{
		if (condition.getQuest() != null)
		{
			QuestState state = facts.quest(condition.getQuest());
			return state == QuestState.FINISHED || (condition.isStarted() && state == QuestState.IN_PROGRESS);
		}
		if (condition.getSkill() != null)
		{
			return facts.level(condition.getSkill()) >= condition.getLevel();
		}
		return facts.varbit(condition.getVarbitId()) >= 1;
	}
}
