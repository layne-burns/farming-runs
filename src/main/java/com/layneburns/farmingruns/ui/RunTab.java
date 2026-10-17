/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.GoalPreset;
import com.layneburns.farmingruns.data.Patch;
import com.layneburns.farmingruns.data.RunType;
import com.layneburns.farmingruns.data.Seed;
import com.layneburns.farmingruns.inventory.RunCheckService;
import com.layneburns.farmingruns.run.BountyAmulet;
import com.layneburns.farmingruns.run.CompostChoice;
import com.layneburns.farmingruns.run.GoalSetups;
import com.layneburns.farmingruns.run.KindPlan;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.run.PresetCombiner;
import com.layneburns.farmingruns.run.Protection;
import com.layneburns.farmingruns.run.RunPlanService;
import com.layneburns.farmingruns.pathing.ShortestPathClient;
import com.layneburns.farmingruns.run.RunController;
import com.layneburns.farmingruns.run.RunProgress;
import com.layneburns.farmingruns.run.RunSession;
import com.layneburns.farmingruns.run.RunSetup;
import com.layneburns.farmingruns.run.TreeRoutine;
import com.layneburns.farmingruns.state.ContractService;
import com.layneburns.farmingruns.state.ItemStore;
import com.layneburns.farmingruns.state.PatchStates;
import com.layneburns.farmingruns.state.PlayerInfo;
import com.layneburns.farmingruns.state.SetupStore;
import com.layneburns.farmingruns.state.UnlockService;
import com.layneburns.farmingruns.vendor.timetracking.PatchImplementation;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;

/**
 * Run tab (SPEC §2.3): presets at the top, then the run types, a card per patch kind (seeds, payment, tree routine),
 * compost, patches to skip, and what the plan comes to. Every edit becomes a new {@link RunSetup}, saved by
 * {@link SetupStore}; the plan is worked out by {@link RunPlanService}. Patches still growing are left out until
 * Start asks about them; the Inventory section checks your items for the rest, and Start runs {@link StartFlow}.
 */
@Singleton
public class RunTab extends TabPanel
{
	private final GameData data;
	private final SetupStore setups;
	private final RunPlanService planner;
	private final UnlockService unlocks;
	private final PlayerInfo player;
	private final ItemStore items;
	private final RunCheckService checks;
	private final RunSession session;
	private final RunController controller;
	private final ShortestPathClient shortestPath;
	private final ItemManager itemManager;
	private final BankRunTab bankRunTab;

	private final FitWidthPanel content = new FitWidthPanel();
	/** Goals and presets ticked (see PresetsPanel's keys), in the order ticked: the first one's compost wins. */
	private final Set<String> ticked = new LinkedHashSet<>();
	private final Map<String, Boolean> collapsed = new HashMap<>(Map.of("Patches to visit", true));
	private final AtomicBoolean redrawQueued = new AtomicBoolean();

