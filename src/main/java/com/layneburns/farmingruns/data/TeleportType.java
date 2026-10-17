/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

/** How a teleport is paid for, which decides what the inventory check looks for. */
public enum TeleportType
{
	/** Any one of the teleport's items. */
	ITEM,
	/** The spell's runes, on the right spellbook. */
	SPELL,
	/** A dramen or lunar staff, unless the waiving diary is done. */
	FAIRY_RING,
	/** No items: from another spirit tree. */
	SPIRIT_TREE,
	/** No items: a walk or a boat. */
	WALK
}
