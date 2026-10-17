/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.run.SeedPicker;
import com.layneburns.farmingruns.vendor.timetracking.Produce;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Your Farming Guild contract (from Guildmaster Jane): which crop, which guild patch it goes in, and whether it's
 * planted yet (from the patch's state in Time Tracking). The run setup plants an unplanted contract's seed in its
 * guild patch first (SPEC §2.3).
 */
@Singleton
public class ContractService
{
	/** Crops whose seed isn't named after them. */
	private static final Map<String, String> SEED_NAMES = Map.of("Oak", "Acorn");

	private final Client client;
	private final GameData data;
	private final PatchStates patchStates;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	/** The contract's crop (Time Tracking's contract value), 0 for none. */
	private volatile int contractValue;
	private volatile boolean complete;

	@Inject
	ContractService(Client client, GameData data, PatchStates patchStates)
	{
		this.client = client;
		this.data = data;
		this.patchStates = patchStates;
		patchStates.addListener(this::changed);
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.FARMGUILD_CONTRACT_TYPE
			|| event.getVarbitId() == VarbitID.FARMGUILD_CONTRACT_COMPLETE)
		{
			refresh();
		}
	}

	/** Reads the contract varbits. Client thread. */
	public void refresh()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		contractValue = client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_TYPE);
		complete = client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_COMPLETE) != 0;
		changed();
	}

	/** Your contract if it still needs planting, else null (no contract, done, or already growing). */
	@Nullable
	public SeedPicker.Contract unplanted()
	{
		if (contractValue <= 0 || complete)
		{
			return null;
		}
		Produce produce = Produce.getByContractVarbitValue(contractValue);
		Seed seed = produce == null ? null : seedFor(produce, data);
		Patch patch = produce == null ? null : guildPatchFor(produce, data);
		if (seed == null || patch == null)
		{
			return null;
		}
		PatchStatus state = patchStates.status(patch, Instant.now().getEpochSecond());
		boolean planted = produce.getName().equals(state.getCrop())
			&& (state.getReadiness() == PatchStatus.Readiness.GROWING
				|| state.getReadiness() == PatchStatus.Readiness.READY);
		return planted ? null : new SeedPicker.Contract(patch.getId(), seed);
	}

	/** The seed for a contract crop: the seed named after it ("Ranarr" -> "Ranarr seed"), with a few exceptions. */
	@Nullable
	static Seed seedFor(Produce produce, GameData data)
	{
		String name = SEED_NAMES.getOrDefault(produce.getName(), produce.getName()).toLowerCase(Locale.ROOT);
		return data.seedsFor(produce.getPatchImplementation()).stream()
			.filter(s -> s.getName().toLowerCase(Locale.ROOT).startsWith(name))
			.findFirst().orElse(null);
	}

	/** The Farming Guild patch a contract crop goes in (the first, for the two allotments). */
	@Nullable
	static Patch guildPatchFor(Produce produce, GameData data)
	{
		return data.location("farming_guild").getPatches().stream()
			.filter(p -> p.getKind() == produce.getPatchImplementation())
			.findFirst().orElse(null);
	}

	private void changed()
	{
		listeners.forEach(Runnable::run);
	}
}
