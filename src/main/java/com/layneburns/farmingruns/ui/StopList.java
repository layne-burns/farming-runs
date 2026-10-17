/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.run.RunProgress;
import com.layneburns.farmingruns.run.RunSession;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.runelite.client.ui.FontManager;

/**
 * A stop in the Live tab's lists (SPEC §2.5): done ones ticked, the current one highlighted, skipped ones greyed. Each
 * shows its teleport and how many of its patches are done. Click a stop to go there next or skip it.
 */
final class StopList
{
	private StopList()
	{
	}

	static JPanel row(int number, RunProgress.Stop stop, RunSession.ActiveRun run, GameData data,
		Consumer<String> goTo, Consumer<String> skip)
	{
		RunProgress progress = run.getProgress();
		boolean current = stop == progress.currentStop();
		boolean done = progress.isDone(stop);
		boolean skipped = !done && progress.isSkipped(stop);
		long patchesDone = stop.getPatches().stream()
			.filter(a -> progress.isPatchDone(a.getPatch().getId())).count();

		String mark = done ? "✓" : current ? "▶" : skipped ? "–" : String.valueOf(number);
		JLabel name = new JLabel(mark + "  " + data.location(stop.getLocationId()).getName());
		name.setForeground(done ? Palette.FULL : current ? Palette.ACCENT : skipped ? Palette.SECONDARY_TEXT
			: Palette.TEXT);

		TeleportChoice teleport = run.getTeleports().get(stop.getLocationId());
		String detailText = (teleport == null ? "Walk" : teleport.displayName()) + " · " + patchesDone + "/"
			+ stop.getPatches().size() + " patches" + (skipped ? " · skipped" : "");
		JLabel detail = new JLabel(detailText);
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(Palette.SECONDARY_TEXT);
		// Shrink with "..." instead of widening the panel.
		name.setMinimumSize(new Dimension(0, 0));
		detail.setMinimumSize(new Dimension(0, 0));

		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setOpaque(false);
		text.add(name);
		text.add(detail);
		text.setPreferredSize(new Dimension(0, text.getPreferredSize().height));

		JPanel row = new JPanel(new BorderLayout());
		Color background = current ? Palette.SURFACE_HOVER : Palette.SURFACE;
		row.setBackground(background);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.BACKGROUND),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)));
		row.add(text, BorderLayout.CENTER);
		row.setToolTipText(tooltip(stop, progress));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

		JPopupMenu menu = new JPopupMenu();
		JMenuItem next = new JMenuItem("Go here next");
		next.addActionListener(e -> goTo.accept(stop.getLocationId()));
		JMenuItem leave = new JMenuItem("Skip this stop");
		leave.setEnabled(!done && !skipped);
		leave.addActionListener(e -> skip.accept(stop.getLocationId()));
		menu.add(next);
		menu.add(leave);
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				menu.show(row, e.getX(), e.getY());
			}
		});
		return row;
	}

	/** Each patch at the stop: what's planned there, and whether it's done. */
	private static String tooltip(RunProgress.Stop stop, RunProgress progress)
	{
		StringBuilder html = new StringBuilder("<html>");
		for (PlantingPlan.Assignment a : stop.getPatches())
		{
			String id = a.getPatch().getId();
			boolean patchDone = progress.isPatchDone(id);
			boolean unpaid = progress.getWorkedPatches().contains(id) && progress.isAwaitingPayment(id);
			String kind = a.getPatch().getKind().name().toLowerCase().replace('_', ' ');
			String what = a.isKeptStanding() ? "pick, leave standing"
				: a.getSeed() == null ? "harvest" : "plant " + a.getSeed().getName();
			html.append(patchDone ? "✓ " : "• ").append(kind)
				.append(a.getPatch().getName().isEmpty() ? "" : " (" + a.getPatch().getName() + ")")
				.append(": ").append(what)
				.append(unpaid ? " — <font color='" + Palette.hex(Palette.PARTIAL) + "'>pay the gardener</font>"
					: progress.isAwaitingPayment(id) ? ", then pay the gardener" : "")
				.append("<br>");
		}
		return html.append("</html>").toString();
	}
}
