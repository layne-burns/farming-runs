/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.state.PatchStatus;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** How the Locations tab orders its rows. Ready-ness counts only the patches of the row's section. */
@Getter
@RequiredArgsConstructor
public enum LocationSort
{
	NAME("A–Z", true),
	READY("Ready first", true),
	READY_ALL("Ready first, no sections", false);

	/** First the ones that need you (ready, then empty, diseased, dead), then soonest ready, then not seen. */
	private static final List<PatchStatus.Readiness> ORDER = List.of(PatchStatus.Readiness.READY,
		PatchStatus.Readiness.EMPTY, PatchStatus.Readiness.DISEASED, PatchStatus.Readiness.DEAD,
		PatchStatus.Readiness.GROWING);

	private final String title;
	/** True to keep a section per run type; false lists every unlocked location in one. */
	private final boolean bySection;

	public Comparator<LocationsView.Entry> comparator()
	{
		Comparator<LocationsView.Entry> byName = Comparator.comparing(LocationsView.Entry::getName);
		if (this == NAME)
		{
			return byName;
		}
		return Comparator.<LocationsView.Entry>comparingInt(e -> rank(e.getSummaryReadiness()))
			.thenComparing(Comparator.comparingLong(LocationsView.Entry::getReadyCount).reversed())
			.thenComparingLong(LocationsView.Entry::getSoonestReadyAt)
			.thenComparing(byName);
	}

	private static int rank(PatchStatus.Readiness readiness)
	{
		int i = ORDER.indexOf(readiness);
		return i < 0 ? ORDER.size() : i;
	}

	@Override
	public String toString()
	{
		return title;
	}
}
