/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.util.List;
import lombok.Value;

/** A named requirement (e.g. "Elite Morytania diary"): it passes when all its conditions pass. */
@Value
public class UnlockRule
{
	String id;
	String name;
	List<UnlockCondition> conditions;
}
