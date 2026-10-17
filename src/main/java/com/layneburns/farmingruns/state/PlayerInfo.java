/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Teleport;
import com.layneburns.farmingruns.data.UnlockCondition;
import com.layneburns.farmingruns.data.UnlockRule;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Reads what the unlock rules need about the player (quests, diary varbits, skill levels, spellbook) into a
 * {@link PlayerFacts}. Read once on the first game tick after login, again when a skill levels up, and on request
 * (the Locations tab's Refresh). Quest states come from a game script, so reading is done on the client thread.
 */
@Slf4j
@Singleton
public class PlayerInfo
{
	/** Value of {@link #getFarmingLevel()} before login. */
	public static final int UNKNOWN = -1;

	private final Client client;
	private final Set<Quest> quests = new HashSet<>();
	private final Set<Integer> varbits = new HashSet<>();
	private final Set<Skill> skills = new HashSet<>();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile PlayerFacts facts = PlayerFacts.UNKNOWN;
	/** Set at login; the facts are read on the next tick, once the game has sent them. */
	private boolean readOnNextTick;

	@Inject
	PlayerInfo(Client client, GameData data)
	{
		this.client = client;
		// Only what some rule asks about is read, plus Farming for the seed filters, plus the varbits that make a
		// teleport's items unnecessary (the elite Lumbridge diary for fairy rings).
		skills.add(Skill.FARMING);
		data.teleports().stream().filter(t -> t.getWaivedByVarbitId() != Teleport.NOT_WAIVED)
			.forEach(t -> varbits.add(t.getWaivedByVarbitId()));
		for (UnlockRule rule : data.rules())
		{
			for (UnlockCondition condition : rule.getConditions())
			{
				if (condition.getQuest() != null)
				{
					quests.add(condition.getQuest());
				}
				if (condition.getSkill() != null)
				{
					skills.add(condition.getSkill());
				}
				if (condition.getVarbitId() != UnlockCondition.NO_VARBIT)
				{
					varbits.add(condition.getVarbitId());
				}
			}
		}
	}

	public PlayerFacts getFacts()
	{
		return facts;
	}

	public int getFarmingLevel()
	{
		return facts.isKnown() ? facts.level(Skill.FARMING) : UNKNOWN;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			readOnNextTick = true;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			setFacts(PlayerFacts.UNKNOWN);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (readOnNextTick)
		{
			readOnNextTick = false;
			refresh();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// Switching spellbook, or finishing a diary tier mid-session.
		if (facts.isKnown() && (event.getVarbitId() == VarbitID.SPELLBOOK || varbits.contains(event.getVarbitId())))
		{
			refresh();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (facts.isKnown() && skills.contains(event.getSkill()) && event.getLevel() != facts.level(event.getSkill()))
		{
			refresh();
		}
	}

	/** Reads everything now. Client thread only; does nothing when not logged in. */
	public void refresh()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Map<Quest, QuestState> questStates = new EnumMap<>(Quest.class);
		for (Quest quest : quests)
		{
			questStates.put(quest, quest.getState(client));
		}
		Map<Integer, Integer> varbitValues = new HashMap<>();
		for (int varbit : varbits)
		{
			varbitValues.put(varbit, client.getVarbitValue(varbit));
		}
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : skills)
		{
			levels.put(skill, client.getRealSkillLevel(skill));
		}
		PlayerFacts read = new PlayerFacts(true, Map.copyOf(questStates), Map.copyOf(varbitValues), Map.copyOf(levels),
			client.getVarbitValue(VarbitID.SPELLBOOK));
		log.debug("[unlock] read {} quests, {} varbits, Farming {}, spellbook {}", questStates.size(),
			varbitValues.size(), read.level(Skill.FARMING), read.spellbookName());
		setFacts(read);
	}

	private void setFacts(PlayerFacts read)
	{
		if (!read.equals(facts))
		{
			facts = read;
			listeners.forEach(Runnable::run);
		}
	}
}
