/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.FarmingRunsConfig;
import com.layneburns.farmingruns.MakingMode;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Pack;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.inventory.PaymentKit;
import com.layneburns.farmingruns.inventory.RunCheckService;
import com.layneburns.farmingruns.state.ItemStore;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;

/**
 * Making tab: things to make at the bank apart from a run, one mode at a time. Sapling making pots every tree seed
 * you have (runs grow the saplings they need by themselves); payment making packs fruit into baskets and vegetables
 * into sacks. While one is on, the bank's search button ("Sapling Items", "Payment Items"), the bank tab and the
 * highlights show what it takes, and the run's own items step aside. The kits are worked out with the run check
 * ({@link RunCheckService#saplingKit()}, {@link RunCheckService#paymentPlan()}). Swing thread.
 */
@Singleton
public class MakingTab extends TabPanel
{
	/** Width tick box labels wrap at, less than {@link TabPanel#text} for the box itself. */
	private static final int BOX_WRAP_WIDTH = 155;

	private final RunCheckService checks;
	private final FarmingRunsConfig config;
	private final ConfigManager configManager;
	private final RunTab runTab;
	private final BankRunTab bankRunTab;
	private final GameData data;
	private final FitWidthPanel content = new FitWidthPanel();
	private final AtomicBoolean redrawQueued = new AtomicBoolean();

	@Inject
	MakingTab(RunCheckService checks, FarmingRunsConfig config, ConfigManager configManager, RunTab runTab,
		BankRunTab bankRunTab, ItemStore items, GameData data)
	{
		this.checks = checks;
		this.config = config;
		this.configManager = configManager;
		this.runTab = runTab;
		this.bankRunTab = bankRunTab;
		this.data = data;
		add(scrollPane(content), BorderLayout.CENTER);
		checks.addListener(this::refresh);
		items.addListener(this::refresh);
		refresh();
	}

	/** Redraws soon, on the Swing thread. Safe to call from any thread. */
	public void refresh()
	{
		if (redrawQueued.compareAndSet(false, true))
		{
			SwingUtilities.invokeLater(this::redraw);
		}
	}

	/** Leaves an item out of a mode (or puts it back), and works the kit out again. */
	private void setSkipped(String key, Set<String> current, String id, boolean skip)
	{
		Set<String> skipped = new TreeSet<>(current);
		if (skip)
		{
			skipped.add(id);
		}
		else
		{
			skipped.remove(id);
		}
		configManager.setConfiguration(FarmingRunsConfig.GROUP, key, String.join(",", skipped));
		runTab.refresh();
		refresh();
	}

	private void redraw()
	{
		redrawQueued.set(false);
		content.removeAll();
		MakingMode mode = config.makingMode();
		SectionPanel section = section("Making");
		JComboBox<MakingMode> chooser = new JComboBox<>(MakingMode.values());
		chooser.setSelectedItem(mode);
		chooser.setFocusable(false);
		chooser.addActionListener(e ->
		{
			configManager.setConfiguration(FarmingRunsConfig.GROUP, FarmingRunsConfig.MAKING_MODE,
				chooser.getSelectedItem());
			// The run check works the kit out; then this redraws with it.
			runTab.refresh();
			refresh();
		});
		section.addRow(fill(chooser));
		section.addRow(text(describe(mode)));
		content.add(section);
		if (mode == MakingMode.SAPLINGS)
		{
			addSaplings();
		}
		else if (mode == MakingMode.PAYMENTS)
		{
			addPayments();
		}
		revalidate();
		repaint();
	}

	private static String describe(MakingMode mode)
	{
		switch (mode)
		{
			case SAPLINGS:
				return "Pot every tree seed you have. The bank's search button reads \"Sapling Items\" and brings up the"
					+ " seeds, a filled plant pot for each, a trowel and 3 watering cans; the seed vault shows just those"
					+ " seeds.";
			case PAYMENTS:
				return "Pack fruit into baskets and vegetables into sacks for gardeners' payments: as many as your bank"
					+ " and inventory make. The bank's search button reads \"Payment Items\" and brings up one inventory's"
					+ " worth at a time; bank the full ones (outlined in red) and take the next.";
			default:
				return "Make things at the bank apart from a run. While a mode is on, the bank's search button, the bank"
					+ " tab and the highlights show what it takes instead of the run's items.";
		}
	}

