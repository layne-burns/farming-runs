/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.state;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Where items can be. Bank, seed vault and seed box are remembered between sessions; the rest are live. */
@Getter
@RequiredArgsConstructor
public enum ItemSource
{
	BANK("Bank", true),
	SEED_VAULT("Seed vault", true),
	SEED_BOX("Seed box", true),
	INVENTORY("Inventory", false),
	EQUIPMENT("Worn", false),
	RUNE_POUCH("Rune pouch", false),
	LEPRECHAUN("Tool leprechaun", false);

	private final String displayName;
	/** Saved to the RuneScape profile, because the game only tells us the contents when it's opened. */
	private final boolean remembered;
}
