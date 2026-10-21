/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import com.layneburns.farmingruns.data.Compostable;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.run.CompostBinFill;
import com.layneburns.farmingruns.run.CompostBinPlanner;
import com.layneburns.farmingruns.state.CompostPrefs;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.state.ItemStore;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Joins the compost bin's ranking ({@link CompostBinFill}) to your items and choices: what fills the bin, from your
 * bank and inventory less what the run needs otherwise. Any thread.
 */
@Singleton
public class CompostBinChooser
{
	private final GameData data;
	private final ItemStore items;
	private final CompostPrefs prefs;
	private final CompostBinPlanner bins;

	@Inject
	CompostBinChooser(GameData data, ItemStore items, CompostPrefs prefs, CompostBinPlanner bins)
	{
		this.data = data;
		this.items = items;
		this.prefs = prefs;
		this.bins = bins;
	}

	/**
	 * @param needed   how many the bin takes; 0 just ranks
	 * @param reserved item id to how many the run uses otherwise (gardeners' payments), held back from the bin
	 */
	public CompostBinFill.Fill fill(int needed, Map<Integer, Integer> reserved)
	{
		ItemLedger ledger = items.ledger();
		Map<Integer, Integer> held = new HashMap<>();
		for (Compostable item : data.compostables())
		{
			int id = item.getItemId();
			int have = ledger.count(id, ItemSource.BANK) + ledger.count(id, ItemSource.INVENTORY);
			held.put(id, Math.max(0, have - reserved.getOrDefault(id, 0)));
		}
		return CompostBinFill.fill(data.compostables(), data.seeds(), held, prefs.choices(), needed);
	}

	/** The ranking, for the Run tab. */
	public CompostBinFill.Fill ranking()
	{
		return fill(0, Map.of());
	}

	/** What to put in this bin now, e.g. "30 × Watermelon"; empty when it needs nothing or you have nothing spare. */
	public String wordsFor(Patch bin)
	{
		return fill(bins.work(bin).getFill(), Map.of()).words();
	}
}
