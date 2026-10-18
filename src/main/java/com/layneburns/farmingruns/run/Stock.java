/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.ItemAmount;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.state.BufferSettings;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The seeds and saplings {@link SeedPicker} still has to plan with, as it hands them out. For trees it normally
 * counts only grown saplings; with "grow saplings" on it counts tree seeds too, and remembers how many saplings would
 * have to be grown from seed. The buffer applies to saplings and seeds separately: with a sapling buffer of 2, two of
 * each are kept back. With "only plant what I can pay for", the payment produce held (also in the map) limits
 * protectable seeds, and each one planted uses a payment.
 */
final class Stock
{
	private final Map<Integer, Integer> left;
	private final BufferSettings buffers;
	/** Saplings to grow from seed before the run, by seed. */
	private final Map<Seed, Integer> toGrow = new LinkedHashMap<>();
	private boolean growSaplings;
	/** For the kind being planned: protectable seeds are limited by the payment produce left. */
	private boolean paymentLimited;

	Stock(Map<Integer, Integer> held, BufferSettings buffers)
	{
		this.left = new HashMap<>(held);
		this.buffers = buffers;
	}

	/** Whether tree seeds count, for the kind being planned now. */
	void setGrowSaplings(boolean growSaplings)
	{
		this.growSaplings = growSaplings;
	}

	/** Whether protectable seeds are limited by the payment produce left, for the kind being planned now. */
	void setPaymentLimited(boolean paymentLimited)
	{
		this.paymentLimited = paymentLimited;
	}

	/**
	 * Patches this seed can still fill from what's spare (held beyond the buffer, less what's already used), and when
	 * payment-limited, no more than the payments left cover.
	 */
	int capacity(Seed seed)
	{
		int buffer = buffers.bufferFor(seed);
		int spare = Math.max(0, saplings(seed) - buffer) + Math.max(0, seeds(seed) - buffer);
		int patches = spare / seed.getPerPatch();
		return paymentLimited && seed.canBeProtected() ? Math.min(patches, payable(seed)) : patches;
	}

	/** How many patches of this seed the payment produce left can protect. */
	private int payable(Seed seed)
	{
		int patches = Integer.MAX_VALUE;
		for (ItemAmount pay : seed.getPayment())
		{
			patches = Math.min(patches, left.getOrDefault(pay.getItemId(), 0) / pay.getAmount());
		}
		return patches;
	}

	/**
	 * Takes {@code amount} to plant: spare saplings, then spare seeds, and only then (for the farming contract, which
	 * may use the buffer) the ones kept back. False, taking nothing, when there aren't that many at all.
	 */
	boolean take(Seed seed, int amount)
	{
		if (saplings(seed) + seeds(seed) < amount)
		{
			return false;
		}
		int buffer = buffers.bufferFor(seed);
		int need = amount;
		need -= use(seed.plantItemId(), Math.min(need, Math.max(0, saplings(seed) - buffer)));
		int fromSeeds = Math.min(need, Math.max(0, seeds(seed) - buffer));
		need -= fromSeeds;
		int fromBufferedSaplings = Math.min(need, saplings(seed));
		need -= use(seed.plantItemId(), fromBufferedSaplings);
		fromSeeds += need;
		if (fromSeeds > 0)
		{
			use(seed.getItemId(), fromSeeds);
			toGrow.merge(seed, fromSeeds, Integer::sum);
		}
		if (paymentLimited && seed.canBeProtected())
		{
			// One patch's payment, so another crop paid with the same produce can't count it too.
			seed.getPayment().forEach(pay -> use(pay.getItemId(), pay.getAmount()));
		}
		return true;
	}

	Map<Seed, Integer> toGrow()
	{
		return Map.copyOf(toGrow);
	}

	/** Saplings (or, for other kinds, the seeds themselves) left to plant. */
	private int saplings(Seed seed)
	{
		return left.getOrDefault(seed.plantItemId(), 0);
	}

	/** Tree seeds left that would be grown into saplings; 0 unless they count. */
	private int seeds(Seed seed)
	{
		return growSaplings && seed.isPlantedAsSapling() ? left.getOrDefault(seed.getItemId(), 0) : 0;
	}

	/** Uses {@code count} of an item; returns {@code count}. */
	private int use(int itemId, int count)
	{
		left.merge(itemId, -count, Integer::sum);
		return count;
	}
}
