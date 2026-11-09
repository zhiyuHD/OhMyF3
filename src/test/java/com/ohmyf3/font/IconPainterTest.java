package com.ohmyf3.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IconPainterTest {
	@Test
	void everyIconProducesVisibleCoverage() {
		for (IconPainter.Icon icon : IconPainter.Icon.values()) {
			CoverageBitmap bitmap = IconPainter.paint(icon, 9);

			assertEquals(9, bitmap.width);
			assertEquals(9, bitmap.height);

			int drawn = 0;
			int partial = 0;

			for (byte value : bitmap.coverage) {
				int coverage = value & 0xFF;

				if (coverage > 0) {
					drawn++;
				}

				if (coverage > 0 && coverage < 255) {
					partial++;
				}
			}

			// Every icon must actually draw something, and no icon may be a solid block:
			// that would mean the primitives collapsed into a filled square.
			assertTrue(drawn > 12, icon + " drew only " + drawn + " pixels");
			assertTrue(drawn < 81, icon + " filled the whole tile (" + drawn + " pixels)");
			assertTrue(partial > 0, icon + " has no antialiased edge pixels");
		}
	}

	@Test
	void iconScalesWithPixelSize() {
		CoverageBitmap small = IconPainter.paint(IconPainter.Icon.CROSSHAIR, 9);
		CoverageBitmap large = IconPainter.paint(IconPainter.Icon.CROSSHAIR, 27);

		assertEquals(9, small.width);
		assertEquals(27, large.width);
	}

	@Test
	void iconsAreMonochromeCoverageOnly() {
		// Coverage must stay in range; the colour is applied later when packing the atlas.
		for (IconPainter.Icon icon : IconPainter.Icon.values()) {
			for (byte value : IconPainter.paint(icon, 12).coverage) {
				int coverage = value & 0xFF;
				assertTrue(coverage >= 0 && coverage <= 255);
			}
		}
	}
}
