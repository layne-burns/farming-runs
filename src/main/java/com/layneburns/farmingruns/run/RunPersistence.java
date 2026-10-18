/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.google.gson.Gson;
import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.data.GameData;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

/**
 * Keeps the run in progress saved with your RuneScape profile ({@link SavedRun}) every time it moves on, so a crash,
 * a restart or a new login doesn't lose it: when there's an unfinished run and none going, the Live tab offers to
 * resume it (never by itself). Stopping or finishing a run clears it; closing the plugin or the client doesn't. Runs
 * saved more than {@link #MAX_AGE_MILLIS} ago aren't offered.
 */
@Slf4j
@Singleton
public class RunPersistence
{
	private static final String KEY = "savedRun";
	/** Older than this, patches have grown on and the run's plan is stale. */
	static final long MAX_AGE_MILLIS = 12 * 60 * 60 * 1000L;

	private final ConfigManager configManager;
	private final Gson gson;
	private final GameData data;
	private final RunSession session;
	private final RunController controller;
	private final Client client;
	private final ClientThread clientThread;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	/** False while the plugin shuts down: the run stops then, but the save is kept for next time. */
	private volatile boolean saving = true;
	/** An unfinished run saved earlier, to offer while none is going; null if none. */
	@Nullable
	private volatile RunSession.ActiveRun offer;

	@Inject
	RunPersistence(ConfigManager configManager, Gson gson, GameData data, RunSession session, RunController controller,
		Client client, ClientThread clientThread)
	{
		this.configManager = configManager;
		this.gson = gson;
		this.data = data;
		this.session = session;
		this.controller = controller;
		this.client = client;
		this.clientThread = clientThread;
		session.addListener(this::sessionChanged);
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	/** The unfinished run you could resume, or null. */
	@Nullable
	public RunSession.ActiveRun offer()
	{
		return offer;
	}

	/** Picks the offered run up where it was. Any thread. */
	public void resume()
	{
		RunSession.ActiveRun run = offer;
		if (run != null)
		{
			setOffer(null);
			controller.resume(run);
		}
	}

	/** Forgets the offered run. Any thread. */
	public void discard()
	{
		setOffer(null);
		if (configManager.getRSProfileKey() != null)
		{
			configManager.unsetRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY);
		}
	}

	/** Called first as the plugin shuts down: the run is about to stop, and its save must stay. */
	public void stopSaving()
	{
		saving = false;
	}

	/** Looks for an unfinished run saved for this account (at startup and at each login). */
	public void load()
	{
		if (session.active() != null || configManager.getRSProfileKey() == null)
		{
			return;
		}
		String json = configManager.getRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY);
		long savedAt = SavedRun.savedAt(gson, json);
		RunSession.ActiveRun run = System.currentTimeMillis() - savedAt <= MAX_AGE_MILLIS
			? SavedRun.fromJson(gson, json, data) : null;
		if (run == null || run.getProgress().isFinished())
		{
			if (json != null)
			{
				configManager.unsetRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY);
			}
			setOffer(null);
			return;
		}
		log.debug("[farmrun] unfinished run found: {} of {} stops done", run.getProgress().doneCount(),
			run.getProgress().getStops().size());
		setOffer(run);
		clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"Farming Runs: you have an unfinished run. Resume it from the Live tab.", null));
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		load();
	}

	private void sessionChanged()
	{
		if (!saving || configManager.getRSProfileKey() == null)
		{
			return;
		}
		RunSession.ActiveRun run = session.active();
		if (run == null || run.getProgress().isFinished())
		{
			if (offer == null)
			{
				configManager.unsetRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY);
			}
			return;
		}
		setOffer(null);
		configManager.setRSProfileConfiguration(FarmingRunsConfig.GROUP, KEY,
			SavedRun.toJson(gson, run, System.currentTimeMillis()));
	}

	private void setOffer(@Nullable RunSession.ActiveRun run)
	{
		offer = run;
		listeners.forEach(Runnable::run);
	}
}
