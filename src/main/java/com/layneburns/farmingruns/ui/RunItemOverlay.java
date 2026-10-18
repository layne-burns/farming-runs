/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.run.GuidanceTracker;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * During a run, outlines in your inventory what to use at the current stop (SPEC §2.6): the seeds to plant, the
 * compost, then the gardener's payment. Seeds are outlined in their patch kind's colour ({@link PatchColours}), the
 * same as the patch; everything else in the usual one. Each stops being outlined once it's done.
 */
@Singleton
public class RunItemOverlay extends WidgetItemOverlay
{
	private final GuidanceTracker guidance;
	private final ItemManager itemManager;
	private final PatchColours colours;

	@Inject
	RunItemOverlay(GuidanceTracker guidance, ItemManager itemManager, PatchColours colours)
	{
		this.colours = colours;
		this.guidance = guidance;
		this.itemManager = itemManager;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		// Noted payment counts as the produce itself.
		int canonical = itemManager.canonicalize(itemId);
		if (!guidance.highlights().getItems().contains(canonical))
		{
			return;
		}
		// Seeds take their patch kind's colour, as the patch outline does; everything else the usual one.
		Color colour = colours.ofItem(canonical);
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.drawImage(itemManager.getItemOutline(itemId, widgetItem.getQuantity(), colour), (int) bounds.getX(),
			(int) bounds.getY(), null);
	}
}
