/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.vendor.timetracking.Produce;
import org.junit.BeforeClass;
import org.junit.Test;

public class ContractServiceTest
{
	private static GameData data;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
	}

	@Test
	public void everyContractCropHasASeedAndAGuildPatch()
	{
		for (Produce produce : Produce.values())
		{
			if (produce.getContractVarbitValue() > 0)
			{
				assertNotNull("no seed for contract crop " + produce.getName(), ContractService.seedFor(produce, data));
				assertNotNull("no guild patch for " + produce.getName(), ContractService.guildPatchFor(produce, data));
			}
		}
	}

	@Test
	public void knownContracts()
	{
		assertEquals("Acorn", ContractService.seedFor(Produce.OAK, data).getName());
		assertEquals("Cactus seed", ContractService.seedFor(Produce.CACTUS, data).getName());
		assertEquals("Potato seed", ContractService.seedFor(Produce.POTATO, data).getName());
		assertEquals("farming_guild.herb", ContractService.guildPatchFor(Produce.RANARR, data).getId());
		assertEquals("farming_guild.allotment_north", ContractService.guildPatchFor(Produce.POTATO, data).getId());
	}
}
