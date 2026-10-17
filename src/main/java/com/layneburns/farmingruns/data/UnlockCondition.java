/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

	/** RuneLite's diary varbit names, e.g. MORYTANIA_DIARY_ELITE_COMPLETE. */
	private static final Pattern DIARY = Pattern.compile("([A-Z_]+)_DIARY_(EASY|MEDIUM|HARD|ELITE)_COMPLETE");

	/** Readable form for the panel, e.g. "Quest: Bone Voyage", "Farming 65" or "Elite Morytania diary". */
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
		Matcher diary = varbitName == null ? null : DIARY.matcher(varbitName);
		if (diary != null && diary.matches())
		{
			return capitalise(diary.group(2)) + " " + capitalise(diary.group(1).replace('_', ' ')) + " diary";
		}
		return "Varbit " + varbitName;
	}

	/** "MORYTANIA" → "Morytania", "WESTERN PROVINCES" → "Western provinces". */
	private static String capitalise(String words)
	{
		String lower = words.toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
