/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import java.util.List;
import lombok.Value;
import lombok.With;

/**
 * The plan for one kind of patch (herb, allotment...): which seeds (picked automatically, or the crops you chose
 * with their shares), whether to pay the gardener, and for trees what to do with the grown tree.
 */
@Value
@With
public class KindPlan
{
	/** Auto: the best seeds you can spare. */
	public static final KindPlan AUTO = new KindPlan(true, List.of(), false, TreeRoutine.PAY_TO_REMOVE);

	/** True: pick the best seeds you can spare. False: use {@link #crops}. */
	boolean auto;
	/** Your chosen crops and their shares (used when not auto). */
	List<CropShare> crops;
	/** Pay the gardener to protect the crop, where it can be protected. */
	boolean pay;
	TreeRoutine treeRoutine;
}
