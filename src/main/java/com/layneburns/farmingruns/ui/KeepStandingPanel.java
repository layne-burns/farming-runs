/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.run.KindPlan;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * On a fruit tree card: a tick per tree type to keep standing. A patch growing a ticked type (as Time Tracking last saw
 * it) is picked and left; the others are cleared and replanted.
 */
class KeepStandingPanel extends JPanel
{
	KeepStandingPanel(KindPlan plan, List<Seed> seeds, Consumer<KindPlan> onChange)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);

		JLabel heading = new JLabel("Keep standing (pick only):");
		heading.setFont(FontManager.getRunescapeSmallFont());
		heading.setForeground(Palette.SECONDARY_TEXT);
		heading.setAlignmentX(LEFT_ALIGNMENT);
		add(heading);

		JPanel grid = new JPanel(new GridLayout(0, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(LEFT_ALIGNMENT);
		for (Seed seed : seeds)
		{
			JCheckBox box = new JCheckBox(treeName(seed), plan.getKeepStanding().contains(seed.getItemId()));
			box.setForeground(Palette.TEXT);
			box.setOpaque(false);
			box.setFocusable(false);
			box.setToolTipText("Patches growing " + treeName(seed).toLowerCase() + " trees are picked and left standing"
				+ " instead of replanted");
			box.addActionListener(e ->
			{
				Set<Integer> keep = new HashSet<>(plan.getKeepStanding());
				if (box.isSelected())
				{
					keep.add(seed.getItemId());
				}
				else
				{
					keep.remove(seed.getItemId());
				}
				onChange.accept(plan.withKeepStanding(Set.copyOf(keep)));
			});
			grid.add(box);
		}
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		add(grid);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	/** "Papaya tree seed" → "Papaya", "Pineapple seed" → "Pineapple". */
	static String treeName(Seed seed)
	{
		return seed.getName().replaceFirst("( tree)? seed$", "");
	}
}
