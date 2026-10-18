/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.run.GuidanceTracker;
import com.layneburns.farmingruns.run.FairyRingRoute;
import com.layneburns.farmingruns.run.LegTimer;
import com.layneburns.farmingruns.run.LeprechaunNeeds;
import com.layneburns.farmingruns.run.PineappleErrand;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.run.Protection;
import com.layneburns.farmingruns.run.Reminder;
import com.layneburns.farmingruns.run.RunProgress;
import com.layneburns.farmingruns.run.RunSession;
import com.layneburns.farmingruns.run.TravelChain;
import com.layneburns.farmingruns.state.ItemStore;
import com.layneburns.farmingruns.state.LocationPrefs;
import com.layneburns.farmingruns.state.PlayerInfo;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * During a run, shows above your head the next thing to do at the current stop ({@link Reminder}) while you're
 * there, and where you're going next and how until you set off. Seen by you alone. Can be switched off, and its
 * colour changed, in the settings.
 */
@Singleton
public class ReminderOverlay extends Overlay
{
	/** How far above your head the text sits. */
	private static final int HEIGHT_ABOVE = 40;

	private final Client client;
	private final GameData data;
	private final RunSession session;
	private final GuidanceTracker guidance;
	private final LegTimer legs;
	private final FairyRingRoute fairyRings;
	private final PineappleErrand pineapples;
	private final LocationPrefs prefs;
	private final ItemStore items;
	private final PlayerInfo player;
	private final FarmingRunsConfig config;

	@Inject
	ReminderOverlay(Client client, GameData data, RunSession session, GuidanceTracker guidance, LegTimer legs,
		ItemStore items, PlayerInfo player, FarmingRunsConfig config, FairyRingRoute fairyRings,
		PineappleErrand pineapples, LocationPrefs prefs)
	{
		this.prefs = prefs;
		this.pineapples = pineapples;
		this.fairyRings = fairyRings;
		this.client = client;
		this.data = data;
		this.session = session;
		this.guidance = guidance;
		this.legs = legs;
		this.items = items;
		this.player = player;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		RunSession.ActiveRun run = session.active();
		Player me = client.getLocalPlayer();
		if (!config.overheadReminders() || me == null)
		{
			return null;
		}
		if (run == null)
		{
			int toBuy = pineapples.stillToBuy();
			if (toBuy > 0)
			{
				draw(graphics, me, "Buy " + toBuy + " pineapple" + (toBuy == 1 ? "" : "s")
					+ " · Trader Crewmember, Deepfin Point · hop worlds");
			}
			return null;
		}
		RunProgress.Stop stop = run.getProgress().currentStop();
		if (stop == null)
		{
			return null;
		}
		if (!isAt(stop, me.getWorldLocation().getRegionID()))
		{
			// On the way: where to, and the steps still to take; each teleport takes one off, the last clears it.
			TeleportChoice teleport = run.getTeleports().get(stop.getLocationId());
			List<String> learned = prefs.chain(stop.getLocationId(), teleport == null ? TravelChain.WALK : teleport.getRef());
			List<String> steps = TravelChain.remaining(TravelChain.steps(teleport, fairyRings.via(teleport), learned),
				legs.jumps());
			if (!steps.isEmpty())
			{
				draw(graphics, me, Reminder.travel(data.location(stop.getLocationId()).getName(), steps));
			}
			return null;
		}
		Set<String> composted = stop.getPatches().stream().map(a -> a.getPatch().getId())
			.filter(guidance::isComposted).collect(Collectors.toSet());
		String text = Reminder.next(stop, run.getProgress(), run.getSetup().getCompost(), composted,
			(PlantingPlan.Assignment a) -> Protection.shouldPay(a, run.getSetup(), data, player.getFacts()),
			LeprechaunNeeds.atStop(stop, run.getProgress(), run.getSetup().getCompost(), composted, data.getItems(),
				items.ledger()));
		if (text != null)
		{
			draw(graphics, me, text);
		}
		return null;
	}

	private void draw(Graphics2D graphics, Player me, String text)
	{
		graphics.setFont(FontManager.getRunescapeBoldFont());
		Point at = me.getCanvasTextLocation(graphics, text, me.getLogicalHeight() + HEIGHT_ABOVE);
		if (at != null)
		{
			OverlayUtil.renderTextLocation(graphics, at, text, config.reminderColour());
		}
	}

	private boolean isAt(RunProgress.Stop stop, int region)
	{
		Location location = data.location(stop.getLocationId());
		return location.getRegionId() == region || location.getExtraRegionIds().contains(region);
	}
}
