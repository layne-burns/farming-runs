/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.inventory.RunCheckService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines, in the bank, the items the inventory check says to withdraw (SPEC §2.4), so they're easy to find.
 */
@Singleton
public class BankHighlightOverlay extends WidgetItemOverlay
{
	private static final Color OUTLINE = new Color(255, 200, 0);

	private final RunCheckService checks;
	private final ItemManager itemManager;

	@Inject
	BankHighlightOverlay(RunCheckService checks, ItemManager itemManager)
	{
		this.checks = checks;
		this.itemManager = itemManager;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		// A quantity of 0 is a bank placeholder: nothing there to withdraw.
		if (widgetItem.getQuantity() <= 0 || !checks.toWithdraw().contains(itemId))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), OUTLINE);
		graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
	}
}
