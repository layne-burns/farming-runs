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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;

/**
 * A temporary bank tab with just the items the run check says to take from the bank, using RuneLite's Bank Tags
 * plugin (it must be on). Your saved tags aren't touched. "Show in bank" opens it now if the bank is open, or the next
 * time you open the bank. It closes with the bank, and items leave it once you carry enough.
 */
@Slf4j
@Singleton
public class BankRunTab
{
	/** The tag's name; hidden in the bank, but it must not clash with a tag of yours. */
	private static final String TAG = "farmingruns-run";
	private static final int OPTIONS = BankTagsService.OPTION_NO_LAYOUT | BankTagsService.OPTION_HIDE_TAG_NAME;

	private final Client client;
	private final ClientThread clientThread;
	private final TagManager tagManager;
	private final BankTagsService bankTags;
	private final BankSearch bankSearch;
	private final RunCheckService checks;
	/** "Show in bank" was pressed while the bank was closed: open the tab when it opens. Client thread. */
	private boolean pending;

	@Inject
	BankRunTab(Client client, ClientThread clientThread, TagManager tagManager, BankTagsService bankTags,
		BankSearch bankSearch, RunCheckService checks)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.tagManager = tagManager;
		this.bankTags = bankTags;
		this.bankSearch = bankSearch;
		this.checks = checks;
		checks.addListener(this::relayout);
	}

	public void register()
	{
		tagManager.registerTag(TAG, itemId -> checks.bankItems().containsKey(itemId));
	}

	public void unregister()
	{
		clientThread.invoke(this::close);
		tagManager.unregisterTag(TAG);
	}

	/** Opens the tab now if the bank is open, else when it next opens. Safe from any thread. */
	public void show()
	{
		clientThread.invoke(() ->
		{
			if (isBankOpen())
			{
				open();
			}
			else
			{
				pending = true;
			}
		});
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && pending)
		{
			pending = false;
			// Once the bank has drawn itself.
			clientThread.invokeLater(this::open);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			close();
		}
	}

	private void open()
	{
		log.debug("[farmrun] bank tab: {} items", checks.bankItems().size());
		bankTags.openBankTag(TAG, OPTIONS);
	}

	/** Back to your normal bank, if ours is the open tab. Client thread. */
	private void close()
	{
		if (TAG.equals(bankTags.getActiveTag()))
		{
			bankTags.closeBankTag();
		}
	}

	/** Redraws the bank when what's needed changes, so items you now carry enough of drop out of the tab. */
	private void relayout()
	{
		clientThread.invoke(() ->
		{
			if (isBankOpen() && TAG.equals(bankTags.getActiveTag()))
			{
				bankSearch.layoutBank();
			}
		});
	}

	private boolean isBankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
	}
}
