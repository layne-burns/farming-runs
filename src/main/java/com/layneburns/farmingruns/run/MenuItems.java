/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.run;

import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;

/** Which item a menu click was on, including worn items (whose clicks don't carry the item id). */
public final class MenuItems
{
	/** A worn-item slot draws its item in this child. */
	private static final int WORN_ITEM_CHILD = 1;

	private MenuItems()
	{
	}

	/** The clicked item's id, or -1 when the click wasn't on an item. */
	public static int itemId(MenuOptionClicked event)
	{
		if (event.getItemId() > 0)
		{
			return event.getItemId();
		}
		Widget widget = event.getMenuEntry().getWidget();
		if (widget == null)
		{
			return -1;
		}
		if (widget.getItemId() > 0)
		{
			return widget.getItemId();
		}
		Widget worn = widget.getChild(WORN_ITEM_CHILD);
		return worn != null && worn.getItemId() > 0 ? worn.getItemId() : -1;
	}
}
