/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import javax.annotation.Nullable;
import lombok.Value;

/** Produce that can fill the compost bin and makes supercompost (compost_bin.json, SPEC §2.7). */
@Value
public class Compostable
{
	String name;
	int itemId;
	/** Where it's sold, e.g. "Trader Crewmember, Deepfin Point"; null when you only grow it. */
	@Nullable
	String shop;

	public boolean isShopBought()
	{
		return shop != null;
	}
}
