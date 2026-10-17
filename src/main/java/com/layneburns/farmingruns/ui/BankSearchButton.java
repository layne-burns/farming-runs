/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.inventory.RunCheckService;
import java.util.ArrayList;
import java.util.List;
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
 * A raised "Farm Run Items" button down the right of the chatbox while you search the bank, so the run's bank tab
 * is one click away without the side panel. It shows only when the run check has something to take from the bank, and is hidden again when
 * the search closes (the chatbox's prompt area is shared with other prompts). Client thread.
 */
@Singleton
public class BankSearchButton
{
	/** Two lines, centred on the button. */
	private static final String LABEL = "Farm Run<br>Items";
	/** The button's width: clear of the prompt's centred text even in the fixed-size chatbox. */
	private static final int WIDTH = 84;
	/** Gap between the button and the chatbox frame, on every side. */
	private static final int MARGIN = 6;
	/** Stone-button colours, as on the game's own interfaces: outline, lit top-left edge, shadowed bottom-right. */
	private static final int OUTLINE = 0x0e0e0c;
	private static final int HIGHLIGHT = 0x726451;
	private static final int SHADOW = 0x1d1a15;
	private static final int FACE = 0x4a3f33;
	private static final int FACE_HOVER = 0x5b4e3f;
	/** The game's orange button text, white on hover. */
	private static final int TEXT_COLOUR = 0xff981f;
	private static final int TEXT_HOVER = 0xffffff;

	private final Client client;
	private final ClientThread clientThread;
	private final BankRunTab bankRunTab;
	private final RunCheckService checks;
	/** Our widgets in the chatbox prompt layer, or null when not added (or the game rebuilt the layer). */
	private final List<Widget> parts = new ArrayList<>();
	private Widget face;
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
		parts.clear();
		// Drawn in order: outline, lit edge (up-left), shadow edge (down-right), face on top. Each is
		// (inset from the outline's left/top, inset from its right/bottom).
		rectangle(layer, OUTLINE, 0, 0, 0, 0);
		rectangle(layer, HIGHLIGHT, 1, 1, 2, 2);
		rectangle(layer, SHADOW, 2, 2, 1, 1);
		face = rectangle(layer, FACE, 2, 2, 2, 2);

		text = layer.createChild(-1, WidgetType.TEXT);
		place(text, 2, 2, 2, 2);
		text.setText(LABEL);
		text.setFontId(FontID.BOLD_12);
		text.setTextColor(TEXT_COLOUR);
		text.setTextShadowed(true);
		text.setXTextAlignment(WidgetTextAlignment.CENTER);
		text.setYTextAlignment(WidgetTextAlignment.CENTER);
		text.setHasListener(true);
		text.setAction(0, "Show");
		text.setOnOpListener((JavaScriptCallback) e -> bankRunTab.openFromSearch());
		text.setOnMouseOverListener((JavaScriptCallback) e -> hover(true));
		text.setOnMouseLeaveListener((JavaScriptCallback) e -> hover(false));
		text.revalidate();
		parts.add(text);
	}

	private void hover(boolean on)
	{
		face.setTextColor(on ? FACE_HOVER : FACE);
		text.setTextColor(on ? TEXT_HOVER : TEXT_COLOUR);
	}

	private Widget rectangle(Widget layer, int colour, int left, int top, int right, int bottom)
	{
		Widget rect = layer.createChild(-1, WidgetType.RECTANGLE);
		place(rect, left, top, right, bottom);
		rect.setFilled(true);
		rect.setTextColor(colour);
		rect.revalidate();
		parts.add(rect);
		return rect;
	}

	/**
	 * Places a widget within the button's outline (which runs down the right of the prompt area, {@link #MARGIN} from
	 * its edges), inset by the given pixels on each side. Top-anchored so odd insets land exactly.
	 */
	private static void place(Widget widget, int left, int top, int right, int bottom)
	{
		widget.setOriginalWidth(WIDTH - left - right);
		widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		widget.setOriginalX(MARGIN + right);
		widget.setHeightMode(WidgetSizeMode.MINUS);
		widget.setOriginalHeight(2 * MARGIN + top + bottom);
		widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		widget.setOriginalY(MARGIN + top);
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
		parts.forEach(w -> w.setHidden(hidden));
		if (hidden)
		{
			hover(false);
		}
	}

	private boolean isBankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
	}
}
