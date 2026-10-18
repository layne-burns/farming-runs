/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import static org.junit.Assert.assertEquals;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

public class OnTheWayTest
{
	@Test
	public void growingPatchesReadyByTheTimeYouGetThereAreKept()
	{
		GameData data = new DataLoader(new Gson()).load();
		List<Patch> herbs = data.patches().stream().filter(p -> p.getKind() == PatchImplementation.HERB)
			.limit(3).collect(Collectors.toList());
		Patch soon = herbs.get(1);
		Patch late = herbs.get(2);
		// Every leg takes 100 ticks; one herb is ready in 150 (fine as the 2nd or 3rd stop), one in 10,000.
		Map<String, Integer> readyIn = Map.of(soon.getId(), 150, late.getId(), 10_000);
		List<Patch> kept = OnTheWay.patches(herbs, p -> readyIn.getOrDefault(p.getId(), 0), (from, to) -> 100,
			(stop, patches) -> OnTheWay.WORK_TICKS_PER_PATCH * patches, null);
		assertEquals(List.of(herbs.get(0), soon), kept);
	}

	@Test
	public void ticksFromSeconds()
	{
		assertEquals(100, OnTheWay.ticks(60));
	}
}
