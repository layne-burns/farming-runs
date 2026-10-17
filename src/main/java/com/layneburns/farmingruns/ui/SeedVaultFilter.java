/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.inventory.RunCheckService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/**
 * The seed vault's version of the run's bank tab: shows only the seeds the run check says to take out, packed into a
 * grid, with the category headings hidden. Bank Tags can't do this for the vault, so the vault's own widgets are
 * hidden and moved, and put back exactly as they were when it's switched off or the vault closes. When the vault
 * redraws itself (after a withdrawal) the filter is applied again. Client thread.
 */
@Slf4j
@Singleton
public class SeedVaultFilter
{
	private final Client client;
	private final ClientThread clientThread;
	private final RunCheckService checks;
	private boolean on;
	/** Widgets we changed, with their original x, y and hidden state, to put back. */
	private final Map<Widget, int[]> original = new HashMap<>();
	private int originalScrollHeight = -1;

	@Inject
	SeedVaultFilter(Client client, ClientThread clientThread, RunCheckService checks)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.checks = checks;
		checks.addListener(() -> clientThread.invokeLater(() ->
		{
			if (on)
			{
				restore();
				apply();
			}
		}));
	}

	public boolean isOn()
	{
		return on;
	}

	public boolean isVaultOpen()
	{
		Widget vault = client.getWidget(InterfaceID.SeedVault.UNIVERSE);
		return vault != null && !vault.isHidden();
	}

	/**
	 * From the search prompt's button: closes the search and switches the filter on or off. The click arrives inside a
	 * game script, so this waits for it to finish.
	 */
	void toggleFromSearch()
	{
		clientThread.invokeLater(() ->
		{
			client.runScript(ScriptID.MESSAGE_LAYER_CLOSE, 1, 1, 0);
			client.setVarcStrValue(VarClientID.MESLAYERINPUT, "");
			on = !on;
			// Once the vault has redrawn without the search text.
			clientThread.invokeLater(() ->
			{
				restore();
				if (on)
				{
					apply();
				}
			});
		});
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.SEED_VAULT_BUILD && on)
		{
			// A fresh build made new widgets: nothing of ours to put back.
			original.clear();
			originalScrollHeight = -1;
			apply();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.SEED_VAULT)
		{
			on = false;
			original.clear();
			originalScrollHeight = -1;
		}
	}

	/** Hides the seeds the run doesn't need and the headings, and packs the rest into the grid. */
	private void apply()
	{
		Widget objects = client.getWidget(InterfaceID.SeedVault.OBJ_LIST);
		Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
		if (objects == null || list == null || objects.getDynamicChildren() == null)
		{
			return;
		}
		Widget[] items = objects.getDynamicChildren();
		Widget[] names = children(InterfaceID.SeedVault.TEXT_LIST);
		Set<Integer> wanted = checks.bankItems().keySet();

		// The grid: the item columns' x positions and the row height, from where the vault drew its items.
		TreeSet<Integer> columns = new TreeSet<>();
		TreeSet<Integer> rows = new TreeSet<>();
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < items.length; i++)
		{
			if (items[i] != null && items[i].getItemId() > 0)
			{
				columns.add(items[i].getOriginalX());
				rows.add(items[i].getOriginalY());
				order.add(i);
			}
		}
		if (columns.isEmpty())
		{
			return;
		}
		int rowHeight = smallestGap(rows, items[order.get(0)].getOriginalHeight());
		int top = rows.first();
		List<Integer> xs = new ArrayList<>(columns);
		order.sort(Comparator.comparingInt((Integer i) -> items[i].getOriginalY()).thenComparingInt(i -> items[i].getOriginalX()));

		int shown = 0;
		for (int i : order)
		{
			Widget item = items[i];
			Widget name = names.length == items.length ? names[i] : null;
			if (wanted.contains(item.getItemId()) && item.getItemQuantity() > 0)
			{
				int x = xs.get(shown % xs.size());
				int y = top + (shown / xs.size()) * rowHeight;
				if (name != null)
				{
					move(name, x + name.getOriginalX() - item.getOriginalX(), y + name.getOriginalY() - item.getOriginalY(),
						false);
				}
				move(item, x, y, false);
				shown++;
			}
			else
			{
				hide(item);
				if (name != null)
				{
					hide(name);
				}
			}
		}
		for (Widget heading : children(InterfaceID.SeedVault.CATEGORY_HEADERS))
		{
			hide(heading);
		}
		for (Widget line : children(InterfaceID.SeedVault.CATEGORY_LINES))
		{
			hide(line);
		}
		if (originalScrollHeight < 0)
		{
			originalScrollHeight = list.getScrollHeight();
		}
		list.setScrollY(0);
		list.setScrollHeight(Math.max(list.getHeight(), top + ((shown + xs.size() - 1) / xs.size()) * rowHeight));
		list.revalidateScroll();
		log.debug("[farmrun] seed vault: showing {} of {} seeds", shown, order.size());
	}

	/** Puts every widget we changed back as the vault drew it. */
	private void restore()
	{
		original.forEach((widget, was) ->
		{
			widget.setOriginalX(was[0]);
			widget.setOriginalY(was[1]);
			widget.setHidden(was[2] == 1);
			widget.revalidate();
		});
		original.clear();
		Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
		if (list != null && originalScrollHeight >= 0)
		{
			list.setScrollHeight(originalScrollHeight);
			list.revalidateScroll();
		}
		originalScrollHeight = -1;
	}

	private void move(Widget widget, int x, int y, boolean hidden)
	{
		remember(widget);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setHidden(hidden);
		widget.revalidate();
	}

	private void hide(Widget widget)
	{
		remember(widget);
		widget.setHidden(true);
	}

	private void remember(Widget widget)
	{
		original.putIfAbsent(widget, new int[]{widget.getOriginalX(), widget.getOriginalY(), widget.isSelfHidden() ? 1 : 0});
	}

	private Widget[] children(int componentId)
	{
		Widget parent = client.getWidget(componentId);
		Widget[] children = parent == null ? null : parent.getDynamicChildren();
		return children == null ? new Widget[0] : children;
	}

	/** The usual distance between rows: the smallest gap between item rows (headings make some gaps bigger). */
	private static int smallestGap(TreeSet<Integer> rows, int fallback)
	{
		int gap = Integer.MAX_VALUE;
		Integer previous = null;
		for (int y : rows)
		{
			if (previous != null)
			{
				gap = Math.min(gap, y - previous);
			}
			previous = y;
		}
		return gap == Integer.MAX_VALUE ? fallback : gap;
	}
}
