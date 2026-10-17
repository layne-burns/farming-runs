/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.inventory.InventoryChecker.Line;
import com.layneburns.farmingruns.inventory.RunCheckService;
import com.layneburns.farmingruns.inventory.RunCheckService.Check;
import com.layneburns.farmingruns.pathing.ShortestPathClient;
import com.layneburns.farmingruns.run.RunController;
import com.layneburns.farmingruns.run.RunSetup;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * What happens when you press Start (SPEC §2.4): ask which not-ready patches to visit anyway, check your items, and if
 * something you need is missing or still in the bank, ask "Start anyway" or "Cancel". Then the run starts. Swing
 * thread.
 */
final class StartFlow
{
	private static final String START_ANYWAY = "Start anyway";
	private static final String CANCEL = "Cancel";

	private StartFlow()
	{
	}

	static void start(Component parent, RunSetup setup, RunCheckService checks, RunController controller,
		ShortestPathClient shortestPath, GameData data)
	{
		long now = System.currentTimeMillis() / 1000;
		List<Patch> patches = new ArrayList<>(checks.readyPatches(setup, now));
		List<Patch> growing = checks.notReady(setup, now);
		if (!growing.isEmpty())
		{
			List<Patch> anyway = askNotReady(parent, growing, checks, data, now);
			if (anyway == null)
			{
				return;
			}
			patches.addAll(anyway);
		}
		if (patches.isEmpty())
		{
			JOptionPane.showMessageDialog(parent, "There are no patches to visit.", "Start run",
				JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Check check = checks.check(setup, patches, now);
		if ((check.hasMissing() || check.hasWithdrawals()) && !askStartAnyway(parent, check))
		{
			return;
		}
		if (!shortestPath.isAvailable())
		{
			JOptionPane.showMessageDialog(parent, "The Shortest Path plugin isn't on, so no path will be drawn. The run"
				+ " still tracks your stops; turn on Shortest Path (Plugin Hub) for directions.", "Start run",
				JOptionPane.INFORMATION_MESSAGE);
		}
		controller.start(setup, check.getPlan(), check.getRequirements().getTeleports());
	}

	/** Ticks for each patch still growing, all off. Null when cancelled, else the ones ticked. */
	private static List<Patch> askNotReady(Component parent, List<Patch> growing, RunCheckService checks,
		GameData data, long now)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(new JLabel("These patches aren't ready yet. Tick any to visit anyway:"));
		Map<JCheckBox, Patch> boxes = new LinkedHashMap<>();
		for (Patch patch : growing)
		{
			String name = data.location(patch.getLocationId()).getName() + " "
				+ patch.getKind().name().toLowerCase().replace('_', ' ')
				+ (patch.getName().isEmpty() ? "" : " (" + patch.getName() + ")");
			JCheckBox box = new JCheckBox(name + ": " + checks.status(patch, now).describe(now));
			box.setFocusable(false);
			boxes.put(box, patch);
			panel.add(box);
		}
		int answer = JOptionPane.showConfirmDialog(parent, panel, "Not ready yet", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
		{
			return null;
		}
		return boxes.entrySet().stream().filter(e -> e.getKey().isSelected()).map(Map.Entry::getValue)
			.collect(Collectors.toList());
	}

	/** Lists what's missing and what's still in the bank; true for "Start anyway". */
	private static boolean askStartAnyway(Component parent, Check check)
	{
		StringBuilder html = new StringBuilder("<html>");
		List<Line> missing = check.getLines().stream().filter(Line::blocksStart).collect(Collectors.toList());
		if (!missing.isEmpty())
		{
			html.append("<b>Missing</b>");
			missing.forEach(l -> html.append("<br>").append(l.getShortBy()).append(" × ")
				.append(l.getRequirement().getLabel()).append(" <i>(").append(l.getRequirement().why()).append(")</i>"));
		}
		List<Line> bank = check.getLines().stream().filter(l -> l.getToWithdraw() > 0).collect(Collectors.toList());
		if (!bank.isEmpty())
		{
			html.append(missing.isEmpty() ? "" : "<br><br>").append("<b>Still in the bank</b> (highlighted there)");
			bank.forEach(l -> html.append("<br>").append(l.getToWithdraw()).append(" × ")
				.append(l.getRequirement().getLabel()));
		}
		check.getRequirements().getNotes().forEach(n -> html.append("<br><br>⚠ ").append(n));
		html.append("</html>");
		Object[] options = {START_ANYWAY, CANCEL};
		int answer = JOptionPane.showOptionDialog(parent, html.toString(), "Inventory check", JOptionPane.DEFAULT_OPTION,
			JOptionPane.WARNING_MESSAGE, null, options, CANCEL);
		return answer == 0;
	}
}
