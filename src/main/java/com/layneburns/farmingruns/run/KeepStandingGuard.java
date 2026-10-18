/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.Patch;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * During a run, stops a misclick from destroying a plant you're keeping standing (a harvest run): "Chop down",
 * "Clear" or "Dig up" on its patch, and "Pay" to its gardener (which clears a grown tree for 200 coins). The click is
 * swallowed with a note in the chat; shift-click goes through anyway. Can be switched off in the settings. Client
 * thread.
 */
@Slf4j
@Singleton
public class KeepStandingGuard
{
	/** Patch options that would remove the plant. */
	private static final Set<String> CLEARING = Set.of("chop down", "chop-down", "clear", "dig up", "dig-up", "uproot");

	private final Client client;
	private final GameData data;
	private final RunSession session;
	private final FarmingRunsConfig config;

	@Inject
	KeepStandingGuard(Client client, GameData data, RunSession session, FarmingRunsConfig config)
	{
		this.client = client;
		this.data = data;
		this.session = session;
		this.config = config;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		RunSession.ActiveRun run = session.active();
		if (run == null || !config.blockClearingKept() || client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}
		String option = Text.removeTags(event.getMenuOption()).toLowerCase(Locale.ROOT);
		PlantingPlan.Assignment kept = null;
		if (CLEARING.contains(option) && isObjectClick(event.getMenuAction()))
		{
			ObjectComposition object = client.getObjectDefinition(event.getId());
			kept = object == null ? null : keptHere(run, a -> a.getPatch().getVarbitId() == object.getVarbitId());
		}
		else if (option.startsWith("pay") && isNpcClick(event.getMenuAction()))
		{
			NPC npc = event.getMenuEntry().getNpc();
			kept = npc == null ? null : keptHere(run, a -> a.getPatch().getGardenerNpcId() == npc.getId());
		}
		if (kept == null)
		{
			return;
		}
		event.consume();
		String what = plantName(kept);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Farming Runs: keeping your " + what
			+ " standing (harvest run). Shift-click to " + option + " anyway.", null);
		log.debug("[farmrun] blocked {} on {}", option, kept.getPatch().getId());
	}

	/** A patch you're keeping standing, in the area you're in, that the test picks out; null if none. */
	@Nullable
	private PlantingPlan.Assignment keptHere(RunSession.ActiveRun run,
		Predicate<PlantingPlan.Assignment> test)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		int region = player.getWorldLocation().getRegionID();
		for (RunProgress.Stop stop : run.getProgress().getStops())
		{
			Location location = data.location(stop.getLocationId());
			if (location.getRegionId() != region && !location.getExtraRegionIds().contains(region))
			{
				continue;
			}
			for (PlantingPlan.Assignment a : stop.getPatches())
			{
				if (a.isKeptStanding() && test.test(a))
				{
					return a;
				}
			}
		}
		return null;
	}

	private static boolean isObjectClick(MenuAction action)
	{
		return action == MenuAction.GAME_OBJECT_FIRST_OPTION || action == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| action == MenuAction.GAME_OBJECT_THIRD_OPTION || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| action == MenuAction.GAME_OBJECT_FIFTH_OPTION;
	}

	private static boolean isNpcClick(MenuAction action)
	{
		return action == MenuAction.NPC_FIRST_OPTION || action == MenuAction.NPC_SECOND_OPTION
			|| action == MenuAction.NPC_THIRD_OPTION || action == MenuAction.NPC_FOURTH_OPTION
			|| action == MenuAction.NPC_FIFTH_OPTION;
	}

	/** "papaya tree", "redberry bush", "cactus", or the patch kind when what's growing isn't known. */
	private static String plantName(PlantingPlan.Assignment kept)
	{
		Patch patch = kept.getPatch();
		String kind = patch.getKind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
		if (kept.getStanding() == null)
		{
			return kind;
		}
		String name = kept.getStanding().getName().replaceFirst(" seed$", "").toLowerCase(Locale.ROOT);
		return kind.equals("bush") ? name + " bush" : name;
	}
}
