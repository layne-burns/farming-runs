/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.ItemAmount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out which patches were just paid for: the game doesn't show protection, but paying the gardener takes the
 * produce out of your inventory. Each patch whose whole payment is among what just left takes its share, in order,
 * so two allotments paid one after the other are both seen. Pure: unit tested.
 */
public final class PaymentMatcher
{
	private PaymentMatcher()
	{
	}

	/**
	 * @param spent    item id to how many just left your inventory (noted counted as unnoted)
	 * @param awaiting patches still to be paid for, where you are
	 * @return the ids of the patches now paid for
	 */
	public static List<String> paid(Map<Integer, Integer> spent, List<PlantingPlan.Assignment> awaiting)
	{
		Map<Integer, Integer> left = new HashMap<>(spent);
		List<String> paid = new ArrayList<>();
		for (PlantingPlan.Assignment a : awaiting)
		{
			if (a.getSeed() == null || a.getSeed().getPayment().isEmpty())
			{
				continue;
			}
			List<ItemAmount> payment = a.getSeed().getPayment();
			boolean covered = payment.stream().allMatch(p -> left.getOrDefault(p.getItemId(), 0) >= p.getAmount());
			if (covered)
			{
				payment.forEach(p -> left.merge(p.getItemId(), -p.getAmount(), Integer::sum));
				paid.add(a.getPatch().getId());
			}
		}
		return paid;
	}

	/** What went down between two inventories: item id to how many fewer. */
	public static Map<Integer, Integer> spent(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		Map<Integer, Integer> spent = new HashMap<>();
		before.forEach((id, count) ->
		{
			int fewer = count - after.getOrDefault(id, 0);
			if (fewer > 0)
			{
				spent.put(id, fewer);
			}
		});
		return spent;
	}
}
