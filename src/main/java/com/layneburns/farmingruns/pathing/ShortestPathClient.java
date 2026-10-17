/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.pathing;

import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

/**
 * The only class that talks to the Shortest Path plugin, over its plugin messages: "path" with a target draws the
 * route there from wherever you are, "clear" removes it. A path can override Shortest Path's settings for that leg:
 * {@link #CARRIED_ONLY} keeps it to the teleport items you carry, with no bank trips. Sends only when the
 * target changes, and again after a logout or world hop, when Shortest Path may have dropped it. Nothing comes back,
 * so it can't tell whether Shortest Path is listening; {@link #isAvailable()} checks it's installed and on.
 */
@Slf4j
@Singleton
public class ShortestPathClient
{
	private static final String NAMESPACE = "shortestpath";
	private static final String PLUGIN_NAME = "Shortest Path";
	/** Shortest Path settings for a leg that uses only what you carry: its option labels, as it takes them. */
	private static final Map<String, Object> CARRIED_ONLY = Map.of(
		"useTeleportationItems", "Inventory",
		"includeBankPath", false);

	private final EventBus eventBus;
	private final PluginManager pluginManager;
	/** What was last sent: a target, or null for "clear". Starts as "clear" so we never wipe a path you set yourself. */
	@Nullable
	private WorldPoint sent;
	private boolean sentCarriedOnly;
	private boolean sentAnything;

	@Inject
	ShortestPathClient(EventBus eventBus, PluginManager pluginManager)
	{
		this.eventBus = eventBus;
		this.pluginManager = pluginManager;
	}

	/**
	 * Draws the path to {@code target}, unless that's already the path.
	 *
	 * @param carriedOnly use only the teleport items you carry and no bank trips, instead of your own settings
	 */
	public void target(WorldPoint target, boolean carriedOnly)
	{
		if (target.equals(sent) && carriedOnly == sentCarriedOnly && sentAnything)
		{
			return;
		}
		eventBus.post(new PluginMessage(NAMESPACE, "path", carriedOnly
			? Map.of("target", target, "config", CARRIED_ONLY) : Map.of("target", target)));
		log.debug("[sp] path to {}{}", target, carriedOnly ? " (carried items only)" : "");
		sent = target;
		sentCarriedOnly = carriedOnly;
		sentAnything = true;
	}

	/** Removes our path (only if we drew one). */
	public void clear()
	{
		if (sent == null)
		{
			return;
		}
		eventBus.post(new PluginMessage(NAMESPACE, "clear"));
		log.debug("[sp] clear");
		sent = null;
	}

	/** Forgets what was sent, so the next {@link #target} sends again (after a logout or hop). */
	public void resend()
	{
		sentAnything = false;
	}

	/** The last target sent, for debug info; null when cleared. */
	@Nullable
	public WorldPoint lastTarget()
	{
		return sent;
	}

	/** True when the Shortest Path plugin is installed and switched on. */
	public boolean isAvailable()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
			if (descriptor != null && PLUGIN_NAME.equals(descriptor.name()))
			{
				return pluginManager.isPluginEnabled(plugin);
			}
		}
		return false;
	}
}