	@Inject
	RunTab(GameData data, SetupStore setups, RunPlanService planner, UnlockService unlocks, ItemStore items,
		PlayerInfo player, ContractService contract, RunCheckService checks, RunSession session, PatchStates states,
		ItemManager itemManager, BankRunTab bankRunTab, RunController controller, ShortestPathClient shortestPath)
	{
		this.data = data;
		this.setups = setups;
		this.planner = planner;
		this.unlocks = unlocks;
		this.player = player;
		this.items = items;
		this.checks = checks;
		this.session = session;
		this.controller = controller;
		this.shortestPath = shortestPath;
		this.itemManager = itemManager;
		this.bankRunTab = bankRunTab;
		add(scrollPane(content), BorderLayout.CENTER);
		// Redraw when the setup, your items, unlocks, level, contract, patch states or the run change.
		setups.addListener(this::refresh);
		items.addListener(this::refresh);
		unlocks.addListener(this::refresh);
		player.addListener(this::refresh);
		contract.addListener(this::refresh);
		states.addListener(this::refresh);
		session.addListener(this::refresh);
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

	private void update(UnaryOperator<RunSetup> change)
	{
		setups.setCurrent(change.apply(setups.current()));
	}

	private void redraw()
	{
		redrawQueued.set(false);
		RunSetup setup = setups.current();
		long now = System.currentTimeMillis() / 1000;
		int growing = checks.notReady(setup, now).size();
		RunCheckService.Check check = checks.check(setup, checks.readyPatches(setup, now), now);
		PlantingPlan plan = check.getPlan();
		content.removeAll();

		RunSession.ActiveRun active = session.active();
		if (active != null)
		{
			RunProgress progress = active.getProgress();
			JLabel running = text(progress.isFinished() ? "Run complete: see the Live tab."
				: "Run in progress (" + progress.doneCount() + " of " + progress.getStops().size()
				+ " stops): see the Live tab.");
			running.setForeground(Palette.ACCENT);
			running.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
			content.add(running);
		}

		SectionPanel presets = section("Presets");
		presets.addRow(new PresetsPanel(data.goals(), setups.presets().keySet(), ticked, player.getFarmingLevel(),
			this::tick, this::deletePreset, this::savePreset));
		content.add(presets);

		SectionPanel types = section("Run types");
		types.addRow(runTypes(setup));
		content.add(types);

		Map<Seed, List<String>> goalsBySeed = new HashMap<>();
		data.goals().forEach(g -> g.getCrops().forEach(c -> goalsBySeed.computeIfAbsent(c.getSeed(),
			k -> new ArrayList<>()).add(g.getName())));
		Function<Seed, String> seedInfo = seed -> BankTooltips.html(seed.getName(),
			BankTooltips.seedChoice(seed, goalsBySeed.getOrDefault(seed, List.of())));
		for (PatchImplementation kind : kindsIn(setup))
		{
			List<PlantingPlan.Assignment> planned = plan.getAssignments().stream()
				.filter(a -> a.getPatch().getKind() == kind).collect(Collectors.toList());
			List<Seed> seeds = new ArrayList<>(data.seedsFor(kind));
			Collections.reverse(seeds);
			boolean canPay = seeds.stream().anyMatch(Seed::canBeProtected);
			boolean isTree = seeds.stream().anyMatch(Seed::isPlantedAsSapling);
			content.add(new KindCard(BankViewBuilder.kindName(kind), setup.kindPlan(kind), seeds, planned,
				planned.size(), canPay ? PaymentTooltip.build(planned, items.ledger(),
					p -> Protection.isDiseaseFree(p, data, player.getFacts())) : null, isTree,
				TreeRoutine.hasHarvest(kind), kindNotes(setup, plan, kind, planned),
				kind == PatchImplementation.ALLOTMENT ? BountyAmulet.wanted(setup.kindPlan(kind), planned, data) : null,
				collapsed, seedInfo, kp -> update(s -> s.withKindPlan(kind, kp))));
		}

		SectionPanel compost = section("Compost");
		JComboBox<CompostChoice> compostBox = new JComboBox<>(CompostChoice.values());
		compostBox.setSelectedItem(setup.getCompost());
		compostBox.setFocusable(false);
		compostBox.addActionListener(e -> update(s -> s.withCompost((CompostChoice) compostBox.getSelectedItem())));
		compost.addRow(fill(compostBox));
		content.add(compost);

		if (!setup.getRunTypes().isEmpty())
		{
			content.add(skipList(setup));
			content.add(summary(plan, growing));
			SectionPanel inventory = section("Inventory");
			CheckSection.fill(inventory, check, itemManager, bankRunTab::show);
			content.add(inventory);
		}

		JButton start = button("Start run", "Asks about patches that aren't ready, checks your items, then starts");
		start.setEnabled(active == null && !setup.getRunTypes().isEmpty());
		start.addActionListener(e -> StartFlow.start(this, setups.current(), checks, controller, shortestPath,
			data));
		JPanel startRow = new JPanel(new BorderLayout());
		startRow.setBackground(Palette.BACKGROUND);
		startRow.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
		startRow.setAlignmentX(LEFT_ALIGNMENT);
		startRow.add(start, BorderLayout.CENTER);
		startRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		content.add(startRow);

		revalidate();
		repaint();
	}

	/** A tick box per run type, two per row. */
	private JPanel runTypes(RunSetup setup)
	{
		JPanel grid = new JPanel(new GridLayout(0, 2));
		grid.setBackground(Palette.BACKGROUND);
		grid.setAlignmentX(LEFT_ALIGNMENT);
		for (RunType type : RunType.values())
		{
			JCheckBox box = new JCheckBox(type.getTitle(), setup.getRunTypes().contains(type));
			box.setForeground(Palette.TEXT);
			box.setBackground(Palette.BACKGROUND);
			box.setFocusable(false);
			box.addActionListener(e -> update(s ->
			{
				Set<RunType> chosen = s.getRunTypes().isEmpty() ? EnumSet.noneOf(RunType.class) : EnumSet.copyOf(s.getRunTypes());
				if (box.isSelected())
				{
					chosen.add(type);
				}
				else
				{
					chosen.remove(type);
				}
				return s.withRunTypes(Set.copyOf(chosen));
			}));
			grid.add(box);
		}
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		return grid;
	}

	/** Kinds the chosen run types cover that have usable patches, in run-type order. */
	private List<PatchImplementation> kindsIn(RunSetup setup)
	{
		List<PatchImplementation> kinds = new ArrayList<>();
		for (RunType type : RunType.values())
		{
			if (!setup.getRunTypes().contains(type))
			{
				continue;
			}
			type.getKinds().stream()
				.filter(k -> !planner.usablePatches(k).isEmpty() && !kinds.contains(k))
				.sorted()
				.forEach(kinds::add);
		}
		return kinds;
	}

	/** "Patches to visit": a tick box per usable patch of the chosen kinds; untick to skip it. */
	private SectionPanel skipList(RunSetup setup)
	{
		SectionPanel section = section("Patches to visit");
		for (PatchImplementation kind : kindsIn(setup))
		{
			section.addHeading(BankViewBuilder.kindName(kind));
			for (Patch patch : planner.usablePatches(kind))
			{
				String name = data.location(patch.getLocationId()).getName()
					+ (patch.getName().isEmpty() ? "" : " (" + patch.getName() + ")");
				JCheckBox box = new JCheckBox(name, !setup.getSkippedPatches().contains(patch.getId()));
				box.setForeground(Palette.TEXT);
				box.setBackground(Palette.BACKGROUND);
				box.setFocusable(false);
				box.addActionListener(e -> update(s -> s.withSkipped(patch.getId(), !box.isSelected())));
				section.addRow(fill(box));
			}
		}
		return section;
	}

	/**
	 * Yellow notes for a kind's card: saplings to grow first, and on a harvest run that replaces some types, patches
	 * Time Tracking hasn't seen (their tree isn't known, so they're left standing).
	 */
	private List<String> kindNotes(RunSetup setup, PlantingPlan plan, PatchImplementation kind,
		List<PlantingPlan.Assignment> planned)
	{
		List<String> notes = new ArrayList<>();
		List<String> grow = plan.growNotes(kind);
		if (!grow.isEmpty())
		{
			notes.add("Grow first: " + String.join(", ", grow));
		}
		KindPlan kindPlan = setup.kindPlan(kind);
		if (kindPlan.harvestOnly(kind) && !kindPlan.getReplaceStanding().isEmpty())
		{
			long unseen = planned.stream().filter(a -> a.isKeptStanding() && a.getStanding() == null).count();
			if (unseen > 0)
			{
				notes.add(unseen + (unseen == 1 ? " patch" : " patches") + " not seen by Time Tracking, so left standing");
			}
		}
		return notes;
	}

	/** What the plan comes to: patches, contract, and anything to know. */
	private SectionPanel summary(PlantingPlan plan, int growing)
	{
		SectionPanel section = section("Plan");
		long kept = plan.keptCount();
		section.addRow(text(plan.plantedCount() + " of " + plan.getAssignments().size() + " patches get a seed"
			+ (kept > 0 ? "; " + kept + " tree" + (kept == 1 ? "" : "s") + " picked and left standing." : ".")));
		if (growing > 0)
		{
			section.addRow(text(growing + " patch" + (growing == 1 ? " is" : "es are") + " still growing and left out;"
				+ " Start asks whether to visit " + (growing == 1 ? "it" : "them") + " anyway."));
		}
		String contract = planner.contractNote();
		if (contract != null)
		{
			section.addRow(text("Farming contract: " + contract + "."));
		}
		for (String warning : plan.getWarnings())
		{
			JLabel label = text("⚠ " + warning);
			label.setForeground(Palette.PARTIAL);
			section.addRow(label);
		}
		return section;
	}

	private void tick(String name, boolean on)
	{
		if (on)
		{
			ticked.add(name);
		}
		else
		{
			ticked.remove(name);
		}
		List<RunSetup> chosen = ticked.stream().map(this::setupFor).filter(p -> p != null).collect(Collectors.toList());
		if (chosen.isEmpty())
		{
			refresh();
			return;
		}
		setups.setCurrent(PresetCombiner.combine(chosen));
	}

	/** The setup for a ticked key: a goal turned into a setup, or a saved preset; null if it's gone. */
	private RunSetup setupFor(String key)
	{
		for (GoalPreset goal : data.goals())
		{
			if (PresetsPanel.goalKey(goal.getId()).equals(key))
			{
				return GoalSetups.toSetup(goal);
			}
		}
		return setups.presets().entrySet().stream().filter(e -> PresetsPanel.presetKey(e.getKey()).equals(key))
			.map(Map.Entry::getValue).findFirst().orElse(null);
	}

	private void savePreset()
	{
		String name = JOptionPane.showInputDialog(this, "Name for this preset:", "Save preset", JOptionPane.PLAIN_MESSAGE);
		if (name == null || name.isBlank())
		{
			return;
		}
		name = name.trim();
		if (setups.presets().containsKey(name) && JOptionPane.showConfirmDialog(this, "Replace the preset \"" + name
			+ "\"?", "Save preset", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
		{
			return;
		}
		setups.savePreset(name, setups.current());
	}

	private void deletePreset(String name)
	{
		if (JOptionPane.showConfirmDialog(this, "Delete the preset \"" + name + "\"?", "Delete preset",
			JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
		{
			ticked.remove(PresetsPanel.presetKey(name));
			setups.deletePreset(name);
		}
	}

	private SectionPanel section(String title)
	{
		return new SectionPanel(title, collapsed.getOrDefault(title, false), state -> collapsed.put(title, state));
	}

	private static JPanel fill(Component component)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(Palette.BACKGROUND);
		wrapper.setAlignmentX(LEFT_ALIGNMENT);
		wrapper.add(component, BorderLayout.CENTER);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapper.getPreferredSize().height));
		return wrapper;
	}
}
