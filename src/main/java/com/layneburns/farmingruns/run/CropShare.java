/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import javax.annotation.Nullable;
import lombok.Value;

/**
 * One crop chosen for a patch kind, and its share of the patches: either a weight (shares are split in proportion;
 * equal weights split evenly) or an exact number of patches.
 */
@Value
public class CropShare
{
	/** The seed's item id (for trees, the seed, not the sapling). */
	int seedItemId;
	/** Relative share when patches are split by proportion; 1 for an even split. */
	int weight;
	/** An exact number of patches for this crop, or null to split by weight. */
	@Nullable
	Integer patches;

	public static CropShare even(int seedItemId)
	{
		return new CropShare(seedItemId, 1, null);
	}
}
