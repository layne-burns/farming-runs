/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.lang.reflect.Field;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;

/**
 * Turns RuneLite constant names used in the data files ("FARMING_TRANSMIT_A", "KRAGEN") into numbers. Names
 * keep the files readable; this fails loudly if RuneLite renames one.
 */
final class GameIds
{
	private GameIds()
	{
	}

	static int varbit(String name, String where)
	{
		return constant(VarbitID.class, name, where);
	}

	static int npc(String name, String where)
	{
		return constant(NpcID.class, name, where);
	}

	private static int constant(Class<?> holder, String name, String where)
	{
		try
		{
			Field field = holder.getField(name);
			return field.getInt(null);
		}
		catch (NoSuchFieldException | IllegalAccessException e)
		{
			throw new DataException(where + ": unknown " + holder.getSimpleName() + " name " + name, e);
		}
	}
}
