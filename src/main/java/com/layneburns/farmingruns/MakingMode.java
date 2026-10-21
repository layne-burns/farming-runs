/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Something to make at the bank apart from a run (the Making tab). While one is on, the bank's search button, the
 * bank tab and the highlights show what it takes instead of the run's items.
 */
@Getter
@RequiredArgsConstructor
public enum MakingMode
{
	OFF("Off"),
	SAPLINGS("Sapling making"),
	PAYMENTS("Payment making");

	private final String title;

	@Override
	public String toString()
	{
		return title;
	}
}
