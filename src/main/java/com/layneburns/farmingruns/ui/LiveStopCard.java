/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.ItemAmount;
import com.layneburns.farmingruns.data.TeleportChoice;
import com.layneburns.farmingruns.run.CompostChoice;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.run.RunProgress;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The Live tab's card for a stop: where, how to get there, and each patch's steps (compost, plant, pay, or pick and
 * leave), ticked as the game confirms them. Also the one-line "Next" summary.
 */
final class LiveStopCard
{
	/** What the card needs to know about a patch beyond the run's progress. */
	interface PatchFacts
	{
		boolean composted(String patchId);

		/** The gardener is to be paid for this patch (it may already have been). */
		boolean pays(PlantingPlan.Assignment assignment);
	}

	private LiveStopCard()
	{
	}

	/** The current stop: heading, teleport, and a checklist per patch. */
	static JPanel now(String locationName, @Nullable TeleportChoice teleport, RunProgress.Stop stop,
		RunProgress progress, CompostChoice compost, PatchFacts facts, List<String> fromLeprechaun)
	{
		JPanel card = card();
		JLabel heading = new JLabel("▶ Now: " + locationName);
		heading.setForeground(Palette.ACCENT);
		card.add(heading);
		card.add(detail(teleport == null ? "Walk there" : "via " + teleport.displayName()));
		if (!fromLeprechaun.isEmpty())
		{
			JLabel take = TabPanel.text("⚠ Take from the leprechaun: " + String.join(", ", fromLeprechaun));
			take.setForeground(Palette.PARTIAL);
			card.add(take);
		}
		for (PlantingPlan.Assignment a : stop.getPatches())
		{
			JLabel patch = new JLabel(patchName(a));
			patch.setForeground(Palette.TEXT);
			patch.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
			card.add(patch);
			String id = a.getPatch().getId();
			boolean worked = progress.getWorkedPatches().contains(id);
			if (a.isKeptStanding())
			{
				card.add(step("Pick the crop, leave the tree", worked));
				continue;
			}
			if (a.getSeed() == null)
			{
				card.add(step("Harvest and clear (nothing to plant)", worked));
				continue;
			}
			if (compost == CompostChoice.FERTILE_SOIL)
			{
				card.add(step("Cast Fertile Soil", null));
			}
			else if (compost != CompostChoice.NONE)
			{
				card.add(step(compost.getTitle(), facts.composted(id)));
			}
			card.add(step("Plant " + (a.getSeed().isPlantedAsSapling() ? a.getSeed().getSaplingName()
				: a.getSeed().getName()), worked));
			if (facts.pays(a))
			{
				card.add(step("Pay " + payment(a), worked && !progress.isAwaitingPayment(id)));
			}
		}
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/** The stop after this one, in a line or two: where, how, and what's there. */
	static JPanel next(String locationName, @Nullable TeleportChoice teleport, RunProgress.Stop stop,
		Predicate<PlantingPlan.Assignment> pays)
	{
		JPanel card = card();
		JLabel heading = new JLabel("Next: " + locationName);
		heading.setForeground(Palette.TEXT);
		card.add(heading);
		card.add(detail((teleport == null ? "Walk there" : "via " + teleport.displayName()) + " · "
			+ stop.getPatches().stream().map(a -> kind(a) + (a.isKeptStanding() ? ": pick"
				: a.getSeed() == null ? ": harvest" : pays.test(a) ? ": plant + pay" : ": plant"))
				.collect(Collectors.joining(", "))));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/** "✓ Plant Ranarr seed" in green when done, "☐ ..." when not; "• ..." when it can't be told. */
	private static JLabel step(String text, @Nullable Boolean done)
	{
		String mark = done == null ? "•" : done ? "✓" : "☐";
		JLabel label = new JLabel("   " + mark + " " + text);
		label.setForeground(done != null && done ? Palette.FULL : Palette.TEXT);
		label.setMinimumSize(new Dimension(0, 0));
		return label;
	}

	private static String payment(PlantingPlan.Assignment a)
	{
		List<String> parts = new ArrayList<>();
		for (ItemAmount p : a.getSeed().getPayment())
		{
			parts.add(p.getAmount() + " × " + p.getName());
		}
		return String.join(" + ", parts);
	}

	private static String patchName(PlantingPlan.Assignment a)
	{
		String name = a.getPatch().getName();
		String kind = kind(a);
		return Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + (name.isEmpty() ? "" : " (" + name + ")");
	}

	private static String kind(PlantingPlan.Assignment a)
	{
		return a.getPatch().getKind().name().toLowerCase().replace('_', ' ');
	}

	private static JLabel detail(String text)
	{
		JLabel label = TabPanel.text(text);
		label.setForeground(Palette.SECONDARY_TEXT);
		return label;
	}

	private static JPanel card()
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(Palette.SURFACE);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 6, 0, Palette.BACKGROUND),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		card.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		return card;
	}
}
