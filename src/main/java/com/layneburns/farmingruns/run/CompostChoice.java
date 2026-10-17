/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** What you put on each patch before planting (SPEC §2.3). */
@Getter
@RequiredArgsConstructor
public enum CompostChoice
{
	NONE("None"),
	COMPOST("Compost"),
	SUPERCOMPOST("Supercompost"),
	ULTRACOMPOST("Ultracompost"),
	BOTTOMLESS_BUCKET("Bottomless bucket"),
	FERTILE_SOIL("Fertile Soil (spell)");

	private final String title;

	@Override
	public String toString()
	{
		return title;
	}
}
