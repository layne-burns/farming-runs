/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Singleton;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Teleports you used to reach a stop that aren't its first choice, offered in the Run tab as "make it first". Never
 * applied by itself. A dismissed suggestion isn't offered again until the client restarts. Safe from any thread.
 */
@Singleton
public class TeleportSuggestions
{
	@Value
	public static class Suggestion
	{
		String locationId;
		/** The teleport you used, as "method" or "method:destination". */
		String ref;
		/** Coins you spent on the way after it (boat fares); 0 if none. */
		@EqualsAndHashCode.Exclude
		int coins;
	}

	private final List<Suggestion> suggestions = new CopyOnWriteArrayList<>();
	private final Set<Suggestion> dismissed = new HashSet<>();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public List<Suggestion> all()
	{
		return List.copyOf(suggestions);
	}

	/** Offers a teleport for a location, replacing any earlier offer for it; false if it was dismissed or offered. */
	public synchronized boolean offer(Suggestion suggestion)
	{
		if (dismissed.contains(suggestion) || suggestions.contains(suggestion))
		{
			return false;
		}
		suggestions.removeIf(s -> s.getLocationId().equals(suggestion.getLocationId()));
		suggestions.add(suggestion);
		listeners.forEach(Runnable::run);
		return true;
	}

	/** Taken up: it's gone. */
	public void accepted(Suggestion suggestion)
	{
		suggestions.remove(suggestion);
		listeners.forEach(Runnable::run);
	}

	/** Turned down: gone, and not offered again this session. */
	public synchronized void dismiss(Suggestion suggestion)
	{
		dismissed.add(suggestion);
		suggestions.remove(suggestion);
		listeners.forEach(Runnable::run);
	}
}
