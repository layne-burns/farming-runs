/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.run.GuidanceTracker;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * During a run, outlines the gardener to pay at the current stop once a patch there is planted and still unpaid
 * (SPEC §2.6). Gone once you've paid.
 */
@Singleton
public class GardenerOverlay extends Overlay
{
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;

	private final Client client;
	private final GuidanceTracker guidance;
	private final ModelOutlineRenderer outlines;

	@Inject
	GardenerOverlay(Client client, GuidanceTracker guidance, ModelOutlineRenderer outlines)
	{
		this.client = client;
		this.guidance = guidance;
		this.outlines = outlines;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Set<Integer> gardeners = guidance.highlights().getNpcs();
		if (gardeners.isEmpty())
		{
			return null;
		}
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (gardeners.contains(npc.getId()))
			{
				outlines.drawOutline(npc, OUTLINE_WIDTH, RunItemOverlay.OUTLINE, OUTLINE_FEATHER);
			}
		}
		return null;
	}
}
