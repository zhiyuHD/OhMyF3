package com.ohmyf3.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlideInAnimationTest {
	private static final long DURATION = 1_000_000_000L; // 1s for easy arithmetic

	@Test
	void hiddenPanelIsNeverDisplaced() {
		SlideInAnimation animation = new SlideInAnimation(DURATION);

		assertEquals(0.0F, animation.offset(false, 0L));
		assertEquals(0.0F, animation.offset(false, 5_000_000_000L));
	}

	@Test
	void startsFullyDisplacedOnFirstVisibleFrame() {
		SlideInAnimation animation = new SlideInAnimation(DURATION);

		assertEquals(1.0F, animation.offset(true, 100L));
	}

	@Test
	void approachesZeroAndSettlesAfterDuration() {
		long start = 100L;
		SlideInAnimation animation = new SlideInAnimation(DURATION);
		animation.offset(true, start);

		// Half way through: 1 - easeOutCubic(0.5) = 1 - 0.875 = 0.125
		assertEquals(0.125F, animation.offset(true, start + DURATION / 2), 1.0e-6F);

		// Quarter way: easeOutCubic(0.25) = 1 - 0.75^3 = 0.578125, so the offset is 0.421875
		assertEquals(0.421875F, animation.offset(true, start + DURATION / 4), 1.0e-6F);

		// Finished, and stays finished.
		assertEquals(0.0F, animation.offset(true, start + DURATION));
		assertEquals(0.0F, animation.offset(true, start + 10 * DURATION));
	}

	@Test
	void displacementDecreasesMonotonically() {
		long start = 0L;
		SlideInAnimation animation = new SlideInAnimation(DURATION);
		float previous = animation.offset(true, start);

		for (long elapsed = 10_000_000L; elapsed <= DURATION; elapsed += 10_000_000L) {
			float current = animation.offset(true, start + elapsed);
			assertTrue(current <= previous,
					"offset must not increase, but went from " + previous + " to " + current
							+ " at " + elapsed + "ns");
			previous = current;
		}

		assertEquals(0.0F, previous);
	}

	@Test
	void reopeningRestartsTheSlide() {
		SlideInAnimation animation = new SlideInAnimation(DURATION);

		animation.offset(true, 0L);
		assertEquals(0.0F, animation.offset(true, DURATION));

		// Hide for a frame...
		assertEquals(0.0F, animation.offset(false, DURATION + 1L));

		// ...then show again: back to fully displaced, measured from the new start time.
		long reopenedAt = DURATION + 2L;
		assertEquals(1.0F, animation.offset(true, reopenedAt));
		assertEquals(0.125F, animation.offset(true, reopenedAt + DURATION / 2), 1.0e-6F);
	}

	@Test
	void delayHoldsThePanelBackBeforeMoving() {
		long delay = 200_000_000L;
		SlideInAnimation animation = new SlideInAnimation(DURATION, delay);

		assertEquals(1.0F, animation.offset(true, 0L));
		assertEquals(1.0F, animation.offset(true, delay / 2));
		// Delay consumed, then half of the duration remains.
		assertEquals(0.125F, animation.offset(true, delay + DURATION / 2), 1.0e-6F);
	}

	@Test
	void clockGoingBackwardsDoesNotOvershoot() {
		SlideInAnimation animation = new SlideInAnimation(DURATION);
		animation.offset(true, 1_000L);

		// nowNanos before startNanos must clamp to "fully displaced", not exceed 1.
		assertEquals(1.0F, animation.offset(true, 0L));
	}

	@Test
	void rejectsInvalidConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> new SlideInAnimation(0L));
		assertThrows(IllegalArgumentException.class, () -> new SlideInAnimation(-1L));
		assertThrows(IllegalArgumentException.class, () -> new SlideInAnimation(DURATION, -1L));
	}
}
