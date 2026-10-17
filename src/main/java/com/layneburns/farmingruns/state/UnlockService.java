/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Which locations and patches you can use (SPEC §2.1): each location's unlock rule judged against what's known
 * about you ({@link PlayerInfo}), then your override from {@link LocationPrefs}. A patch with its own rule (a
 * Farming Guild tier) also needs that rule. Listeners are told when either input changes.
 */
@Singleton
public class UnlockService
{
	private final GameData data;
	private final PlayerInfo player;
	private final LocationPrefs prefs;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	@Inject
	UnlockService(GameData data, PlayerInfo player, LocationPrefs prefs)
	{
		this.data = data;
		this.player = player;
		this.prefs = prefs;
		player.addListener(this::changed);
		prefs.addListener(this::changed);
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public LocationStatus status(Location location)
	{
		PlayerFacts facts = player.getFacts();
		List<String> missing = location.getUnlockRuleId() == null ? List.of()
			: UnlockEvaluator.missing(data.rule(location.getUnlockRuleId()), facts);
		return LocationStatus.of(facts.isKnown(), missing, prefs.override(location.getId()));
	}

	/**
	 * A patch's own rule (e.g. a Farming Guild tier), ignoring its location. Overrides apply to whole locations, so
	 * a patch's own rule is never overridden.
	 */
	public LocationStatus patchRuleStatus(Patch patch)
	{
		PlayerFacts facts = player.getFacts();
		List<String> missing = patch.getUnlockRuleId() == null ? List.of()
			: UnlockEvaluator.missing(data.rule(patch.getUnlockRuleId()), facts);
		return LocationStatus.of(facts.isKnown(), missing, null);
	}

	/** Usable: its location is usable and so is its own rule (both count as usable before login). */
	public boolean isUsable(Patch patch)
	{
		return status(data.location(patch.getLocationId())).isUsable() && patchRuleStatus(patch).isUsable();
	}

	/** Usable patches per kind: what a "full run" visits. */
	public Map<PatchImplementation, Integer> usablePatchCounts()
	{
		Map<PatchImplementation, Integer> counts = new EnumMap<>(PatchImplementation.class);
		for (Patch patch : data.patches())
		{
			if (isUsable(patch))
			{
				counts.merge(patch.getKind(), 1, Integer::sum);
			}
		}
		return counts;
	}

	private void changed()
	{
		listeners.forEach(Runnable::run);
	}
}
