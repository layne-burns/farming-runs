/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.BorderLayout;
import javax.inject.Singleton;
import javax.swing.JLabel;

/**
 * Bank tab: the seed log, run supplies and tool leprechaun storage (SPEC §2.2).
 */
@Singleton
public class BankTab extends TabPanel
{
	BankTab()
	{
		add(new JLabel("<html>Coming in phase 2.</html>"), BorderLayout.NORTH);
	}
}
