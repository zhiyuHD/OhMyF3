package com.ohmyf3.client.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Frame-rate independent value smoothing for Material 3 motion.
 *
 * <p>Material 3 animates with the emphasised easing family over roughly 150–300 ms; this
 * approximates that feel with an exponential approach (which is self-correcting when frames are
 * long) and clamps to the target so values settle exactly. Hover states, switch thumbs and the
 * screen's entrance all read from here, keyed by a stable string.
 */
public final class Anim {
	/** Fraction of the remaining distance covered per 60 FPS frame. */
	private static final float PER_FRAME = 0.28F;

	/** Snap threshold, in the same units as the animated value. */
	private static final float EPSILON = 0.002F;

	private static final Map<String, State> STATES = new HashMap<>();

	private Anim() {
	}

	private static final class State {
		private float value;
		private long lastNanos;
	}

	/** Starts a key at {@code value} so it can animate towards its target afterwards. */
	public static void seed(String key, float value) {
		State state = new State();
		state.value = value;
		state.lastNanos = System.nanoTime();
		STATES.put(key, state);
	}

	/** Advances {@code key} towards {@code target}; call once per frame, per key. */
	public static float value(String key, float target) {
		long now = System.nanoTime();
		State state = STATES.get(key);

		if (state == null) {
			state = new State();
			state.value = target;
			state.lastNanos = now;
			STATES.put(key, state);
			return target;
		}

		double elapsedMs = Math.max(0.0, Math.min(250.0, (now - state.lastNanos) / 1_000_000.0));
		state.lastNanos = now;

		// Normalise to "frames at 60 Hz", so a long frame does not undershoot.
		double frames = elapsedMs / (1000.0 / 60.0);
		double factor = 1.0 - Math.pow(1.0 - PER_FRAME, frames);
		float next = state.value + (target - state.value) * (float) factor;

		if (Math.abs(target - next) <= EPSILON) {
			next = target;
		}

		state.value = next;
		return next;
	}

	/** Linear colour interpolation in premultiplied-by-alpha space, so fades stay even. */
	public static int mix(int from, int to, float t) {
		float clamped = t < 0.0F ? 0.0F : Math.min(t, 1.0F);

		int a = Math.round((from >>> 24 & 0xFF) + ((to >>> 24 & 0xFF) - (from >>> 24 & 0xFF)) * clamped);
		int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * clamped);
		int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * clamped);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);

		return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
	}

	/** Multiplies a colour's alpha, for fade-ins. */
	public static int alpha(int color, float factor) {
		float clamped = factor < 0.0F ? 0.0F : Math.min(factor, 1.0F);
		int a = Math.round((color >>> 24 & 0xFF) * clamped);
		return (a & 0xFF) << 24 | (color & 0xFFFFFF);
	}
}
