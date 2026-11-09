package com.ohmyf3.client;

import com.ohmyf3.animation.SlideAnimation;
import com.ohmyf3.config.OhMyF3Config;

/**
 * Per-frame slide state shared between the HUD hook (which owns the clock) and the
 * debug-column hook (which applies the translation).
 *
 * <p>The F3 screen is drawn as two independent columns on opposite sides of the screen,
 * so the animation is sampled once per frame and both columns reuse the same value.
 * Sampling it inside each column would make the second column lag behind the first.
 */
public final class DebugOverlaySlide {
	/** How long each column takes to slide home. */
	private static final long DURATION_NANOS = 320_000_000L;

	/** How far each column starts outside its resting position, in GUI pixels. */
	public static final float DISTANCE = 200.0F;

	private static final SlideAnimation ANIMATION = new SlideAnimation(DURATION_NANOS);

	/** 1 = fully open, 0 = fully displaced off-screen. */
	private static float openness = 1.0F;

	private DebugOverlaySlide() {
	}

	/** Called once per rendered frame, before either column is extracted. */
	public static void update(boolean visible, long nowNanos) {
		if (!OhMyF3Config.get().slideAnimation) {
			openness = visible ? 1.0F : 0.0F;
			return;
		}

		openness = ANIMATION.update(visible, nowNanos);
	}

	/**
	 * Displacement factor in {@code [0, 1]}: {@code 1} means fully off-screen (start of the
	 * slide-in, end of the slide-out) and {@code 0} means settled.
	 */
	public static float offset() {
		return 1.0F - openness;
	}
}
