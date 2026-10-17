/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import org.junit.BeforeClass;
import org.junit.Test;

/** The real rules from unlocks.json, judged against made-up players. */
public class UnlockEvaluatorTest
{
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	@Test
	public void finishedQuestPasses()
	{
		PlayerFacts facts = facts(Map.of(Quest.BONE_VOYAGE, QuestState.FINISHED), Map.of(), 99);
		assertTrue(UnlockEvaluator.missing(data.rule("bone_voyage"), facts).isEmpty());
	}

	@Test
	public void unfinishedQuestIsNamed()
	{
		PlayerFacts facts = facts(Map.of(Quest.SONG_OF_THE_ELVES, QuestState.IN_PROGRESS), Map.of(), 99);
		assertEquals(List.of("Quest: Song of the Elves"), UnlockEvaluator.missing(data.rule("song_of_the_elves"), facts));
	}

	@Test
	public void startedIsEnoughWhenTheRuleSaysSo()
	{
		PlayerFacts facts = facts(Map.of(Quest.MOURNINGS_END_PART_I, QuestState.IN_PROGRESS), Map.of(), 99);
		assertTrue(UnlockEvaluator.missing(data.rule("mournings_end_started"), facts).isEmpty());
	}

	@Test
	public void diaryVarbitAndSkillLevel()
	{
		PlayerFacts noDiary = facts(Map.of(), Map.of(), 70);
		assertFalse(UnlockEvaluator.missing(data.rule("morytania_elite"), noDiary).isEmpty());
		PlayerFacts diary = facts(Map.of(), Map.of(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE, 1), 70);
		assertTrue(UnlockEvaluator.missing(data.rule("morytania_elite"), diary).isEmpty());

		assertTrue(UnlockEvaluator.missing(data.rule("farming_guild_intermediate"), noDiary).isEmpty());
		assertEquals(List.of("Farming 85"), UnlockEvaluator.missing(data.rule("farming_guild_advanced"), noDiary));
	}

	@Test
	public void overridesWinButTheRulesAreKept()
	{
		LocationStatus forced = LocationStatus.of(true, List.of("Quest: Song of the Elves"), true);
		assertEquals(LocationStatus.Access.UNLOCKED, forced.getAccess());
		assertEquals(LocationStatus.Access.LOCKED, forced.getByRules());
		assertTrue(forced.isOverridden());

		LocationStatus loggedOut = LocationStatus.of(false, List.of(), null);
		assertEquals(LocationStatus.Access.UNKNOWN, loggedOut.getAccess());
		assertTrue("unknown counts as usable, so nothing is hidden before login", loggedOut.isUsable());
	}

	private static PlayerFacts facts(Map<Quest, QuestState> quests, Map<Integer, Integer> varbits, int farming)
	{
		return new PlayerFacts(true, quests, varbits, Map.of(Skill.FARMING, farming), 0);
	}
}
