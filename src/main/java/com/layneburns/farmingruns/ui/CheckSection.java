/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.inventory.InventoryChecker.Line;
import com.layneburns.farmingruns.inventory.InventoryChecker.Status;
import com.layneburns.farmingruns.inventory.Requirement;
import com.layneburns.farmingruns.inventory.RunCheckService.Check;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;

/**
 * The Run tab's "Inventory" section (SPEC §2.4): one row per thing the run needs, worst first: missing, then to
 * withdraw, then to take from the tool leprechaun, then what you already carry.
 */
final class CheckSection
{
	private CheckSection()
	{
	}

	/**
	 * @param showInBank      opens the bank tab of things to take out
	 * @param buyPineapples   starts a trip to buy pineapples, given how many you want in all; shown when short
	 */
	static void fill(SectionPanel section, Check check, ItemManager itemManager, Runnable showInBank,
		IntConsumer buyPineapples)
	{
		List<Line> lines = check.getLines();
		if (lines.isEmpty())
		{
			section.addRow(TabPanel.text("Nothing to plant or visit yet."));
			return;
		}
		section.addRow(summary(lines));
		if (lines.stream().anyMatch(l -> l.fromBank() > 0))
		{
			JButton show = TabPanel.button("Show in bank", "Opens a bank tab with just what to take out, each with"
				+ " how many (needs the Bank Tags plugin). If the bank is closed, it opens when you next open the bank.");
			show.addActionListener(e -> showInBank.run());
			JPanel row = new JPanel(new BorderLayout());
			row.setBackground(Palette.BACKGROUND);
			row.add(show, BorderLayout.CENTER);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
			section.addRow(row);
		}
		lines.stream().filter(l -> l.getShortBy() > 0 && "Pineapple".equals(l.getRequirement().getLabel()))
			.findFirst().ifPresent(l ->
			{
				JButton buy = TabPanel.button("Buy pineapples at Deepfin Point", "Shortest Path to the Trader"
					+ " Crewmember at Deepfin Point; hop worlds when the stock runs out. Ends when you have "
					+ l.getRequirement().getAmount() + ".");
				buy.addActionListener(e -> buyPineapples.accept(l.getRequirement().getAmount()));
				JPanel row = new JPanel(new BorderLayout());
				row.setBackground(Palette.BACKGROUND);
				row.add(buy, BorderLayout.CENTER);
				row.setAlignmentX(Component.LEFT_ALIGNMENT);
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
				section.addRow(row);
			});
		for (String note : check.getRequirements().getNotes())
		{
			JLabel label = TabPanel.text("⚠ " + note);
			label.setForeground(Palette.PARTIAL);
			section.addRow(label);
		}
		List<Line> sorted = lines.stream()
			.sorted(Comparator.comparingInt(CheckSection::rank))
			.collect(Collectors.toList());
		for (Line line : sorted)
		{
			section.addRow(new ItemRow(itemManager, row(line)));
		}
	}

	/** Missing things you need, then to withdraw, from the leprechaun, missing extras, and last what you carry. */
	private static int rank(Line line)
	{
		if (line.blocksStart())
		{
			return 0;
		}
		switch (line.getStatus())
		{
			case WITHDRAW:
				return 1;
			case FROM_LEPRECHAUN:
				return 2;
			case MISSING:
				return 3;
			default:
				return 4;
		}
	}

	/** "Ready to go", or e.g. "1 missing · 3 to withdraw". */
	private static JLabel summary(List<Line> lines)
	{
		long missing = lines.stream().filter(Line::blocksStart).count();
		long withdraw = lines.stream().filter(l -> l.getToWithdraw() > 0).count();
		long leprechaun = lines.stream().filter(l -> l.getStatus() == Status.FROM_LEPRECHAUN).count();
		if (missing == 0 && withdraw == 0)
		{
			JLabel ready = TabPanel.text(leprechaun == 0 ? "Ready to go." : "Ready to go (" + leprechaun
				+ " from the tool leprechaun at the first stop).");
			ready.setForeground(Palette.FULL);
			return ready;
		}
		StringBuilder text = new StringBuilder();
		if (missing > 0)
		{
			text.append(missing).append(" missing");
		}
		if (withdraw > 0)
		{
			text.append(text.length() > 0 ? " · " : "").append(withdraw).append(" to withdraw");
		}
		JLabel label = TabPanel.text(text.toString());
		label.setForeground(missing > 0 ? Palette.NONE : Palette.PARTIAL);
		return label;
	}

	private static BankView.Row row(Line line)
	{
		Requirement r = line.getRequirement();
		boolean optional = r.getImportance() == Requirement.Importance.RECOMMENDED;
		String detail;
		BankView.Coverage colour;
		switch (line.getStatus())
		{
			case OK:
				detail = "Carried";
				colour = BankView.Coverage.FULL;
				break;
			case FROM_LEPRECHAUN:
				detail = "Take " + line.getFromLeprechaun() + " from the leprechaun";
				colour = BankView.Coverage.FULL;
				break;
			case WITHDRAW:
				detail = "Withdraw " + line.getToWithdraw()
					+ (line.getFromLeprechaun() > 0 ? ", " + line.getFromLeprechaun() + " from leprechaun" : "");
				colour = BankView.Coverage.PARTIAL;
				break;
			default:
				detail = optional ? "Optional: not carried" : "Missing " + line.getShortBy();
				colour = optional ? null : BankView.Coverage.NONE;
				break;
		}
		String tooltip = "For: " + r.why()
			+ "\n" + (optional ? "Recommended, not needed" : "Needed")
			+ "\nCarried " + line.getCarried()
			+ (r.isStoredByLeprechaun() ? "\nThe tool leprechaun can hold it" : "");
		return new BankView.Row(Collections.min(r.getItemIds()), r.getLabel(), String.valueOf(r.getAmount()), detail,
			colour, tooltip, null);
	}
}
