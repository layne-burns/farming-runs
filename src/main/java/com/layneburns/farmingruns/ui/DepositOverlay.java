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
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * While the bank or the seed vault is open, outlines in red the items in your inventory that the run (or the making
 * mode) doesn't use, so they're easy to deposit: in payment making, that includes the full baskets and sacks. Only
 * there: elsewhere you can carry what you like without it lighting up.
 */
@Singleton
public class DepositOverlay extends WidgetItemOverlay
{
	private static final Color OUTLINE = new Color(230, 50, 50);

	private final Client client;
	private final RunCheckService checks;
	private final ItemManager itemManager;

	@Inject
	DepositOverlay(Client client, RunCheckService checks, ItemManager itemManager)
	{
		this.client = client;
		this.checks = checks;
		this.itemManager = itemManager;
		showOnInventory();
		// The seed vault's own copy of your inventory.
		showOnInterfaces(InterfaceID.SEED_VAULT_DEPOSIT);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Set<Integer> needed = checks.neededItems();
		// Nothing set up: no way to tell what's spare, so mark nothing.
		if (needed.isEmpty() || !(isOpen(InterfaceID.Bankmain.UNIVERSE) || isOpen(InterfaceID.SeedVault.UNIVERSE))
			|| needed.contains(itemManager.canonicalize(itemId)))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.drawImage(itemManager.getItemOutline(itemId, widgetItem.getQuantity(), OUTLINE), (int) bounds.getX(),
			(int) bounds.getY(), null);
	}

	private boolean isOpen(int componentId)
	{
		Widget widget = client.getWidget(componentId);
		return widget != null && !widget.isHidden();
	}
}
