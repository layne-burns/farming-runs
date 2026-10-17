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
 * compost, then the gardener's payment. Each stops being outlined once it's done.
 */
@Singleton
public class RunItemOverlay extends WidgetItemOverlay
{
	static final Color OUTLINE = new Color(0, 220, 255);

	private final GuidanceTracker guidance;
	private final ItemManager itemManager;

	@Inject
	RunItemOverlay(GuidanceTracker guidance, ItemManager itemManager)
	{
		this.guidance = guidance;
		this.itemManager = itemManager;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		// Noted payment counts as the produce itself.
		if (!guidance.highlights().getItems().contains(itemManager.canonicalize(itemId)))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.drawImage(itemManager.getItemOutline(itemId, widgetItem.getQuantity(), OUTLINE), (int) bounds.getX(),
			(int) bounds.getY(), null);
	}
}
