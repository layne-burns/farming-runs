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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JLabel;
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

	static void fill(SectionPanel section, Check check, ItemManager itemManager)
	{
		List<Line> lines = check.getLines();
		if (lines.isEmpty())
		{
			section.addRow(TabPanel.text("Nothing to plant or visit yet."));
			return;
		}
		section.addRow(summary(lines));
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
			text.append(text.length() > 0 ? " · " : "").append(withdraw).append(" to withdraw (highlighted in the bank)");
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
