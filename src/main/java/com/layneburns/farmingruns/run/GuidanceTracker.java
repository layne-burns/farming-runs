/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.GameData;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Follows the game's messages during a run: "You treat the bush patch with supercompost." marks a patch composted,
 * and "You pay the gardener 8 ... to protect the patch." marks it paid for. Gives the overlays what to point out at
 * the current stop ({@link RunGuidance}). Client thread.
 */
@Slf4j
@Singleton
public class GuidanceTracker
{
	private static final Pattern TREATED = Pattern.compile("you treat the (.+) with (?:ultra|super)?compost");
	private static final Pattern PAID = Pattern.compile("you pay the gardener .+ to protect");

	private final GameData data;
	private final RunSession session;
	/** Patches composted in the current run, and which run that is (by its start time). */
	private final Set<String> composted = new HashSet<>();
	private long runStartedAt;

	@Inject
	GuidanceTracker(GameData data, RunSession session)
	{
		this.data = data;
		this.session = session;
	}

	/** What to outline now: nothing when no run is going. */
	public RunGuidance.Highlights highlights()
	{
		RunSession.ActiveRun run = currentRun();
		if (run == null)
		{
			return RunGuidance.NONE;
		}
		return RunGuidance.at(run.getProgress().currentStop(), run.getProgress(), run.getSetup().getCompost(),
			Set.copyOf(composted), data.getItems());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		RunSession.ActiveRun run = currentRun();
		RunProgress.Stop stop = run == null ? null : run.getProgress().currentStop();
		if (stop == null)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage()).toLowerCase(Locale.ROOT);
		Matcher treated = TREATED.matcher(message);
		if (treated.find())
		{
			String what = treated.group(1);
			// The patch of the kind named ("bush patch", "allotment"), else the first one waiting for compost.
			Optional<PlantingPlan.Assignment> patch = stop.getPatches().stream()
				.filter(a -> a.getSeed() != null && !composted.contains(a.getPatch().getId()))
				.filter(a -> what.contains(kindName(a)))
				.findFirst();
			patch.or(() -> stop.getPatches().stream()
				.filter(a -> a.getSeed() != null && !composted.contains(a.getPatch().getId())).findFirst())
				.ifPresent(a ->
				{
					composted.add(a.getPatch().getId());
					log.debug("[farmrun] composted {}", a.getPatch().getId());
				});
			return;
		}
		if (PAID.matcher(message).find())
		{
			// The first planted patch here still waiting to be paid for.
			RunProgress progress = run.getProgress();
			stop.getPatches().stream()
				.map(a -> a.getPatch().getId())
				.filter(id -> progress.getWorkedPatches().contains(id) && progress.isAwaitingPayment(id))
				.findFirst()
				.ifPresent(id ->
				{
					log.debug("[farmrun] paid for {}", id);
					session.update(p -> p.markPaid(id));
				});
		}
	}

	/** The active run, forgetting what was composted when a new one has started. */
	private RunSession.ActiveRun currentRun()
	{
		RunSession.ActiveRun run = session.active();
		if (run != null && run.getStartedAtMillis() != runStartedAt)
		{
			runStartedAt = run.getStartedAtMillis();
			composted.clear();
		}
		return run;
	}

	/** "allotment", "fruit tree", ... as the game words it. */
	private static String kindName(PlantingPlan.Assignment a)
	{
		return a.getPatch().getKind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
	}
}