	private void addSaplings()
	{
		// A tick box per tree seed you have: unticked ones are left out (e.g. oak, until you want oak saplings).
		Map<Seed, Integer> held = checks.saplingSeedsHeld();
		if (!held.isEmpty())
		{
			SectionPanel seeds = section("Seeds to pot");
			Set<String> skipped = checks.saplingSkipped();
			held.forEach((seed, count) ->
			{
				String id = String.valueOf(seed.getItemId());
				JCheckBox box = tickBox(count + " × " + seed.getName(), !skipped.contains(id));
				box.addActionListener(e -> setSkipped(FarmingRunsConfig.SAPLING_SKIPPED, checks.saplingSkipped(), id,
					!box.isSelected()));
				seeds.addRow(box);
			});
			content.add(seeds);
		}

		SectionPanel kitSection = section("To take");
		List<String> kit = checks.saplingKit();
		kitSection.addRow(text(kit.isEmpty() ? "No tree seeds to pot." : String.join("<br>", kit)));
		if (!kit.isEmpty())
		{
			kitSection.addRow(showInBank("Opens a bank tab with just the sapling items"));
		}
		content.add(kitSection);
	}

	private void addPayments()
	{
		// A tick box per payment, with what it pays for: unticked ones are left out.
		PaymentKit.Plan plan = checks.paymentPlan();
		SectionPanel packs = section("Payments to make");
		Set<String> skipped = checks.paymentSkipped();
		for (Pack pack : data.getItems().packs())
		{
			String id = String.valueOf(pack.fullId());
			Integer made = plan.getMade().get(pack);
			String label = (made == null ? "" : made + " × ") + pack.getName() + " (for "
				+ String.join(", ", PaymentKit.paysFor(pack, data.seeds())) + ")";
			JCheckBox box = tickBox(label, !skipped.contains(id));
			box.addActionListener(e -> setSkipped(FarmingRunsConfig.PAYMENT_SKIPPED, checks.paymentSkipped(), id,
				!box.isSelected()));
			packs.addRow(box);
		}
		content.add(packs);

		SectionPanel trip = section("This trip");
		List<String> taking = checks.paymentTrip();
		int total = plan.getMade().values().stream().mapToInt(Integer::intValue).sum();
		if (taking.isEmpty())
		{
			trip.addRow(text("Nothing to pack: no loose fruit or vegetables, with a basket or sack for them, in your"
				+ " bank or inventory."));
		}
		else
		{
			trip.addRow(text(String.join("<br>", taking)));
			trip.addRow(text(total + " to make in all."));
		}
		plan.getContainersShort().forEach((container, count) ->
		{
			JLabel label = text(count + " more " + container.toLowerCase() + (count == 1 ? "" : "s")
				+ " would pack the rest.");
			label.setForeground(Palette.PARTIAL);
			trip.addRow(label);
		});
		if (!taking.isEmpty())
		{
			trip.addRow(showInBank("Opens a bank tab with just this trip's baskets, sacks and produce"));
		}
		content.add(trip);
	}

	private JPanel showInBank(String tooltip)
	{
		JButton show = button("Show in bank", tooltip);
		show.addActionListener(e -> bankRunTab.show());
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
		row.setOpaque(false);
		row.add(show);
		return row;
	}

	private static JCheckBox tickBox(String label, boolean selected)
	{
		JCheckBox box = new JCheckBox("<html><div style='width:" + BOX_WRAP_WIDTH + "px'>" + label + "</div></html>",
			selected);
		box.setFocusable(false);
		box.setOpaque(false);
		return box;
	}

	private static SectionPanel section(String title)
	{
		return new SectionPanel(title, false, state ->
		{
		});
	}
}
