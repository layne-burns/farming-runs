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
import javax.swing.JPanel;

/**
 * Locations tab: unlock state per location, overrides, teleport choice and patch ready times (SPEC §2.1).
 */
@Singleton
public class LocationsTab extends JPanel
{
	LocationsTab()
	{
		super(new BorderLayout());
		add(new JLabel("<html>Coming in phase 3.</html>"), BorderLayout.NORTH);
	}
}
