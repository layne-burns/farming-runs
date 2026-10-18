/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.state.PlayerFacts;
import com.layneburns.farmingruns.state.UnlockEvaluator;

/**
 * Whether to pay a gardener to protect a patch: the kind's plan pays, the crop can be protected, the patch has a
 * gardener, and the patch isn't disease-free for you (Hosidius after the easy Kourend & Kebos diary). The one place
 * this is decided, for the inventory check, the run and the panel. Pure.
 */
public final class Protection
{
	private Protection()
	{
	}

	public static boolean shouldPay(PlantingPlan.Assignment a, RunSetup setup, GameData data, PlayerFacts facts)
	{
		return a.getSeed() != null && !a.isUnprotected() && a.getSeed().canBeProtected() && a.getPatch().hasGardener()
			&& setup.kindPlan(a.getPatch().getKind()).isPay() && !isDiseaseFree(a.getPatch(), data, facts);
	}

	/** True when the patch can't catch disease for you, so protecting it is pointless. */
	public static boolean isDiseaseFree(Patch patch, GameData data, PlayerFacts facts)
	{
		String rule = data.location(patch.getLocationId()).getDiseaseFreeRuleId();
		return rule != null && facts.isKnown() && UnlockEvaluator.missing(data.rule(rule), facts).isEmpty();
	}
}
