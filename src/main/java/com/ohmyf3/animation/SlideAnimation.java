package com.ohmyf3.animation;

/**
 * A retargetable 0..1 animation, used for the debug screen's open/close movement.
 *
 * <p>Instead of running a separate slide-in and slide-out, a single "openness" value is eased
 * towards a target: {@code 1} when the overlay is shown, {@code 0} when it is hidden. Changing
 * the target mid-flight starts a fresh segment <em>from the current value</em>, so toggling F3
 * during either animation reverses smoothly instead of snapping back to an endpoint.
 *
 * <p>Each segment's duration is proportional to the distance it has to travel, which keeps the
 * apparent speed constant no matter where the reversal happens.
 */
public final class SlideAnimation {
	private final long fullDurationNanos;

	private boolean initialised;
	private boolean lastVisible;

	private float value;
	private float segmentFrom;
	private float segmentTo;
	private long segmentStartNanos;
	private long segmentDurationNanos;
	private boolean animating;

	public SlideAnimation(long fullDurationNanos) {
		if (fullDurationNanos <= 0L) {
			throw new IllegalArgumentException("durationNanos must be positive, got " + fullDurationNanos);
		}

		this.fullDurationNanos = fullDurationNanos;
	}

	/**
	 * Advances the animation and returns the current openness in {@code [0, 1]}.
	 *
	 * @param visible what the overlay wants to be right now
	 */
	public float update(boolean visible, long nowNanos) {
		if (!this.initialised) {
			// Start wherever the overlay already is, so opening the game does not animate.
			this.initialised = true;
			this.lastVisible = visible;
			this.value = visible ? 1.0F : 0.0F;
			return this.value;
		}

		if (visible != this.lastVisible) {
			this.lastVisible = visible;
			startSegment(visible ? 1.0F : 0.0F, nowNanos);
		}

		if (this.animating) {
			long elapsed = nowNanos - this.segmentStartNanos;

			if (elapsed >= this.segmentDurationNanos) {
				this.value = this.segmentTo;
				this.animating = false;
			} else if (elapsed > 0L) {
				float progress = elapsed / (float) this.segmentDurationNanos;
				this.value = this.segmentFrom + (this.segmentTo - this.segmentFrom) * easeOutCubic(progress);
			}
		}

		return this.value;
	}

	private void startSegment(float target, long nowNanos) {
		float distance = Math.abs(target - this.value);

		this.segmentFrom = this.value;
		this.segmentTo = target;

		if (distance <= 1.0e-4F) {
			this.value = target;
			this.animating = false;
			return;
		}

		this.segmentStartNanos = nowNanos;
		this.segmentDurationNanos = Math.max(1L, Math.round(this.fullDurationNanos * distance));
		this.animating = true;
	}

	private static float easeOutCubic(float t) {
		float inverse = 1.0F - t;
		return 1.0F - inverse * inverse * inverse;
	}
}
