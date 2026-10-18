/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.run.KindPlan;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * On a harvest run's card: which types to leave standing (fruit trees, bushes, cacti), a tick per type, all ticked by
 * default. A patch growing an unticked type (as Time Tracking last saw it) is cleared and replanted. Collapsed to a
 * one-line summary until clicked; whether it's open is remembered in {@code collapsed} under {@code key}.
 */
class KeepStandingPanel extends JPanel
{
	KeepStandingPanel(KindPlan plan, List<Seed> seeds, Consumer<KindPlan> onChange, Map<String, Boolean> collapsed,
		String key)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);

		long keptTypes = seeds.stream().filter(s -> !plan.getReplaceStanding().contains(s.getItemId())).count();
		JLabel heading = new JLabel();
		heading.setFont(FontManager.getRunescapeSmallFont());
		heading.setForeground(Palette.SECONDARY_TEXT);
		heading.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		heading.setToolTipText("Untick a type to clear and replant it instead of leaving it standing");
		heading.setAlignmentX(LEFT_ALIGNMENT);
		add(heading);

		JPanel grid = new JPanel(new GridLayout(0, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(LEFT_ALIGNMENT);
		for (Seed seed : seeds)
		{
			grid.add(tick(plan, seed, onChange));
		}
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		add(grid);

		String summary = "Keep standing: " + (keptTypes == seeds.size() ? "all types" : keptTypes + " of "
			+ seeds.size() + " types");
		Runnable show = () ->
		{
			boolean closed = collapsed.getOrDefault(key, true);
			heading.setText((closed ? "▸ " : "▾ ") + summary);
			grid.setVisible(!closed);
			setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
			revalidate();
		};
		heading.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				collapsed.put(key, !collapsed.getOrDefault(key, true));
				show.run();
			}
		});
		show.run();
	}

	/** Ticked: leave this type standing. Unticked: replace it. */
	private static JCheckBox tick(KindPlan plan, Seed seed, Consumer<KindPlan> onChange)
	{
		JCheckBox box = new JCheckBox(treeName(seed), !plan.getReplaceStanding().contains(seed.getItemId()));
		box.setForeground(Palette.TEXT);
		box.setOpaque(false);
		box.setFocusable(false);
		box.setToolTipText("Ticked: " + treeName(seed).toLowerCase() + " is picked and left standing. Unticked:"
			+ " cleared and replanted.");
		box.addActionListener(e ->
		{
			Set<Integer> replace = new HashSet<>(plan.getReplaceStanding());
			if (box.isSelected())
			{
				replace.remove(seed.getItemId());
			}
			else
			{
				replace.add(seed.getItemId());
			}
			onChange.accept(plan.withReplaceStanding(Set.copyOf(replace)));
		});
		return box;
	}

	/** "Papaya tree seed" → "Papaya", "Pineapple seed" → "Pineapple". */
	static String treeName(Seed seed)
	{
		return seed.getName().replaceFirst("( tree| bush)? seed$", "");
	}
}
