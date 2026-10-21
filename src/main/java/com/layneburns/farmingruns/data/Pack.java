/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

import java.util.List;
import lombok.Value;

/**
 * A gardener's payment that you pack yourself: a basket of 5 fruit or a sack of 10 vegetables, filled one at a time
 * (Oranges(1), Oranges(2)... Oranges(5)). From items.json, for payment making.
 */
@Value
public class Pack
{
	/** The full one, e.g. "Oranges(5)". */
	String name;
	/** What goes in, e.g. "Orange". */
	String produceName;
	int produceId;
	/** The item group (items.json) of the empty container: "basket" or "empty_sack". */
	String container;
	/** Item ids by how many it holds: the first holds 1, the last is full. */
	List<Integer> fills;

	/** How many it holds when full. */
	public int size()
	{
		return fills.size();
	}

	public int fullId()
	{
		return fills.get(fills.size() - 1);
	}

	/** The item holding {@code count}, from 1 to {@link #size()}. */
	public int fillId(int count)
	{
		return fills.get(count - 1);
	}

	/** E.g. "Oranges(3)" for 3. */
	public String fillName(int count)
	{
		return name.replaceFirst("\\(\\d+\\)$", "(" + count + ")");
	}
}
