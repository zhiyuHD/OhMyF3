package com.ohmyf3.client.chart;

import com.ohmyf3.config.OhMyF3Config;

/**
 * Where the profiler chart sits, and how to move the whole thing.
 *
 * <p>Only the pie rectangle is handed to the mod by vanilla; the panel and the legend beside it are
 * drawn separately. Translating the pie alone would leave its background behind, so the renderer
 * records the pie's default centre once, and the chart mixin translates the whole
 * {@code extractRenderState} by the difference between that centre and the player's choice.
 *
 * <p>Positions are stored in permille of the screen, so the layout survives resolution changes.
 */
public final class PieLayout {
	public static final String KEY = "pie";

	/** Default centre of the pie, in GUI pixels; filled in the first time vanilla draws it. */
	private static volatile int[] defaultCentre;

	private PieLayout() {
	}

	/** Called from the pie redirect with the rectangle vanilla computed. */
	public static void observeDefaultCentre(int x1, int y1, int x2, int y2) {
		if (defaultCentre == null) {
			defaultCentre = new int[] {(x1 + x2) / 2, (y1 + y2) / 2};
		}
	}

	/** Translation the chart needs this frame, or {@code null} when it sits where vanilla put it. */
	public static int[] translation(int guiWidth, int guiHeight) {
		int[] stored = OhMyF3Config.get().position(KEY);
		int[] centre = defaultCentre;

		if (stored == null || centre == null) {
			return null;
		}

		int targetX = Math.round(stored[0] * guiWidth / 1000.0F);
		int targetY = Math.round(stored[1] * guiHeight / 1000.0F);
		return new int[] {targetX - centre[0], targetY - centre[1]};
	}

	/** Target centre in GUI pixels, for the layout editor's preview. */
	public static int[] targetCentre(int guiWidth, int guiHeight) {
		int[] stored = OhMyF3Config.get().position(KEY);

		if (stored == null) {
			return defaultCentre == null ? new int[] {guiWidth - 140, guiHeight - 200}
					: new int[] {defaultCentre[0], defaultCentre[1]};
		}

		return new int[] {Math.round(stored[0] * guiWidth / 1000.0F),
				Math.round(stored[1] * guiHeight / 1000.0F)};
	}
}
