/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiFunction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.components.IconTextField;

/**
 * The Bank tab's top row: a search box and a "Filters" dropdown. It only holds what's typed and ticked, and calls
 * {@code onChange} when either changes; {@link BankFilters} and {@link BankView#search} do the filtering.
 */
class BankToolbar extends JPanel
{
	private final IconTextField search = new IconTextField();
	private final JButton filtersButton = new JButton();
	private final Runnable onChange;
	private BankFilters filters = BankFilters.NONE;

	BankToolbar(Runnable onChange)
	{
		super(new BorderLayout(4, 0));
		this.onChange = onChange;
		setBackground(Palette.BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setBackground(Palette.SURFACE);
		search.setHoverBackgroundColor(Palette.SURFACE_HOVER);
		search.setPreferredSize(new Dimension(0, 30));
		search.setToolTipText("Search every page: an item name or a patch kind (e.g. \"ranarr\", \"fruit\")");
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onChange.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onChange.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onChange.run();
			}
		});
		search.addClearListener(onChange);

		JPopupMenu menu = new JPopupMenu();
		menu.add(filterItem("Has protection",
			"Only crops a gardener protects, for which you hold payment. Green: enough for a full run; yellow: some.",
			BankFilters::withHasProtection));
		menu.add(filterItem("Enough for a run",
			"Only seeds whose spare (after the buffer) fills at least one patch. Green: every patch; yellow: some.",
			BankFilters::withEnoughForRun));
		menu.add(filterItem("Can plant", "Only seeds at or below your Farming level.", BankFilters::withCanPlant));
		filtersButton.setFocusable(false);
		filtersButton.setBackground(Palette.SURFACE);
		filtersButton.setForeground(Color.WHITE);
		filtersButton.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
		filtersButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				filtersButton.setBackground(Palette.SURFACE_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				filtersButton.setBackground(Palette.SURFACE);
			}
		});
		filtersButton.setToolTipText("Filter the seed list (they combine)");
		filtersButton.addActionListener(e -> menu.show(filtersButton, 0, filtersButton.getHeight()));
		updateButtonText();

		add(search, BorderLayout.CENTER);
		add(filtersButton, BorderLayout.EAST);
	}

	String query()
	{
		return search.getText().trim();
	}

	/** The ticked filters, with your current Farming level. */
	BankFilters filters(int farmingLevel)
	{
		return filters.withFarmingLevel(farmingLevel);
	}

	private JCheckBoxMenuItem filterItem(String name, String tooltip,
		BiFunction<BankFilters, Boolean, BankFilters> setter)
	{
		JCheckBoxMenuItem item = new JCheckBoxMenuItem(name);
		item.setToolTipText(tooltip);
		// Keep the menu open, so several filters can be ticked in one go.
		item.putClientProperty("CheckBoxMenuItem.doNotCloseOnMouseClick", true);
		item.addActionListener(e ->
		{
			filters = setter.apply(filters, item.isSelected());
			updateButtonText();
			onChange.run();
		});
		return item;
	}

	private void updateButtonText()
	{
		int active = filters.activeCount();
		filtersButton.setText(active == 0 ? "Filters" : "Filters (" + active + ")");
	}
}
