/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.google.gson.Gson;
import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.inventory.RunCheckService;
import com.layneburns.farmingruns.pathing.ShortestPathClient;
import com.layneburns.farmingruns.run.LegTimer;
import com.layneburns.farmingruns.run.RunProgress;
import com.layneburns.farmingruns.run.RunSession;
import com.layneburns.farmingruns.run.RunTracker;
import com.layneburns.farmingruns.run.SetupJson;
import com.layneburns.farmingruns.state.PlayerFacts;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.state.SetupStore;
import com.layneburns.farmingruns.state.StatsStore;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * "Copy debug info": a plain-text snapshot of what the plugin knows, put on the clipboard to paste into a bug report.
 * Your setup, the run's progress, what Shortest Path was last sent, the latest inventory check and your stats. No
 * account details beyond your Farming level and spellbook.
 */
@Singleton
public class DebugInfo
{
	private final GameData data;
	private final SetupStore setups;
	private final RunSession session;
	private final RunCheckService checks;
	private final ShortestPathClient shortestPath;
	private final PlayerInfo player;
	private final RunTracker tracker;
	private final StatsStore stats;
	private final LegTimer legs;
	private final FarmingRunsConfig config;
	private final Gson gson;

	@Inject
	DebugInfo(GameData data, SetupStore setups, RunSession session, RunCheckService checks,
		ShortestPathClient shortestPath, PlayerInfo player, RunTracker tracker, StatsStore stats,
		FarmingRunsConfig config, Gson gson, LegTimer legs)
	{
		this.data = data;
		this.setups = setups;
		this.session = session;
		this.checks = checks;
		this.shortestPath = shortestPath;
		this.player = player;
		this.tracker = tracker;
		this.stats = stats;
		this.config = config;
		this.gson = gson;
		this.legs = legs;
	}

	/** Puts the snapshot on the clipboard. Swing thread. */
	public void copy()
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text()), null);
	}

	String text()
	{
		StringBuilder out = new StringBuilder("Farming Runs debug info, ").append(Instant.now()).append('\n');
		out.append("Patch table: ").append(data.getRuneliteTag()).append('\n');
		PlayerFacts facts = player.getFacts();
		out.append("Player: known=").append(facts.isKnown()).append(", Farming ").append(player.getFarmingLevel())
			.append(", spellbook ").append(facts.spellbookName()).append('\n');
		out.append("Shortest Path: on=").append(shortestPath.isAvailable()).append(", last target ")
			.append(shortestPath.lastTarget()).append(", carried items only=").append(config.pathCarriedOnly())
			.append('\n');
		out.append("Setup: ").append(SetupJson.toJson(gson, setups.current())).append('\n');

		RunSession.ActiveRun run = session.active();
		if (run == null)
		{
			out.append("Run: none\n");
		}
		else
		{
			RunProgress progress = run.getProgress();
			out.append("Run: started ").append(Instant.ofEpochMilli(run.getStartedAtMillis()))
				.append(", current stop ").append(progress.currentStop() == null ? "(finished)"
					: progress.currentStop().getLocationId())
				.append(", XP gained ").append(tracker.xpGained()).append('\n');
			for (RunProgress.Stop stop : progress.getStops())
			{
				out.append("  ").append(stop.getLocationId())
					.append(progress.isDone(stop) ? " [done]" : progress.isSkipped(stop) ? " [skipped]" : "")
					.append(" via ").append(run.getTeleports().containsKey(stop.getLocationId())
						? run.getTeleports().get(stop.getLocationId()).getRef() : "walk")
					.append('\n');
				stop.getPatches().forEach(a -> out.append("    ").append(a.getPatch().getId()).append(": ")
					.append(a.isKeptStanding() ? "keep standing" : a.getSeed() == null ? "nothing to plant"
						: a.getSeed().getName())
					.append(progress.getWorkedPatches().contains(a.getPatch().getId()) ? ", worked" : "")
					.append(progress.isAwaitingPayment(a.getPatch().getId()) ? ", awaiting payment" : "")
					.append('\n'));
			}
		}
		out.append("Check: ").append(checks.neededItems().size()).append(" item ids used, ")
			.append(checks.bankItems().size()).append(" to take from the bank, from leprechaun ")
			.append(checks.fromLeprechaun()).append('\n');
		out.append("Legs (travel = first patch click - teleport click; work = stop done - first click):\n");
		for (LegTimer.Leg leg : legs.legs())
		{
			out.append("  ").append(leg.getLocationId()).append(": travel ")
				.append(leg.getTravelMillis() < 0 ? "?" : leg.getTravelMillis() / 1000 + "s").append(", work ")
				.append(leg.getWorkMillis() / 1000).append('s')
				.append(" (").append(leg.how()).append(")\n");
		}
		out.append("Last run: ").append(tracker.lastSummary()).append('\n');
		out.append("Stats: ").append(stats.stats()).append('\n');
		return out.toString();
	}
}
