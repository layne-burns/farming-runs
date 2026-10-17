/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.inventory.RunCheckService;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * In the bank and seed vault, outlines the items the inventory check says to take out (SPEC §2.4), with how many
 * in the bottom right corner, so they're easy to find and grab.
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
		showOnInterfaces(InterfaceID.SEED_VAULT);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Integer take = checks.bankItems().get(itemId);
		// A quantity of 0 is a bank placeholder: nothing there to take.
		if (take == null || widgetItem.getQuantity() <= 0)
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), OUTLINE);
		graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);

		String text = "×" + take;
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int x = (int) bounds.getMaxX() - metrics.stringWidth(text);
		int y = (int) bounds.getMaxY();
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(OUTLINE);
		graphics.drawString(text, x, y);
	}
}
