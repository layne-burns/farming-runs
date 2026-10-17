/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.util.List;
import lombok.Value;

/** A spell a run casts that isn't a teleport (Fertile Soil instead of compost), with its runes per cast. */
@Value
public class Spell
{
	String id;
	String name;
	/** "Normal", "Lunar", "Arceuus" or "Ancient", as the wiki writes it. */
	String spellbook;
	List<ItemAmount> runes;
}
