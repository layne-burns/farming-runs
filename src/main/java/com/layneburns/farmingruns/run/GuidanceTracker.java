/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.data.TeleportChoice;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Follows the game's messages during a run: "You treat the bush patch with supercompost." marks a patch composted,
 * and "You pay the gardener 8 ... to protect the patch." marks it paid for. Gives the overlays what to point out at
 * the current stop ({@link RunGuidance}), and while you're still at the stop you just finished, the teleport item
 * that gets you to the next one.
 * Client thread.
 */
@Slf4j
@Singleton
public class GuidanceTracker
{
	private static final Pattern TREATED = Pattern.compile("you treat the (.+) with (?:ultra|super)?compost");
	private static final Pattern PAID = Pattern.compile("you pay the gardener .+ to protect");

	private final Client client;
	private final GameData data;
	private final RunSession session;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	/** Patches composted in the current run, and which run that is (by its start time). */
	private final Set<String> composted = new HashSet<>();
	private long runStartedAt;
	/** The current stop when last looked at, and the regions of where you were when it became current. */
	private String stopId;
	private Set<Integer> leaving = Set.of();

	@Inject
	GuidanceTracker(Client client, GameData data, RunSession session)
	{
		this.client = client;
		this.data = data;
		this.session = session;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	/** True when the game said this patch was treated with compost during the run. */
	public boolean isComposted(String patchId)
	{
		return composted.contains(patchId);
	}

	/**
	 * What to outline now: nothing when no run is going. While you're still at the stop you just finished, the current
	 * stop's teleport item as well, so it's ready to use.
	 */
	public RunGuidance.Highlights highlights()
	{
		RunSession.ActiveRun run = currentRun();
		if (run == null)
		{
			return RunGuidance.NONE;
		}
		RunProgress.Stop stop = run.getProgress().currentStop();
		RunGuidance.Highlights here = RunGuidance.at(stop, run.getProgress(), run.getSetup().getCompost(),
			Set.copyOf(composted), data.getItems());
		if (stop == null || !leavingFrom(stop))
		{
			return here;
		}
		Set<Integer> items = new HashSet<>(here.getItems());
		items.addAll(teleportItems(run.getTeleports().get(stop.getLocationId())));
		return new RunGuidance.Highlights(Set.copyOf(items), here.getNpcs());
	}

	/** The items to use for a teleport: its item, or a way into your house for a portal; none for spells and walks. */
	private Set<Integer> teleportItems(@Nullable TeleportChoice teleport)
	{
		if (teleport == null)
		{
			return Set.of();
		}
		switch (teleport.getMethod().getType())
		{
			case ITEM:
				return teleport.itemIds();
			case HOUSE:
				Set<Integer> house = new HashSet<>(data.getItems().group("construction_cape"));
				house.addAll(data.getItems().group("house_tablet"));
				return house;
			default:
				return Set.of();
		}
	}

	/**
	 * True while you're still where you were when this stop became the current one: at the stop you just finished, or
	 * where you pressed Start. That's when to get its teleport ready; once you've left (by that teleport, or on foot
	 * towards the patch after landing nearby), it isn't outlined any more.
	 */
	private boolean leavingFrom(RunProgress.Stop stop)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return false;
		}
		int region = player.getWorldLocation().getRegionID();
		if (!stop.getLocationId().equals(stopId))
		{
			// The run has just moved on to this stop: remember where you are now.
			leaving = stopId == null ? Set.of(region) : regions(stopId);
			stopId = stop.getLocationId();
		}
		return leaving.contains(region) && !regions(stop.getLocationId()).contains(region);
	}

	private Set<Integer> regions(String locationId)
	{
		Location location = data.location(locationId);
		Set<Integer> regions = new HashSet<>(location.getExtraRegionIds());
		regions.add(location.getRegionId());
		return regions;
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
		// With debug logging on, every game message during a run, to tune what's matched here and in RunTracker.
		log.debug("[chat] {}", message);
		Player player = client.getLocalPlayer();
		if (player == null || !regions(stop.getLocationId()).contains(player.getWorldLocation().getRegionID()))
		{
			// Composting or paying somewhere else (a patch you're not running): not this stop's.
			return;
		}
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
					session.update(p -> p.markComposted(a.getPatch().getId()));
					listeners.forEach(Runnable::run);
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
			stopId = null;
			leaving = Set.of();
		}
		return run;
	}

	/** "allotment", "fruit tree", ... as the game words it. */
	private static String kindName(PlantingPlan.Assignment a)
	{
		return a.getPatch().getKind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
	}
}
