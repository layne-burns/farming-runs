/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.BorderLayout;
import javax.inject.Singleton;

/**
 * Run tab: the run setup form and Start, then the ordered stop list during a run (SPEC §2.3, §2.5).
 */
@Singleton
public class RunTab extends TabPanel
{
	RunTab()
	{
		add(text("Coming in phase 5."), BorderLayout.NORTH);
	}
}
