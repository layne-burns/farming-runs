/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.run.CompostChoice;
import com.layneburns.farmingruns.run.CropShare;
import com.layneburns.farmingruns.run.FillOrder;
import com.layneburns.farmingruns.run.KindPlan;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.run.TreeRoutine;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.ui.FontManager;

/**
 * One patch kind in the Run tab (e.g. Herb, 9 patches): Auto (the best seeds you can spare, or for kinds with no
 * buffer, the best you have) or your chosen crops,
 * each with a share or an exact number of patches; whether to pay the gardener; for trees, what to do with the grown
 * tree. Shows what will actually be planted. Every change is passed to {@code onChange} as a new {@link KindPlan}.
 */
class KindCard extends JPanel
{
	/** The other choice for bushes and cacti: dig up the old plant and plant again. */
	private static final String CLEAR_AND_REPLANT = "Clear and replant";

	/**
	 * @param seeds    the seeds of this kind, best first
	 * @param planned  this kind's assignments in the current planting plan
	 * @param patches  patches of this kind the run visits
	 * @param payTooltip what the gardeners want and how much you have; null when no seed of this kind can be
	 *                   protected (then there's no "pay" box)
	 * @param isTree   whether a tree routine applies
	 * @param harvests whether the grown tree has a crop to pick before clearing (fruit trees, calquat, celastrus)
	 * @param regrows  whether the crop grows back, so the plant can be picked and left standing (fruit trees, bushes,
	 *                 cacti)
	 * @param notes    things to know, shown in yellow, e.g. "Grow first: 2 × Magic sapling (from Magic seed)"
	 * @param amulet   allotments: whether the amulet of bounty will be worn (your choice or the default); null for
	 *                 other kinds
	 * @param collapsed which collapsible parts are closed, by key; kept by the Run tab across redraws
	 * @param seedInfo  a seed's tooltip: XP, protection and the goals that use it
	 * @param onLeaveOut leaves this kind out of the run (the × by its heading)
	 * @param runCompost the run's compost, which this kind uses unless it has its own; null when the kind can't take
	 *                   compost (coral), so there's no compost choice
	 * @param buffered   this kind's seeds have some kept back (kinds a farming contract can ask for); without a buffer
	 *                   every seed you have counts, so the card doesn't talk of what you can spare
	 */
	KindCard(String title, KindPlan plan, List<Seed> seeds, List<PlantingPlan.Assignment> planned, int patches,
		@Nullable String payTooltip, boolean isTree, boolean harvests, boolean regrows, List<String> notes, @Nullable Boolean amulet,
		Map<String, Boolean> collapsed, Function<Seed, String> seedInfo, Consumer<KindPlan> onChange,
		Runnable onLeaveOut, @Nullable CompostChoice runCompost, boolean buffered)
	{
		String usable = buffered ? "you can spare" : "you have";
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Palette.SURFACE);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 4, 0, Palette.BACKGROUND),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		setAlignmentX(LEFT_ALIGNMENT);

		JLabel heading = new JLabel(title + " · " + patches + " patch" + (patches == 1 ? "" : "es"));
		heading.setForeground(Palette.TEXT);
		JButton leaveOut = TabPanel.button("×", "Leave " + title.toLowerCase() + " out of this run (the rest of its"
			+ " run type stays). Put it back under Run types.");
		leaveOut.addActionListener(e -> onLeaveOut.run());
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(heading, BorderLayout.CENTER);
		top.add(leaveOut, BorderLayout.EAST);
		add(left(top));

		if (isTree)
		{
			// First for trees, since it decides the rest: a harvest run plants nothing.
			TreeRoutine[] choices = (regrows ? List.of(TreeRoutine.values())
				: List.of(TreeRoutine.PAY_TO_REMOVE, TreeRoutine.CHOP_AND_DIG)).toArray(new TreeRoutine[0]);
			JComboBox<TreeRoutine> routine = new JComboBox<>(choices);
			routine.setSelectedItem(plan.getTreeRoutine());
			routine.setFocusable(false);
			routine.setToolTipText("What to do with a grown tree before replanting. \"Pick, leave it standing\" is a"
				+ " harvest run: the crop is picked and the tree left (choose which types below).");
			routine.addActionListener(e -> onChange.accept(plan.withTreeRoutine((TreeRoutine) routine.getSelectedItem())));
			add(left(routine));
		}
		else if (regrows)
		{
			// Bushes and cacti: dug up to replant, or picked and left to grow more.
			JComboBox<String> routine = new JComboBox<>(new String[]{CLEAR_AND_REPLANT,
				TreeRoutine.LEAVE_STANDING.getTitle()});
			routine.setSelectedIndex(plan.getTreeRoutine() == TreeRoutine.LEAVE_STANDING ? 1 : 0);
			routine.setFocusable(false);
			routine.setToolTipText("\"Pick, leave it standing\" is a harvest run: the crop is picked and the plant left"
				+ " to grow more (choose which types below).");
			routine.addActionListener(e -> onChange.accept(plan.withTreeRoutine(routine.getSelectedIndex() == 1
				? TreeRoutine.LEAVE_STANDING : TreeRoutine.PAY_TO_REMOVE)));
			add(left(routine));
		}
		if ((isTree || regrows) && !(regrows && plan.getTreeRoutine() == TreeRoutine.LEAVE_STANDING))
		{
			JCheckBox same = check("Replant the same crop", plan.isReplantSame());
			same.setToolTipText("<html>Pick the grown crop, clear it and plant the seed that was growing there again"
				+ "<br>(when you have one" + (buffered ? " to spare" : "") + "; otherwise the seed choice below). With \"only plant what I can"
				+ "<br>pay for\", one you can't pay for is just picked and left standing. Clearing it before it's"
				+ "<br>picked is blocked (shift-click to do it anyway).</html>");
			same.addActionListener(e -> onChange.accept(plan.withReplantSame(same.isSelected())));
			add(left(same));
		}
		if (regrows && plan.getTreeRoutine() == TreeRoutine.LEAVE_STANDING)
		{
			if (seeds.size() > 1)
			{
				add(new KeepStandingPanel(plan, seeds, onChange, collapsed, "keep:" + title, seedInfo));
			}
			if (plan.getReplaceStanding().isEmpty())
			{
				add(left(TabPanel.text("Harvest run: pick the crop and leave it standing. Nothing is planted, paid"
					+ " or cleared.")));
				setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
				return;
			}
			// Some types are replaced: the seed choice below is for those patches.
		}

		if (runCompost != null)
		{
			add(left(compostChoice(plan, runCompost, onChange)));
		}

		JCheckBox auto = check("Auto: best seeds " + usable, plan.isAuto());
		auto.setToolTipText("Untick to choose the seeds yourself");
		auto.addActionListener(e ->
		{
			if (auto.isSelected())
			{
				onChange.accept(plan.withAuto(true));
			}
			else
			{
				// Start from what Auto would plant, shared evenly, so there's something to adjust.
				List<CropShare> start = planned.stream().filter(a -> a.getSeed() != null && !a.isContract())
					.map(a -> a.getSeed().getItemId()).distinct().map(CropShare::even).collect(Collectors.toList());
				onChange.accept(plan.withAuto(false).withCrops(plan.getCrops().isEmpty() ? start : plan.getCrops()));
			}
		});
		add(left(auto));

		if (!plan.isAuto())
		{
			JComboBox<FillOrder> order = new JComboBox<>(FillOrder.values());
			order.setSelectedItem(plan.getFillOrder());
			order.setFocusable(false);
			order.setToolTipText("By share: patches split by the numbers below. Most XP first: the highest-XP seed"
				+ " fills patches first, as far as your " + (buffered ? "spare goes" : "seeds go") + ", then the next.");
			order.addActionListener(e -> onChange.accept(plan.withFillOrder((FillOrder) order.getSelectedItem())));
			add(left(order));
			boolean byShare = plan.getFillOrder() != FillOrder.MOST_XP;
			List<Integer> indexes = new ArrayList<>();
			for (int i = 0; i < plan.getCrops().size(); i++)
			{
				indexes.add(i);
			}
			if (!byShare)
			{
				// Listed in the order they fill: most XP first.
				indexes.sort(Comparator.comparingDouble((Integer i) -> -xpOf(plan.getCrops().get(i), seeds)));
			}
			for (int i : indexes)
			{
				add(cropRow(plan, i, seeds, patches, byShare, seedInfo, onChange));
			}
			add(addCrop(plan, seeds, seedInfo, onChange));
		}

		JLabel preview = TabPanel.text(preview(planned));
		preview.setToolTipText("What will be planted, given the seeds " + usable);
		add(left(preview));
		for (String note : notes)
		{
			JLabel label = TabPanel.text(note);
			label.setForeground(Palette.PARTIAL);
			add(left(label));
		}
		if (isTree)
		{
			JCheckBox seedsToo = check("Use seeds too (grow saplings first)", plan.isGrowSaplings());
			seedsToo.setToolTipText("Off: only saplings you've already grown count. On: tree seeds count as well, so"
				+ " the best tree can be picked from seeds; the card lists the saplings to grow before the run.");
			seedsToo.addActionListener(e -> onChange.accept(plan.withGrowSaplings(seedsToo.isSelected())));
			add(left(seedsToo));
		}

		if (payTooltip != null)
		{
			JCheckBox pay = check("Pay the gardener to protect", plan.isPay());
			pay.setToolTipText(payTooltip);
			pay.addActionListener(e -> onChange.accept(plan.withPay(pay.isSelected())));
			add(left(pay));
			if (plan.isPay())
			{
				JCheckBox payable = check("Only plant what I can pay for", plan.isPayableOnly());
				payable.setToolTipText("Plant protectable crops only as far as your payment produce goes (best first);"
					+ " any patches left are planted without protection.");
				payable.addActionListener(e -> onChange.accept(plan.withPayableOnly(payable.isSelected())));
				add(left(payable));
			}
		}
		if (amulet != null)
		{
			JCheckBox bounty = check("Wear an amulet of bounty", amulet);
			bounty.setToolTipText("25% chance to plant with one seed instead of three. "
				+ (plan.getAmuletOfBounty() == null ? "On by default above strawberry (watermelon, snape grass)."
				: "Your choice (the default is on above strawberry)."));
			bounty.addActionListener(e -> onChange.accept(plan.withAmuletOfBounty(bounty.isSelected())));
			add(left(bounty));
		}
		if (harvests)
		{
			JCheckBox harvest = check("Harvest before clearing", !plan.isSkipHarvest());
			harvest.setToolTipText("Pick the crop before paying to remove or chopping the tree");
			harvest.addActionListener(e -> onChange.accept(plan.withSkipHarvest(!harvest.isSelected())));
			add(left(harvest));
		}
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	/** "Ranarr seed   share [1]  patches [-]  ✕" */
	private static JPanel cropRow(KindPlan plan, int index, List<Seed> seeds, int patches, boolean byShare,
		Function<Seed, String> seedInfo, Consumer<KindPlan> onChange)
	{
		CropShare crop = plan.getCrops().get(index);
		Seed seed = seeds.stream().filter(s -> s.getItemId() == crop.getSeedItemId()).findFirst().orElse(null);
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		JLabel label = new JLabel(seed == null ? "?" : seed.getName());
		label.setForeground(Palette.TEXT);
		if (seed != null)
		{
			label.setToolTipText(seedInfo.apply(seed));
		}
		label.setMinimumSize(new Dimension(0, 0));
		label.setPreferredSize(new Dimension(0, label.getPreferredSize().height));

		JPanel numbers = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		numbers.setOpaque(false);
		JSpinner share = spinner(crop.getWeight(), 1, 99, "Share: patches are split in proportion to the shares");
		share.setEnabled(crop.getPatches() == null);
		share.addChangeListener(e -> onChange.accept(replace(plan, index,
			new CropShare(crop.getSeedItemId(), (Integer) share.getValue(), crop.getPatches()))));
		JSpinner exact = spinner(crop.getPatches() == null ? 0 : crop.getPatches(), 0, Math.max(patches, 1),
			"Exact number of patches (0: use the share instead)");
		exact.addChangeListener(e ->
		{
			int value = (Integer) exact.getValue();
			onChange.accept(replace(plan, index, new CropShare(crop.getSeedItemId(), crop.getWeight(),
				value == 0 ? null : value)));
		});
		if (byShare)
		{
			numbers.add(small("share", Palette.SECONDARY_TEXT));
			numbers.add(share);
			numbers.add(small("#", Palette.SECONDARY_TEXT));
			numbers.add(exact);
		}
		numbers.add(link("✕", "Remove this seed", () ->
		{
			List<CropShare> crops = new ArrayList<>(plan.getCrops());
			crops.remove(index);
			onChange.accept(plan.withCrops(List.copyOf(crops)));
		}));
		row.add(label, BorderLayout.CENTER);
		row.add(numbers, BorderLayout.EAST);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static JPanel addCrop(KindPlan plan, List<Seed> seeds, Function<Seed, String> seedInfo,
		Consumer<KindPlan> onChange)
	{
		JPopupMenu menu = new JPopupMenu();
		for (Seed seed : seeds)
		{
			if (plan.getCrops().stream().anyMatch(c -> c.getSeedItemId() == seed.getItemId()))
			{
				continue;
			}
			JMenuItem item = new JMenuItem(seed.getName() + " (level " + seed.getLevel() + ")");
			item.setToolTipText(seedInfo.apply(seed));
			item.addActionListener(e ->
			{
				List<CropShare> crops = new ArrayList<>(plan.getCrops());
				crops.add(CropShare.even(seed.getItemId()));
				onChange.accept(plan.withCrops(List.copyOf(crops)));
			});
			menu.add(item);
		}
		JLabel add = new JLabel("+ Add a seed…");
		add.setForeground(Palette.SECONDARY_TEXT);
		add.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		add.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				menu.show(add, 0, add.getHeight());
			}
		});
		return left(add);
	}

	/** "5 × Ranarr seed, 4 × Snapdragon seed (+ contract)", or what's missing. */
	static String preview(List<PlantingPlan.Assignment> planned)
	{
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, Integer> kept = new LinkedHashMap<>();
		int empty = 0;
		for (PlantingPlan.Assignment a : planned)
		{
			if (a.isKeptStanding())
			{
				kept.merge(a.getStanding() == null ? "tree" : KeepStandingPanel.treeName(a.getStanding()), 1,
					Integer::sum);
			}
			else if (a.getSeed() == null)
			{
				empty++;
			}
			else
			{
				counts.merge(a.getSeed().getName() + (a.isContract() ? " (contract)" : ""), 1, Integer::sum);
			}
		}
		List<String> parts = counts.entrySet().stream().map(e -> e.getValue() + " × " + e.getKey())
			.collect(Collectors.toList());
		if (empty > 0)
		{
			parts.add(empty + " with no seed");
		}
		if (!kept.isEmpty())
		{
			parts.add("left standing: " + kept.entrySet().stream().map(e -> e.getValue() + " " + e.getKey())
				.collect(Collectors.joining(", ")));
		}
		return parts.isEmpty() ? "Nothing to plant" : String.join(", ", parts);
	}

	private static double xpOf(CropShare crop, List<Seed> seeds)
	{
		return seeds.stream().filter(s -> s.getItemId() == crop.getSeedItemId()).mapToDouble(FillOrder::xpScore)
			.findFirst().orElse(0);
	}

	private static KindPlan replace(KindPlan plan, int index, CropShare crop)
	{
		List<CropShare> crops = new ArrayList<>(plan.getCrops());
		crops.set(index, crop);
		return plan.withCrops(List.copyOf(crops));
	}

	private static JSpinner spinner(int value, int min, int max, String tooltip)
	{
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(Math.max(min, Math.min(max, value)), min, max, 1));
		spinner.setToolTipText(tooltip);
		spinner.setPreferredSize(new Dimension(42, spinner.getPreferredSize().height));
		return spinner;
	}

	private static JCheckBox check(String text, boolean selected)
	{
		JCheckBox box = new JCheckBox(text, selected);
		box.setForeground(Palette.TEXT);
		box.setOpaque(false);
		box.setFocusable(false);
		return box;
	}

	private static JLabel small(String text, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		return label;
	}

	private static JLabel link(String text, String tooltip, Runnable onClick)
	{
		JLabel label = new JLabel(text);
		label.setForeground(Palette.SECONDARY_TEXT);
		label.setToolTipText(tooltip);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}
		});
		return label;
	}

	/** Wraps a component so it sits left-aligned in the card's vertical stack. */
	/** "Compost:" and a choice: the run's (the default), or this kind's own, e.g. none for allotments. */
	private static JPanel compostChoice(KindPlan plan, CompostChoice runCompost, Consumer<KindPlan> onChange)
	{
		List<String> titles = new ArrayList<>();
		titles.add("Run's (" + runCompost.getTitle() + ")");
		for (CompostChoice choice : CompostChoice.values())
		{
			titles.add(choice.getTitle());
		}
		JComboBox<String> box = new JComboBox<>(titles.toArray(new String[0]));
		box.setSelectedIndex(plan.getCompost() == null ? 0 : plan.getCompost().ordinal() + 1);
		box.setFocusable(false);
		box.setToolTipText("Compost for this kind's patches; the run's choice unless you pick another");
		box.addActionListener(e -> onChange.accept(plan.withCompost(box.getSelectedIndex() == 0 ? null
			: CompostChoice.values()[box.getSelectedIndex() - 1])));
		JLabel label = new JLabel("Compost: ");
		label.setForeground(Palette.SECONDARY_TEXT);
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(label, BorderLayout.WEST);
		row.add(box, BorderLayout.CENTER);
		return row;
	}

	private static JPanel left(Component component)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.setAlignmentX(LEFT_ALIGNMENT);
		wrapper.add(component, BorderLayout.WEST);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapper.getPreferredSize().height));
		return wrapper;
	}
}
