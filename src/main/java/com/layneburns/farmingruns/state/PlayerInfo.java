/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;

/**
 * Facts about the player that the panel needs. For now just the real (unboosted) Farming level; phase 3 adds
 * quests, diaries and spellbook for the unlock rules.
 */
@Singleton
public class PlayerInfo
{
	/** Value of {@link #getFarmingLevel()} before login. */
	public static final int UNKNOWN = -1;

	private final Client client;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile int farmingLevel = UNKNOWN;

	@Inject
	PlayerInfo(Client client)
	{
		this.client = client;
	}

	public int getFarmingLevel()
	{
		return farmingLevel;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.FARMING)
		{
			setFarmingLevel(event.getLevel());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			setFarmingLevel(UNKNOWN);
		}
	}

	/** Reads the level now (for when the plugin starts while logged in). Client thread. */
	public void refresh()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			setFarmingLevel(client.getRealSkillLevel(Skill.FARMING));
		}
	}

	private void setFarmingLevel(int level)
	{
		if (level != farmingLevel)
		{
			farmingLevel = level;
			listeners.forEach(Runnable::run);
		}
	}
}
