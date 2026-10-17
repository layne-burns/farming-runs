/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import java.util.List;
import java.util.Set;
import lombok.Value;

/**
 * Something a run needs you to bring (SPEC §2.4): an amount of any of some items, why, and how much it matters.
 * Made by {@link RequirementsBuilder}, checked against your items by {@link InventoryChecker}.
 */
@Value
public class Requirement
{
	public enum Importance
	{
		/** The run can't be done properly without it: seeds, compost, payment, spade, teleports. */
		NEED,
		/** Helps but isn't needed: rake, magic secateurs, farmer's outfit, amulet of bounty. */
		RECOMMENDED
	}

	/** E.g. "Ranarr seed", "Supercompost", "Skills necklace", "Law rune". */
	String label;
	/** Any of these items counts (every charge of a necklace, every kind of axe). */
	Set<Integer> itemIds;
	int amount;
	Importance importance;
	/** The tool leprechaun can store it (tools, compost), so a stored one counts. */
	boolean storedByLeprechaun;
	/** What it's for, e.g. "6 herb patches", "Catherby", "Fairy ring CIR (Farming Guild)". */
	List<String> uses;

	public String why()
	{
		return String.join(", ", uses);
	}
}
