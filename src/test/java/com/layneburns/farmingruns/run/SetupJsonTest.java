/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class SetupJsonTest
{
	private final Gson gson = new Gson();

	@Test
	public void aSetupSurvivesSavingAndLoading()
	{
		RunSetup setup = RunSetup.EMPTY
			.withRunTypes(Set.of(RunType.HERB, RunType.TREE))
			.withKindPlan(PatchImplementation.HERB, new KindPlan(false,
				List.of(CropShare.even(ItemID.RANARR_SEED), new CropShare(ItemID.SNAPDRAGON_SEED, 1, 4)), false,
				TreeRoutine.PAY_TO_REMOVE, false, FillOrder.MOST_XP, null, true, Set.of(5288), true, true))
			.withKindPlan(PatchImplementation.ALLOTMENT, KindPlan.AUTO.withAmuletOfBounty(false))
			.withKindPlan(PatchImplementation.TREE, KindPlan.AUTO.withPay(true).withTreeRoutine(TreeRoutine.CHOP_AND_DIG))
			.withKindPlan(PatchImplementation.FRUIT_TREE, KindPlan.AUTO.withSkipHarvest(true))
			.withSkipped("weiss.herb", true)
			.withCompost(CompostChoice.ULTRACOMPOST)
			.withLeftOut(PatchImplementation.SPIRIT_TREE, true);
		assertEquals(setup, SetupJson.fromJson(gson, SetupJson.toJson(gson, setup)));
	}

	@Test
	public void gapsInAnOldSaveFallBackToDefaults()
	{
		RunSetup setup = SetupJson.fromJson(gson, "{\"runTypes\":[\"HERB\"]}");
		assertEquals(Set.of(RunType.HERB), setup.getRunTypes());
		assertEquals(CompostChoice.SUPERCOMPOST, setup.getCompost());
		assertEquals(KindPlan.AUTO, setup.kindPlan(PatchImplementation.HERB));
		assertEquals(Set.of(), setup.getLeftOutKinds());
	}

	@Test
	public void aKindLeftOutIsntInTheRunButTheRestOfItsTypeIs()
	{
		RunSetup special = RunSetup.EMPTY.withRunTypes(Set.of(RunType.SPECIAL_TREES))
			.withLeftOut(PatchImplementation.SPIRIT_TREE, true);
		assertFalse(special.includes(PatchImplementation.SPIRIT_TREE));
		assertTrue(special.includes(PatchImplementation.CALQUAT));
		assertTrue(special.withLeftOut(PatchImplementation.SPIRIT_TREE, false).includes(PatchImplementation.SPIRIT_TREE));
	}

	@Test
	public void unreadableSavesAreIgnored()
	{
		assertNull(SetupJson.fromJson(gson, null));
		assertNull(SetupJson.fromJson(gson, "not json {"));
	}
}
