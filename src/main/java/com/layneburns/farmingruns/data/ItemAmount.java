/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import lombok.Value;

/** An amount of one item, e.g. a protection payment (3 baskets of tomatoes) or a spell's runes. */
@Value
public class ItemAmount
{
	String name;
	int itemId;
	int amount;
}
