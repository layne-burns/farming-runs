/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Settings shown in RuneLite's configuration panel. Everyday choices (seeds, skips, payment, presets) live in
 * the side panel instead; this holds the rarely changed ones.
 */
@ConfigGroup(FarmingRunsConfig.GROUP)
public interface FarmingRunsConfig extends Config
{
	String GROUP = "farmingruns";
	String READY_ON_THE_WAY = "readyOnTheWay";
	/** Replaced by {@link #MAKING_MODE}; read once to carry an old setting over. */
	String SAPLING_MODE = "saplingMode";
	String MAKING_MODE = "makingMode";
	String SAPLING_SKIPPED = "saplingSkipped";
	String PAYMENT_SKIPPED = "paymentSkipped";

	@ConfigSection(
		name = "Seed buffers",
		description = "Seeds kept back for farming contracts and retries: never planted automatically, not counted as spare",
		position = 0
	)
	String buffers = "buffers";

	@ConfigItem(
		keyName = "allotmentBuffer",
		name = "Allotment seeds",
		description = "Allotment seeds of each type to keep back",
		position = 1,
		section = buffers
	)
	@Range(max = 1000)
	default int allotmentBuffer()
	{
		return 9;
	}

	@ConfigItem(
		keyName = "saplingBuffer",
		name = "Saplings",
		description = "Saplings of each tree type to keep back (and tree seeds, when a tree card grows saplings from seed)",
		position = 2,
		section = buffers
	)
	@Range(max = 1000)
	default int saplingBuffer()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "otherBuffer",
		name = "Other seeds",
		description = "Seeds of each other type (herbs, flowers, hops...) to keep back",
		position = 3,
		section = buffers
	)
	@Range(max = 1000)
	default int otherBuffer()
	{
		return 3;
	}

	@ConfigItem(
		keyName = READY_ON_THE_WAY,
		name = "Include patches ready on the way",
		description = "Start a run before everything is grown: patches still growing are included when they'll be"
			+ " ready by the time the route gets to them, and the route doesn't take you to a stop before it's"
			+ " ready. Also in the Run tab.",
		position = 9
	)
	default boolean readyOnTheWay()
	{
		return false;
	}

	@ConfigItem(
		keyName = MAKING_MODE,
		name = "Making",
		description = "Sapling making: the bank button, bank tab, highlights and seed vault filter show what pots every"
			+ " tree seed you have. Payment making: they show the baskets, sacks and produce to pack payments. Either"
			+ " replaces the run's items. Set in the Making tab.",
		position = 9,
		hidden = true
	)
	default MakingMode makingMode()
	{
		return MakingMode.OFF;
	}

	@ConfigItem(
		keyName = SAPLING_SKIPPED,
		name = "Seeds left out of sapling making",
		description = "Seed item ids, comma-separated. Set in the Making tab.",
		hidden = true
	)
	default String saplingSkipped()
	{
		return "";
	}

	@ConfigItem(
		keyName = PAYMENT_SKIPPED,
		name = "Payments left out of payment making",
		description = "Item ids of the full basket or sack, comma-separated. Set in the Making tab.",
		hidden = true
	)
	default String paymentSkipped()
	{
		return "";
	}

	@ConfigItem(
		keyName = "fairyRingVia",
		name = "Fairy rings via your house",
		description = "The house portal you take to get to a fairy ring, e.g. Salve Graveyard. Fairy ring legs then let"
			+ " Shortest Path use your house's portals (not just the nearest ring, like the Ardougne monastery's), and"
			+ " the overhead line shows the chain: house teleport, that portal, the ring. Blank: off.",
		position = 10
	)
	default String fairyRingVia()
	{
		return "Salve Graveyard";
	}

	@ConfigItem(
		keyName = "pathCarriedOnly",
		name = "Paths use only what you carry",
		description = "Shortest Path's route to each stop uses only the teleport items you carry and no trips to the"
			+ " bank, so it matches the inventory check. Off: your own Shortest Path settings.",
		position = 10
	)
	default boolean pathCarriedOnly()
	{
		return true;
	}

	@ConfigItem(
		keyName = "blockClearingKept",
		name = "Block clearing kept plants",
		description = "On a harvest run, ignore clicks that would clear a plant you're keeping standing (chop down,"
			+ " clear, dig up, or paying its gardener). Shift-click to do it anyway.",
		position = 13
	)
	default boolean blockClearingKept()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overheadReminders",
		name = "Overhead reminders",
		description = "During a run, show the next thing to do at a stop above your head (compost, plant, pay...)",
		position = 11
	)
	default boolean overheadReminders()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "reminderColour",
		name = "Reminder colour",
		description = "Colour of the overhead reminders",
		position = 12
	)
	default Color reminderColour()
	{
		return new Color(255, 255, 0);
	}

	@ConfigItem(
		keyName = "debugLogging",
		name = "Debug logging",
		description = "Write detailed [farmrun] lines to the client log, for tracking down problems",
		position = 100
	)
	default boolean debugLogging()
	{
		return false;
	}
}
