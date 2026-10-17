/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.inventory.RunCheckService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/**
 * A "Farm Run Items" column at the right of the chatbox while you search the bank, so the run's bank tab is one click away without
 * the side panel. It shows only when the run check has something to take from the bank, and is hidden again when
 * the search closes (the chatbox's prompt area is shared with other prompts). Client thread.
 */
@Singleton
public class BankSearchButton
{
	/** Two lines, centred in the column. */
	private static final String LABEL = "Farm Run<br>Items";
	/** The column's width: clear of the prompt's centred text even in the fixed-size chatbox. */
	private static final int WIDTH = 80;
	private static final int DIVIDER_WIDTH = 2;
	/** Inset from the top and bottom, so the divider stops short of the chatbox frame. */
	private static final int INSET = 4;
	/** The game's chatbox text colour, and the dark brown of its frame for the divider. */
	private static final int TEXT_COLOUR = 0x000000;
	private static final int DIVIDER_COLOUR = 0x3f3628;
	/** A faint shade over the column on hover; opacity runs from 0 (solid) to 255 (invisible). */
	private static final int SHADE_COLOUR = 0x000000;
	private static final int SHADE_HOVER_OPACITY = 215;
	private static final int SHADE_IDLE_OPACITY = 255;

	private final Client client;
	private final ClientThread clientThread;
	private final BankRunTab bankRunTab;
	private final RunCheckService checks;
	/** Our widgets in the chatbox prompt layer, or null when not added (or the game rebuilt the layer). */
	private Widget shade;
	private Widget divider;
	private Widget text;

	@Inject
	BankSearchButton(Client client, ClientThread clientThread, BankRunTab bankRunTab, RunCheckService checks)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.bankRunTab = bankRunTab;
		this.checks = checks;
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		if (event.getIndex() == VarClientID.MESLAYERMODE)
		{
			// After the game has drawn (or cleared) its prompt.
			clientThread.invokeLater(this::update);
		}
	}

	/** Removes the button, e.g. when the plugin stops. */
	public void hide()
	{
		clientThread.invoke(() -> setHidden(true));
	}

	private void update()
	{
		boolean searching = client.getVarcIntValue(VarClientID.MESLAYERMODE) == InputType.SEARCH.getType()
			&& isBankOpen() && !checks.bankItems().isEmpty();
		if (searching)
		{
			add();
		}
		setHidden(!searching);
	}

	private void add()
	{
		Widget layer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		if (layer == null || isOurs(layer))
		{
			return;
		}
		shade = layer.createChild(-1, WidgetType.RECTANGLE);
		place(shade, WIDTH, 0);
		shade.setFilled(true);
		shade.setTextColor(SHADE_COLOUR);
		shade.setOpacity(SHADE_IDLE_OPACITY);
		shade.revalidate();

		divider = layer.createChild(-1, WidgetType.RECTANGLE);
		place(divider, DIVIDER_WIDTH, WIDTH);
		divider.setFilled(true);
		divider.setTextColor(DIVIDER_COLOUR);
		divider.revalidate();

		text = layer.createChild(-1, WidgetType.TEXT);
		place(text, WIDTH, 0);
		text.setText(LABEL);
		text.setFontId(FontID.BOLD_12);
		text.setTextColor(TEXT_COLOUR);
		text.setXTextAlignment(WidgetTextAlignment.CENTER);
		text.setYTextAlignment(WidgetTextAlignment.CENTER);
		text.setHasListener(true);
		text.setAction(0, "Show");
		text.setOnOpListener((JavaScriptCallback) e -> bankRunTab.openFromSearch());
		text.setOnMouseOverListener((JavaScriptCallback) e -> shade.setOpacity(SHADE_HOVER_OPACITY));
		text.setOnMouseLeaveListener((JavaScriptCallback) e -> shade.setOpacity(SHADE_IDLE_OPACITY));
		text.revalidate();
	}

	/** A full-height strip at the right of the prompt area, {@code fromRight} pixels in. */
	private static void place(Widget widget, int width, int fromRight)
	{
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(2 * INSET);
		widget.setHeightMode(WidgetSizeMode.MINUS);
		widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		widget.setOriginalX(fromRight);
		widget.setOriginalY(0);
	}

	/** True when our button is still among the layer's children (the game may have cleared them). */
	private boolean isOurs(Widget layer)
	{
		Widget[] children = layer.getDynamicChildren();
		if (text == null || children == null)
		{
			return false;
		}
		for (Widget child : children)
		{
			if (child == text)
			{
				return true;
			}
		}
		return false;
	}

	private void setHidden(boolean hidden)
	{
		Widget layer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		if (layer == null || !isOurs(layer))
		{
			return;
		}
		shade.setHidden(hidden);
		divider.setHidden(hidden);
		text.setHidden(hidden);
	}

	private boolean isBankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
	}
}
