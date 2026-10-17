/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.run.GuidanceTracker;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.run.Protection;
import com.layneburns.farmingruns.run.RunController;
import com.layneburns.farmingruns.run.RunProgress;
import com.layneburns.farmingruns.run.RunSession;
import com.layneburns.farmingruns.run.RunStats;
import com.layneburns.farmingruns.run.RunSummary;
import com.layneburns.farmingruns.run.RunTracker;
import com.layneburns.farmingruns.run.SeedsLeft;
import com.layneburns.farmingruns.run.TeleportSuggestions;
import com.layneburns.farmingruns.state.ItemStore;
import com.layneburns.farmingruns.state.LocationPrefs;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.state.StatsStore;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Live tab: the run in progress. The header (stops done, time, XP gained, seeds still needed against what you carry,
 * Stop), the current stop's checklist, the next stop, the stops still to come and those done, teleport suggestions,
 * and once the run ends its summary. With no run going: your last run's summary and your lifetime stats.
 */
@Singleton
public class LiveTab extends TabPanel
{
	private final GameData data;
	private final RunSession session;
	private final RunController controller;
	private final RunTracker tracker;
	private final GuidanceTracker guidance;
	private final ItemStore items;
	private final PlayerInfo player;
	private final StatsStore stats;
	private final TeleportSuggestions suggestions;
	private final LocationPrefs prefs;

	private final FitWidthPanel content = new FitWidthPanel();
	private final Map<String, Boolean> collapsed = new HashMap<>(Map.of("Done", true));
	private final AtomicBoolean redrawQueued = new AtomicBoolean();
	/** The header's "3 of 9 stops · 12:34 · 1,234 XP" line, ticked every second while a run is going. */
	@Nullable
	private JLabel status;

	@Inject
	LiveTab(GameData data, RunSession session, RunController controller, RunTracker tracker, GuidanceTracker guidance,
		ItemStore items, PlayerInfo player, StatsStore stats, TeleportSuggestions suggestions, LocationPrefs prefs)
	{
		this.data = data;
		this.session = session;
		this.controller = controller;
		this.tracker = tracker;
		this.guidance = guidance;
		this.items = items;
		this.player = player;
		this.stats = stats;
		this.suggestions = suggestions;
		this.prefs = prefs;
		add(scrollPane(content), BorderLayout.CENTER);
		session.addListener(this::refresh);
		tracker.addListener(this::refresh);
		guidance.addListener(this::refresh);
		items.addListener(this::refresh);
		stats.addListener(this::refresh);
		suggestions.addListener(this::refresh);
		new Timer(1000, e -> tick()).start();
		refresh();
	}

	/** Redraws soon, on the Swing thread. Safe from any thread. */
	public void refresh()
	{
		if (redrawQueued.compareAndSet(false, true))
		{
			SwingUtilities.invokeLater(this::redraw);
		}
	}

	private void redraw()
	{
		redrawQueued.set(false);
		content.removeAll();
		status = null;
		RunSession.ActiveRun run = session.active();
		if (run == null)
		{
			content.add(TabPanel.text("No run in progress. Set one up in the Run tab and press Start."));
		}
		else
		{
			drawRun(run);
		}
		if (!suggestions.all().isEmpty())
		{
			SectionPanel suggested = section("Teleport suggestions");
			SuggestionList.fill(suggested, suggestions, prefs, data);
			content.add(suggested);
		}
		RunSummary last = tracker.lastSummary();
		if (last != null && (run == null || run.getProgress().isFinished()))
		{
			SectionPanel summary = section(last.isCompleted() ? "Run complete" : "Last run");
			RunSummaryView.summary(summary, last);
			content.add(summary);
		}
		if (run == null)
		{
			SectionPanel lifetime = section("Your stats");
			RunSummaryView.stats(lifetime, stats.stats());
			content.add(lifetime);
		}
		revalidate();
		repaint();
	}

