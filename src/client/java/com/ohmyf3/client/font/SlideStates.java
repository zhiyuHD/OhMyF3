package com.ohmyf3.client.font;

import com.ohmyf3.client.DebugOverlaySlide;

/**
 * Bridges the slide animation state into the font renderer without a package cycle.
 *
 * <p>The renderer needs to know whether the close animation has finished, because after that point
 * the always-on rows must be drawn alone instead of as part of a sliding column.
 */
public final class SlideStates {
	private SlideStates() {
	}

	/** True once the animation has fully settled open or closed. */
	public static boolean atRest() {
		float offset = DebugOverlaySlide.offset();
		return offset <= 0.001F || offset >= 0.999F;
	}
}
