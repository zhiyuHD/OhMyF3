package com.ohmyf3.animation;

/**
 * Time-based slide-in progress for a panel that becomes visible on demand.
 *
 * <p>Pure logic with no Minecraft dependencies so it can be unit tested without
 * launching the game. The clock is passed in, which keeps {@code System.nanoTime()}
 * out of the tests.
 *
 * <p>{@link #offset(boolean, long)} returns a multiplier in {@code [0, 1]}:
 * {@code 1} means "fully displaced, start of the animation" and {@code 0} means
 * "settled, animation finished". Multiply it by the slide distance in pixels to get
 * the translation to apply.
 */
public final class SlideInAnimation {
	private final long durationNanos;
	private final long delayNanos;

	private boolean visible;
	private boolean started;
	private long startNanos;

	public SlideInAnimation(long durationNanos) {
		this(durationNanos, 0L);
	}

	/**
	 * @param durationNanos how long the slide takes
	 * @param delayNanos    how long the panel stays fully displaced before moving
	 */
	public SlideInAnimation(long durationNanos, long delayNanos) {
		if (durationNanos <= 0L) {
			throw new IllegalArgumentException("durationNanos must be positive, got " + durationNanos);
		}
		if (delayNanos < 0L) {
			throw new IllegalArgumentException("delayNanos must not be negative, got " + delayNanos);
		}
		this.durationNanos = durationNanos;
		this.delayNanos = delayNanos;
	}

	/**
	 * Feeds the current visibility in and returns the displacement factor.
	 *
	 * <p>Called once per frame. The animation restarts from fully displaced every
	 * time the panel transitions from hidden to visible.
	 */
	public float offset(boolean nowVisible, long nowNanos) {
		if (!nowVisible) {
			this.visible = false;
			this.started = false;
			return 0.0F;
		}

		if (!this.visible || !this.started) {
			// Fresh transition into the visible state: start the slide from the beginning.
			this.visible = true;
			this.started = true;
			this.startNanos = nowNanos;
			return 1.0F;
		}

		long elapsed = nowNanos - this.startNanos - this.delayNanos;

		if (elapsed <= 0L) {
			return 1.0F;
		}

		if (elapsed >= this.durationNanos) {
			// Clamp to zero so a finished animation never translates by rounding noise.
			return 0.0F;
		}

		float progress = (float) elapsed / (float) this.durationNanos;
		return 1.0F - easeOutCubic(progress);
	}

	private static float easeOutCubic(float t) {
		float inverse = 1.0F - t;
		return 1.0F - inverse * inverse * inverse;
	}
}
