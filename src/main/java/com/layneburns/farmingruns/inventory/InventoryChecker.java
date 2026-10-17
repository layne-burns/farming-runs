/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * Checks a run's requirements against your items (SPEC §2.4). Carried means in your inventory, worn, in your rune
 * pouch, or in your seed box when the box is in your inventory. Tools and compost the tool leprechaun holds count,
 * since every patch has a leprechaun. Anything else has to come from the bank or seed vault. Pure: unit tested.
 */
public final class InventoryChecker
{
	public enum Status
	{
		/** You have it on you. */
		OK,
		/** Take it out of the tool leprechaun at the first stop. */
		FROM_LEPRECHAUN,
		/** It's in your bank or seed vault (or a seed box left in the bank). */
		WITHDRAW,
		/** You don't have enough anywhere. */
		MISSING
	}

	@Value
	public static class Line
	{
		Requirement requirement;
		Status status;
		int carried;
		/** How many to take from the leprechaun. */
		int fromLeprechaun;
		/** How many to take from the bank or vault. */
		int toWithdraw;
		/** How many you don't have anywhere. */
		int shortBy;
		/** How many are in the bank and vault (and a seed box left in the bank). */
		int banked;

		/**
		 * How many to take out of the bank when you'd rather not use the leprechaun: everything not carried, as far
		 * as the bank has it. 0 when the bank has none or you carry enough.
		 */
		public int fromBank()
		{
			return Math.min(banked, Math.max(0, requirement.getAmount() - carried));
		}

		/** A missing thing you need: worth a "Start anyway?" question. */
		public boolean blocksStart()
		{
			return status == Status.MISSING && requirement.getImportance() == Requirement.Importance.NEED;
		}
	}

	private InventoryChecker()
	{
	}

	/**
	 * @param seedBoxIds every seed box item id: when one is carried, what's in the box counts as carried
	 */
	public static List<Line> check(List<Requirement> requirements, ItemLedger ledger, Set<Integer> seedBoxIds)
	{
		boolean boxCarried = count(ledger, seedBoxIds, ItemSource.INVENTORY) > 0;
		return requirements.stream().map(r -> line(r, ledger, boxCarried)).collect(Collectors.toList());
	}

	private static Line line(Requirement r, ItemLedger ledger, boolean boxCarried)
	{
		Set<Integer> ids = r.getItemIds();
		int carried = count(ledger, ids, ItemSource.INVENTORY) + count(ledger, ids, ItemSource.EQUIPMENT)
			+ count(ledger, ids, ItemSource.RUNE_POUCH) + (boxCarried ? count(ledger, ids, ItemSource.SEED_BOX) : 0);
		int stored = r.isStoredByLeprechaun() ? count(ledger, ids, ItemSource.LEPRECHAUN) : 0;
		int banked = count(ledger, ids, ItemSource.BANK) + count(ledger, ids, ItemSource.SEED_VAULT)
			+ (boxCarried ? 0 : count(ledger, ids, ItemSource.SEED_BOX));

		int need = r.getAmount();
		int fromLeprechaun = Math.min(stored, Math.max(0, need - carried));
		int toWithdraw = Math.min(banked, Math.max(0, need - carried - fromLeprechaun));
		int shortBy = Math.max(0, need - carried - fromLeprechaun - toWithdraw);
		Status status = shortBy > 0 ? Status.MISSING
			: toWithdraw > 0 ? Status.WITHDRAW
			: fromLeprechaun > 0 ? Status.FROM_LEPRECHAUN
			: Status.OK;
		return new Line(r, status, carried, fromLeprechaun, toWithdraw, shortBy, banked);
	}

	private static int count(ItemLedger ledger, Set<Integer> ids, ItemSource source)
	{
		int total = 0;
		for (int id : ids)
		{
			total += ledger.count(id, source);
		}
		return total;
	}
}