	private void drawRun(RunSession.ActiveRun run)
	{
		RunProgress progress = run.getProgress();
		Predicate<PlantingPlan.Assignment> pays = a -> Protection.shouldPay(a, run.getSetup(), data, player.getFacts());

		SectionPanel header = section("Run");
		status = TabPanel.text(statusText(run));
		header.addRow(status);
		for (SeedsLeft.Line line : SeedsLeft.of(progress, items.ledger(), data.getItems().group("seed_box")))
		{
			JLabel seeds = TabPanel.text(line.getNeeded() + " × " + line.getName() + ": carrying " + line.getCarried());
			seeds.setForeground(line.getCarried() >= line.getNeeded() ? Palette.FULL : Palette.PARTIAL);
			header.addRow(seeds);
		}
		JButton end = button(progress.isFinished() ? "Close run" : "Stop run", "Ends the run and clears the path");
		end.addActionListener(e -> controller.stop());
		header.addRow(wrap(end));
		content.add(header);

		RunProgress.Stop now = progress.currentStop();
		if (now != null)
		{
			content.add(LiveStopCard.now(data.location(now.getLocationId()).getName(),
				run.getTeleports().get(now.getLocationId()), now, progress, run.getSetup().getCompost(),
				new LiveStopCard.PatchFacts()
				{
					@Override
					public boolean composted(String patchId)
					{
						return guidance.isComposted(patchId);
					}

					@Override
					public boolean pays(PlantingPlan.Assignment assignment)
					{
						return pays.test(assignment);
					}
				}));
			RunProgress.Stop next = nextAfter(progress, now);
			if (next != null)
			{
				content.add(LiveStopCard.next(data.location(next.getLocationId()).getName(),
					run.getTeleports().get(next.getLocationId()), next, pays));
			}
		}

		List<RunProgress.Stop> stops = progress.getStops();
		SectionPanel toCome = section("Still to come");
		SectionPanel done = section("Done");
		for (int i = 0; i < stops.size(); i++)
		{
			RunProgress.Stop stop = stops.get(i);
			if (stop == now)
			{
				continue;
			}
			JPanel row = StopList.row(i + 1, stop, run, data, controller::goTo, controller::skip);
			(progress.isDone(stop) ? done : toCome).addRow(row);
		}
		content.add(toCome);
		content.add(done);
	}

	/** The stop that comes after the current one: the next not done or skipped, in route order. */
	@Nullable
	private static RunProgress.Stop nextAfter(RunProgress progress, RunProgress.Stop now)
	{
		List<RunProgress.Stop> stops = progress.getStops();
		int from = stops.indexOf(now);
		for (int step = 1; step < stops.size(); step++)
		{
			RunProgress.Stop stop = stops.get((from + step) % stops.size());
			if (!progress.isDone(stop) && !progress.isSkipped(stop))
			{
				return stop;
			}
		}
		return null;
	}

	/** "3 of 9 stops · 12:34 · 1,234 XP". */
	private String statusText(RunSession.ActiveRun run)
	{
		RunProgress progress = run.getProgress();
		return progress.doneCount() + " of " + progress.getStops().size() + " stops · "
			+ RunStats.minutes(System.currentTimeMillis() - run.getStartedAtMillis())
			+ String.format(" · %,d XP", tracker.xpGained());
	}

	/** Every second: the timer in the header, without redrawing the rest. */
	private void tick()
	{
		RunSession.ActiveRun run = session.active();
		if (status != null && run != null && !run.getProgress().isFinished())
		{
			status.setText(TabPanel.text(statusText(run)).getText());
		}
	}

	private SectionPanel section(String title)
	{
		return new SectionPanel(title, collapsed.getOrDefault(title, false), state -> collapsed.put(title, state));
	}

	private static JPanel wrap(JButton button)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(Palette.BACKGROUND);
		row.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(button, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}
}
