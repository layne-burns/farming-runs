/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.ui;

import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Location;
import com.layneburns.farmingruns.run.PlantingPlan;
import com.layneburns.farmingruns.run.RunProgress;
import com.layneburns.farmingruns.run.RunSession;
import com.layneburns.farmingruns.state.PatchObjects;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * During a run, outlines the patches still to do at the current stop (SPEC §2.6), each in its kind's colour
 * ({@link PatchColours}), the same as the seeds for it in your inventory. Each outline goes once its patch is done.
 */
@Singleton
public class PatchOutlineOverlay extends Overlay
{
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;

	private final GameData data;
	private final RunSession session;
	private final PatchObjects patches;
	private final ModelOutlineRenderer outlines;

	@Inject
	PatchOutlineOverlay(GameData data, RunSession session, PatchObjects patches, ModelOutlineRenderer outlines)
	{
		this.data = data;
		this.session = session;
		this.patches = patches;
		this.outlines = outlines;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		RunSession.ActiveRun run = session.active();
		RunProgress.Stop stop = run == null ? null : run.getProgress().currentStop();
		if (stop == null)
		{
			return null;
		}
		Location location = data.location(stop.getLocationId());
		Set<Integer> regions = new HashSet<>(location.getExtraRegionIds());
		regions.add(location.getRegionId());
		for (PlantingPlan.Assignment a : stop.getPatches())
		{
			if (run.getProgress().isPatchDone(a.getPatch().getId()))
			{
				continue;
			}
			for (TileObject object : patches.objects(a.getPatch(), regions))
			{
				outlines.drawOutline(object, OUTLINE_WIDTH, PatchColours.of(a.getPatch().getKind()), OUTLINE_FEATHER);
			}
		}
		return null;
	}
}
