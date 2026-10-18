/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.pathing;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

/**
 * The only class that talks to the Shortest Path plugin, over its plugin messages: "path" with targets draws the
 * route to the nearest of them from wherever you are, "clear" removes it. Each path overrides some of Shortest Path's
 * settings for that leg: a longer search cutoff, {@link #CARRIED_ONLY} (only the teleport items you carry, no bank
 * trips), and house portals when the stop is reached by one. Sends only when the
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

	/** Shortest Path's setting for how long a search may go without progress, in ticks (its default is 5). */
	private static final String CUTOFF_KEY = "calculationCutoff";
	private static final int MIN_CUTOFF_TICKS = 15;

	private final EventBus eventBus;
	private final ConfigManager configManager;
	private final PluginManager pluginManager;
	/** What was last sent: a target, or null for "clear". Starts as "clear" so we never wipe a path you set yourself. */
	@Nullable
	private Set<WorldPoint> sent;
	private Map<String, Object> sentConfig = Map.of();
	private boolean sentAnything;

	@Inject
	ShortestPathClient(EventBus eventBus, PluginManager pluginManager, ConfigManager configManager)
	{
		this.configManager = configManager;
		this.eventBus = eventBus;
		this.pluginManager = pluginManager;
	}

	/**
	 * Draws the path to the nearest of {@code targets}, unless that's already the path.
	 *
	 * @param carriedOnly  use only the teleport items you carry and no bank trips, instead of your own settings
	 * @param housePortals let it use your house's portals (the stop is reached by one)
	 */
	public void target(Set<WorldPoint> targets, boolean carriedOnly, boolean housePortals)
	{
		Map<String, Object> config = new HashMap<>();
		// Long routes (out of Weiss, say) need longer than Shortest Path's default cutoff to be found.
		config.put(CUTOFF_KEY, Math.max(MIN_CUTOFF_TICKS, yourCutoff()));
		if (carriedOnly)
		{
			config.putAll(CARRIED_ONLY);
		}
		if (housePortals)
		{
			// Off by default in Shortest Path, so a stop reached by your house portal would be unreachable.
			config.put("useTeleportationPortalsPoh", true);
		}
		if (targets.isEmpty() || (targets.equals(sent) && config.equals(sentConfig) && sentAnything))
		{
			return;
		}
		if (config.isEmpty() && !sentConfig.isEmpty())
		{
			// Shortest Path keeps the last settings override until a "clear".
			eventBus.post(new PluginMessage(NAMESPACE, "clear"));
		}
		// One point as itself; several as a set, where Shortest Path goes to the nearest.
		Object target = targets.size() == 1 ? targets.iterator().next() : Set.copyOf(targets);
		eventBus.post(new PluginMessage(NAMESPACE, "path", config.isEmpty() ? Map.of("target", target)
			: Map.of("target", target, "config", Map.copyOf(config))));
		log.debug("[sp] path to {} {}", targets, config);
		sent = Set.copyOf(targets);
		sentConfig = Map.copyOf(config);
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

	/** The last targets sent, for debug info; null when cleared. */
	@Nullable
	public Set<WorldPoint> lastTarget()
	{
		return sent;
	}

	/** Your own Shortest Path cutoff, or 0 if it can't be read. */
	private int yourCutoff()
	{
		Integer cutoff = configManager.getConfiguration(NAMESPACE, CUTOFF_KEY, Integer.class);
		return cutoff == null ? 0 : cutoff;
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
