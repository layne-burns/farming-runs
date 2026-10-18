/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.vendor.timetracking.PatchState;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * What's in each patch and when it'll be ready, from what RuneLite's Time Tracking plugin saved (it records each
 * patch's varbit whenever you're near it). This is the only class that knows Time Tracking's storage format:
 * group "timetracking", key "regionId.varbit", value "varbitValue:epochSeconds", plus its farming tick offset.
 * Listeners are told whenever Time Tracking saves something.
 */
@Singleton
public class PatchStates
{
	private static final String GROUP = "timetracking";
	private static final String TICK_OFFSET = "farmTickOffset";
	private static final String TICK_OFFSET_PRECISION = "farmTickOffsetPrecision";
	private static final String AUTOWEED = "autoweed";
	/** Time Tracking's Autoweed.ON, as it saves it (the enum's position). */
	private static final String AUTOWEED_ON = "2";
	/** RuneLite saves whether a plugin is on under group "runelite", key = plugin class name in lower case. */
	private static final String PLUGIN_ENABLED_KEY = "timetrackingplugin";

	private final ConfigManager configManager;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	@Inject
	PatchStates(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public PatchStatus status(Patch patch, long nowSeconds)
	{
		String stored = configManager.getRSProfileConfiguration(GROUP, patch.getRegionId() + "." + patch.getVarbitId());
		Integer precision = configManager.getRSProfileConfiguration(GROUP, TICK_OFFSET_PRECISION, Integer.class);
		Integer offset = configManager.getRSProfileConfiguration(GROUP, TICK_OFFSET, Integer.class);
		boolean autoweed = isAutoweedOn();
		return ReadyPredictor.predict(stored, patch.getKind(), precision, offset, autoweed, nowSeconds);
	}

	/** The patch's last seen state as the game reported it (e.g. a compost bin's fill), or null if never seen. */
	@Nullable
	public PatchState lastSeen(Patch patch)
	{
		String stored = configManager.getRSProfileConfiguration(GROUP, patch.getRegionId() + "." + patch.getVarbitId());
		if (stored == null)
		{
			return null;
		}
		try
		{
			return patch.getKind().forVarbitValue(Integer.parseInt(stored.split(":")[0]));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** True when Time Tracking has seen your autoweed setting switched on: weeds don't grow back. */
	public boolean isAutoweedOn()
	{
		return AUTOWEED_ON.equals(configManager.getRSProfileConfiguration(GROUP, AUTOWEED));
	}

	/** False when Time Tracking has been switched off (then patch states stop updating). */
	public boolean isTimeTrackingOn()
	{
		return !"false".equals(configManager.getConfiguration("runelite", PLUGIN_ENABLED_KEY));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GROUP.equals(event.getGroup())
			|| ("runelite".equals(event.getGroup()) && PLUGIN_ENABLED_KEY.equals(event.getKey())))
		{
			listeners.forEach(Runnable::run);
		}
	}
}
