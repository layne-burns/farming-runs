/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import java.util.Map;
import lombok.Value;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * What the unlock rules need to know about the player, read from the game in one go (see {@link PlayerInfo}).
 * Immutable, so it can be read from any thread and handed to pure code like {@link UnlockEvaluator}.
 */
@Value
public class PlayerFacts
{
	/** Before login: nothing known, so rules can't be judged. */
	public static final PlayerFacts UNKNOWN = new PlayerFacts(false, Map.of(), Map.of(), Map.of(), -1);

	/** False until read after a login. */
	boolean known;
	Map<Quest, QuestState> quests;
	/** Varbit id to value, for the varbits the rules use (diary tiers). */
	Map<Integer, Integer> varbits;
	/** Real (unboosted) skill levels. */
	Map<Skill, Integer> levels;
	/** The SPELLBOOK varbit: 0 standard, 1 Ancient, 2 Lunar, 3 Arceuus; -1 unknown. */
	int spellbook;

	public QuestState quest(Quest quest)
	{
		return quests.getOrDefault(quest, QuestState.NOT_STARTED);
	}

	public int varbit(int varbitId)
	{
		return varbits.getOrDefault(varbitId, 0);
	}

	public int level(Skill skill)
	{
		return levels.getOrDefault(skill, 1);
	}

	/** The spellbook's name as the wiki writes it ("Normal", "Ancient", "Lunar", "Arceuus"), or null if unknown. */
	public String spellbookName()
	{
		switch (spellbook)
		{
			case 0:
				return "Normal";
			case 1:
				return "Ancient";
			case 2:
				return "Lunar";
			case 3:
				return "Arceuus";
			default:
				return null;
		}
	}
}
