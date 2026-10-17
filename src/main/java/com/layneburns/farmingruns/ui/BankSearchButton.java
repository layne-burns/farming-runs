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
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/**
 * A "Farm run items" button in the chatbox while you search the bank, so the run's bank tab is one click away without
 * the side panel. It shows only when the run check has something to take from the bank, and is hidden again when
 * the search closes (the chatbox's prompt area is shared with other prompts). Client thread.
 */
@Singleton
public class BankSearchButton
{
	private static final String LABEL = "Farm run items";
	private static final int WIDTH = 110;
	private static final int HEIGHT = 20;
	private static final int MARGIN = 6;
	private static final int TEXT_COLOUR = 0xff981f;
	private static final int HOVER_COLOUR = 0xffffff;
	private static final int BACKGROUND = 0x3e3529;
	private static final int BORDER = 0x1c1c19;

	private final Client client;
	private final ClientThread clientThread;
	private final BankRunTab bankRunTab;
	private final RunCheckService checks;
	/** Our widgets in the chatbox prompt layer, or null when not added (or the game rebuilt the layer). */
	private Widget background;
	private Widget border;
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
		background = layer.createChild(-1, WidgetType.RECTANGLE);
		place(background);
		background.setFilled(true);
		background.setTextColor(BACKGROUND);
		background.revalidate();

		border = layer.createChild(-1, WidgetType.RECTANGLE);
		place(border);
		border.setTextColor(BORDER);
		border.revalidate();

		text = layer.createChild(-1, WidgetType.TEXT);
		place(text);
		text.setText(LABEL);
		text.setFontId(FontID.PLAIN_12);
		text.setTextColor(TEXT_COLOUR);
		text.setTextShadowed(true);
		text.setXTextAlignment(WidgetTextAlignment.CENTER);
		text.setYTextAlignment(WidgetTextAlignment.CENTER);
		text.setHasListener(true);
		text.setAction(0, "Show");
		text.setOnOpListener((JavaScriptCallback) e -> bankRunTab.openFromSearch());
		text.setOnMouseOverListener((JavaScriptCallback) e -> text.setTextColor(HOVER_COLOUR));
		text.setOnMouseLeaveListener((JavaScriptCallback) e -> text.setTextColor(TEXT_COLOUR));
		text.revalidate();
	}

	/** Bottom right of the prompt area. */
	private static void place(Widget widget)
	{
		widget.setOriginalWidth(WIDTH);
		widget.setOriginalHeight(HEIGHT);
		widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_BOTTOM);
		widget.setOriginalX(MARGIN);
		widget.setOriginalY(MARGIN);
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
		background.setHidden(hidden);
		border.setHidden(hidden);
		text.setHidden(hidden);
	}

	private boolean isBankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
	}
}
