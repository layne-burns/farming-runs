/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.data.TeleportType;
import com.layneburns.farmingruns.run.RunProgress;
import com.layneburns.farmingruns.run.RunSession;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * On the way to the next stop, outlines its teleport's destination wherever a teleport menu lists it: "Kastori" on
 * the quetzal map, a spirit tree's list, the portal nexus, a jewellery box, or "Farming Guild" among a skills
 * necklace's options (fairy rings are left to RuneLite's Fairy Rings plugin). Interfaces are searched by name and text, so any menu that names the
 * place works. Client thread.
 */
@Singleton
public class TeleportMenuOverlay extends Overlay
{
	/** Opened at login and full of chat that may name a place: never searched. */
	private static final Set<Integer> IGNORED = Set.of(InterfaceID.CHATBOX);

	private final Client client;
	private final GameData data;
	private final RunSession session;
	private final FarmingRunsConfig config;
	/** Interfaces opened since login (and not closed), to search. */
	private final Set<Integer> open = new LinkedHashSet<>();

	@Inject
	TeleportMenuOverlay(Client client, GameData data, RunSession session, FarmingRunsConfig config)
	{
		this.client = client;
		this.data = data;
		this.session = session;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (!IGNORED.contains(event.getGroupId()))
		{
			open.add(event.getGroupId());
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		open.remove(event.getGroupId());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		String target = target();
		if (target == null)
		{
			return null;
		}
		List<Rectangle> found = new ArrayList<>();
		for (int group : open)
		{
			search(client.getWidget(group, 0), target, found, 0);
		}
		Color colour = config.reminderColour();
		graphics.setStroke(new BasicStroke(2));
		for (Rectangle bounds : found)
		{
			graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 50));
			graphics.fill(bounds);
			graphics.setColor(colour);
			graphics.draw(bounds);
		}
		return null;
	}

	/** The next stop's destination name, normalised, while you're on your way there; null otherwise. */
	@Nullable
	private String target()
	{
		RunSession.ActiveRun run = session.active();
		Player me = client.getLocalPlayer();
		RunProgress.Stop stop = run == null ? null : run.getProgress().currentStop();
		if (stop == null || me == null)
		{
			return null;
		}
		Location location = data.location(stop.getLocationId());
		int region = me.getWorldLocation().getRegionID();
		TeleportChoice teleport = run.getTeleports().get(stop.getLocationId());
		// Fairy rings are left to RuneLite's own Fairy Rings plugin.
		if (location.getRegionId() == region || location.getExtraRegionIds().contains(region) || teleport == null
			|| teleport.getDestination() == null || teleport.getMethod().getType() == TeleportType.FAIRY_RING)
		{
			return null;
		}
		return normalise(teleport.getDestination().getName());
	}

	/** Adds the bounds of every visible widget under {@code widget} whose name, text or options name the target. */
	private static void search(@Nullable Widget widget, String target, List<Rectangle> found, int depth)
	{
		if (widget == null || widget.isHidden() || depth > 12)
		{
			return;
		}
		if (names(widget, target))
		{
			Rectangle bounds = widget.getBounds();
			if (bounds != null && bounds.width > 0 && bounds.height > 0)
			{
				found.add(bounds);
			}
		}
		for (Widget[] children : new Widget[][]{widget.getStaticChildren(), widget.getDynamicChildren(),
			widget.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget child : children)
				{
					search(child, target, found, depth + 1);
				}
			}
		}
	}

	private static boolean names(Widget widget, String target)
	{
		if (contains(widget.getName(), target) || contains(widget.getText(), target))
		{
			return true;
		}
		String[] actions = widget.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				if (contains(action, target))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Whole words: "Kastori" matches "Travel to Kastori", but "Kastori" doesn't match "Kastorian". */
	private static boolean contains(@Nullable String text, String target)
	{
		return text != null && !text.isEmpty() && (" " + normalise(text) + " ").contains(" " + target + " ");
	}

	/** Lower case, tags removed, anything but letters and digits as single spaces. */
	static String normalise(String text)
	{
		return Text.removeTags(text).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}
}
