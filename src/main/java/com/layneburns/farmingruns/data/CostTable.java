/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;

/**
 * Travel costs (in ticks) for ordering a run, read from costs.tsv: each location's "arrive" cost (its
 * teleport plus the walk) and cheaper "link" costs between neighbours. A leg costs the cheapest of its link, a walk
 * between the two places when they're close ({@link #withPlaces}), and the destination's arrive cost.
 */
public final class CostTable
{
	/** Running is two tiles a tick; paths wind, so allow about a third more. */
	static final double TICKS_PER_TILE = 0.65;
	/** Further apart than this, a walk is never worth counting (and may cross water or walls). */
	static final int MAX_WALK_TILES = 60;

	private final Map<String, Integer> arrive;
	private final Map<String, Integer> links;
	/** Where each location is, to price walks between close ones; empty for none. */
	private final Map<String, WorldPoint> places;

	private CostTable(Map<String, Integer> arrive, Map<String, Integer> links, Map<String, WorldPoint> places)
	{
		this.arrive = Collections.unmodifiableMap(arrive);
		this.links = Collections.unmodifiableMap(links);
		this.places = Map.copyOf(places);
	}

	/** The same costs, also pricing a walk between locations close to each other. */
	public CostTable withPlaces(Map<String, WorldPoint> places)
	{
		return new CostTable(arrive, links, places);
	}

	/** Cost of the leg from {@code from} (null: the start of the run) to {@code to}. */
	public int cost(@Nullable String from, String to)
	{
		Integer cost = arrive.get(to);
		if (cost == null)
		{
			throw new IllegalArgumentException("no arrive cost for " + to);
		}
		if (from == null)
		{
			return cost;
		}
		Integer link = links.get(linkKey(from, to));
		int best = link != null ? link : cost;
		Integer walk = walk(from, to);
		return walk != null ? Math.min(best, walk) : best;
	}

	/** Ticks to walk between two close locations on the same level; null when they aren't close. */
	@Nullable
	Integer walk(String from, String to)
	{
		WorldPoint a = places.get(from);
		WorldPoint b = places.get(to);
		if (a == null || b == null || a.getPlane() != b.getPlane())
		{
			return null;
		}
		int tiles = Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
		return tiles > MAX_WALK_TILES ? null : (int) Math.ceil(tiles * TICKS_PER_TILE);
	}

	public Set<String> locationsWithArriveCost()
	{
		return arrive.keySet();
	}

	/** Location ids used by links, for validation. */
	public Set<String> linkedPairs()
	{
		return links.keySet();
	}

	static String linkKey(String a, String b)
	{
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	/** Parses the tab-separated file; '#' lines, blank lines and the header row are skipped. */
	static CostTable parse(Reader source) throws IOException
	{
		Map<String, Integer> arrive = new HashMap<>();
		Map<String, Integer> links = new HashMap<>();
		BufferedReader reader = new BufferedReader(source);
		String line;
		int number = 0;
		while ((line = reader.readLine()) != null)
		{
			number++;
			if (line.isBlank() || line.startsWith("#") || line.startsWith("type\t"))
			{
				continue;
			}
			String[] cells = line.split("\t");
			if (cells.length != 4)
			{
				throw new DataException("costs.tsv line " + number + ": expected 4 tab-separated cells: " + line);
			}
			int ticks;
			try
			{
				ticks = Integer.parseInt(cells[3].trim());
			}
			catch (NumberFormatException e)
			{
				throw new DataException("costs.tsv line " + number + ": ticks is not a number: " + cells[3]);
			}
			switch (cells[0])
			{
				case "arrive":
					arrive.put(cells[1], ticks);
					break;
				case "link":
					links.put(linkKey(cells[1], cells[2]), ticks);
					break;
				default:
					throw new DataException("costs.tsv line " + number + ": unknown type " + cells[0]);
			}
		}
		return new CostTable(arrive, links, Map.of());
	}
}
