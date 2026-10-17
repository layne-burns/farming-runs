/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;
import lombok.With;

/**
 * The plan for one kind of patch (herb, allotment...): which seeds (picked automatically, or the crops you chose
 * with their shares), whether to pay the gardener, and for trees what to do with the grown tree (harvest it first,
 * then pay to remove or chop it).
 */
@Value
@With
public class KindPlan
{
	/** Auto: the best seeds you can spare. */
	public static final KindPlan AUTO = new KindPlan(true, List.of(), false, TreeRoutine.PAY_TO_REMOVE, false,
		FillOrder.SHARES, null);

	/** True: pick the best seeds you can spare. False: use {@link #crops}. */
	boolean auto;
	/** Your chosen crops and their shares (used when not auto). */
	List<CropShare> crops;
	/** Pay the gardener to protect the crop, where it can be protected. */
	boolean pay;
	TreeRoutine treeRoutine;
	/**
	 * For trees with a crop (fruit trees, calquat, celastrus): true to clear without harvesting first. Stored this
	 * way round so older saves, which lack it, default to harvesting.
	 */
	boolean skipHarvest;
	/** How your chosen crops get patches: by share, or most XP first. */
	FillOrder fillOrder;
	/** Allotments: wear an amulet of bounty while planting; null to follow {@link BountyAmulet}'s default. */
	@Nullable
	Boolean amuletOfBounty;

	/** True for a harvest run on this kind: pick the crop, leave the tree, plant nothing. */
	public boolean harvestOnly(PatchImplementation kind)
	{
		return treeRoutine == TreeRoutine.LEAVE_STANDING && TreeRoutine.hasHarvest(kind);
	}
}
