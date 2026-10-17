/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * What the Bank tab shows, as plain data: built by {@link BankViewBuilder}, drawn by {@link BankTab}. Keeping the
 * two apart means the choices (what's listed, what counts as spare) are tested without any Swing.
 */
@Value
public class BankView
{
	/** When the remembered places were last seen, e.g. "Bank: 5 min ago · Seed vault: never". */
	String seenLine;
	List<Section> sections;

	@Value
	public static class Section
	{
		String title;
		List<Row> rows;
		/** Shown instead of rows when there are none; null to hide the section. */
		@Nullable
		String emptyText;
	}

	@Value
	public static class Row
	{
		/** Item whose icon is shown. */
		int iconItemId;
		String label;
		String value;
		/** Where the items are, e.g. "Bank 10, Seed vault 2". */
		String tooltip;
	}
}
