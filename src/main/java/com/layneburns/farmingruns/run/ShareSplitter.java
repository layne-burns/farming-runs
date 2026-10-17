/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Shares out a number of patches between crops: exact counts first, then the rest in proportion to the weights
 * (equal weights split evenly; 9 patches at 50:50 is 5 and 4). No crop gets more than its capacity (the patches its
 * spare seeds can fill); what it can't take goes to the others. Pure: unit tested.
 */
public final class ShareSplitter
{
	@Value
	public static class Share
	{
		int weight;
		/** An exact number of patches, or null to share by weight. */
		@Nullable
		Integer exact;
		/** Most patches this crop can fill. */
		int capacity;
	}

	private ShareSplitter()
	{
	}

	/** Patches per share, in the same order; their total is at most {@code patches}. */
	public static int[] split(int patches, List<Share> shares)
	{
		int[] given = new int[shares.size()];
		int left = patches;

		// 1. Exact counts, in order, as far as seeds and patches allow.
		for (int i = 0; i < shares.size() && left > 0; i++)
		{
			Integer exact = shares.get(i).getExact();
			if (exact != null)
			{
				given[i] = Math.min(left, Math.min(exact, shares.get(i).getCapacity()));
				left -= given[i];
			}
		}

		// 2. The rest by weight, in rounds: everyone with room gets the whole part of their fair share, then single
		// patches go to the largest leftover fractions. A crop that runs out of seeds drops out, and the next round
		// shares what it couldn't take among the others.
		while (left > 0)
		{
			List<Integer> active = new ArrayList<>();
			long totalWeight = 0;
			for (int i = 0; i < shares.size(); i++)
			{
				if (hasRoom(shares.get(i), given[i]))
				{
					active.add(i);
					totalWeight += shares.get(i).getWeight();
				}
			}
			if (active.isEmpty())
			{
				break;
			}
			int handed = 0;
			double[] fraction = new double[shares.size()];
			for (int i : active)
			{
				double fair = (double) left * shares.get(i).getWeight() / totalWeight;
				int whole = Math.min((int) fair, shares.get(i).getCapacity() - given[i]);
				given[i] += whole;
				handed += whole;
				fraction[i] = fair - Math.floor(fair);
			}
			left -= handed;
			active.sort(Comparator.comparingDouble((Integer i) -> -fraction[i]));
			for (int i : active)
			{
				if (left == 0)
				{
					break;
				}
				if (given[i] < shares.get(i).getCapacity())
				{
					given[i]++;
					left--;
					handed++;
				}
			}
			if (handed == 0)
			{
				break;
			}
		}
		return given;
	}

	private static boolean hasRoom(Share share, int given)
	{
		return share.getExact() == null && share.getWeight() > 0 && given < share.getCapacity();
	}
}
