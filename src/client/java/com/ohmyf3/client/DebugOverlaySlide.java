package com.ohmyf3.client;

import com.ohmyf3.animation.SlideInAnimation;

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

	private static final SlideInAnimation ANIMATION = new SlideInAnimation(DURATION_NANOS);

	private static float offset;

	private DebugOverlaySlide() {
	}

	/** Called once per rendered frame, before either column is extracted. */
	public static void update(boolean visible, long nowNanos) {
		offset = ANIMATION.offset(visible, nowNanos);
	}

	/** Current displacement factor in {@code [0, 1]}; {@code 0} means the columns are settled. */
	public static float offset() {
		return offset;
	}
}
