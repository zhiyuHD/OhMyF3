package com.ohmyf3.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlideAnimationTest {
	private static final long DURATION = 1_000_000_000L;

	@Test
	void startsSettledWithoutAnimating() {
		SlideAnimation animation = new SlideAnimation(DURATION);

		assertEquals(1.0F, animation.update(true, 0L));
		assertEquals(0.0F, new SlideAnimation(DURATION).update(false, 0L));
	}

	@Test
	void opensAndClosesOverTheFullDuration() {
		SlideAnimation animation = new SlideAnimation(DURATION);
		animation.update(false, 0L);
		animation.update(true, 0L); // the segment starts here

		// Ease-out cubic: half the time gets most of the movement (1 - 0.5^3 = 0.875).
		float halfway = animation.update(true, DURATION / 2);
		assertEquals(0.875F, halfway, 1.0e-4F);

		assertEquals(1.0F, animation.update(true, DURATION), 1.0e-6F);

		animation.update(false, DURATION); // start closing exactly when it finished opening
		float closingHalfway = animation.update(false, DURATION + DURATION / 2);
		assertEquals(0.125F, closingHalfway, 1.0e-4F);

		assertEquals(0.0F, animation.update(false, DURATION * 3), 1.0e-6F);
	}

	@Test
	void reversingMidFlightContinuesFromTheCurrentValue() {
		SlideAnimation animation = new SlideAnimation(DURATION);
		animation.update(false, 0L);
		animation.update(true, 0L);

		// Halfway open, then hide again: the value must not jump to 0 or 1.
		float halfway = animation.update(true, DURATION / 2);
		assertTrue(halfway > 0.5F && halfway < 1.0F, "expected a partial value, got " + halfway);

		float afterReversal = animation.update(false, DURATION / 2);
		assertEquals(halfway, afterReversal, 1.0e-6F);

		// And it keeps moving towards closed from there.
		float later = animation.update(false, DURATION / 2 + DURATION / 4);
		assertTrue(later < halfway, "value should decrease after reversing, was " + halfway + " then " + later);

		assertEquals(0.0F, animation.update(false, DURATION * 4));
	}

	@Test
	void reopeningDuringCloseSlidesBackIn() {
		SlideAnimation animation = new SlideAnimation(DURATION);
		animation.update(true, 0L);
		animation.update(false, 0L);

		float closing = animation.update(false, DURATION / 3);
		assertTrue(closing < 1.0F && closing > 0.0F, "expected mid-close, got " + closing);

		float reopening = animation.update(true, DURATION / 3);
		assertEquals(closing, reopening, 1.0e-6F);

		assertEquals(1.0F, animation.update(true, DURATION * 3));
	}

	@Test
	void repeatedTogglesStayContinuous() {
		SlideAnimation animation = new SlideAnimation(DURATION);
		boolean visible = false;
		float previous = animation.update(false, 0L);

		for (int step = 1; step <= 40; step++) {
			if (step % 3 == 0) {
				visible = !visible;
			}

			float value = animation.update(visible, step * 25_000_000L);
			assertTrue(Math.abs(value - previous) <= 0.35F,
					"value jumped from " + previous + " to " + value + " at step " + step);
			assertTrue(value >= 0.0F && value <= 1.0F, "out of range: " + value);
			previous = value;
		}
	}

	@Test
	void rejectsInvalidDuration() {
		assertThrows(IllegalArgumentException.class, () -> new SlideAnimation(0L));
		assertThrows(IllegalArgumentException.class, () -> new SlideAnimation(-5L));
	}
}
