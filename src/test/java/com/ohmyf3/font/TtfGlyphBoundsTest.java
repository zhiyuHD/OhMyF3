package com.ohmyf3.font;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Cross-checks parsed outlines against the bounding boxes the font stores in each glyph's
 * {@code glyf} header.
 *
 * <p>This is the regression test for a real bug: the x and y axes of a simple glyph use
 * different flag bits, and decoding both axes with the x bits left every y coordinate
 * corrupted. Bounds are the cheapest way to notice that, and they caught it for all ASCII.
 */
class TtfGlyphBoundsTest {
	private static final int TOLERANCE = 2;

	@Test
	void parsedBoundsMatchDeclaredBoundsForAscii() {
		var path = SystemFonts.find();
		Assumptions.assumeTrue(path != null, "no system TrueType font available");

		TtfFont font;

		try {
			font = TtfFont.parse(path);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}

		int checked = 0;

		for (char c = 32; c < 127; c++) {
			int glyphIndex = font.glyphIndex(c);
			int[] declared = font.declaredBounds(glyphIndex);
			GlyphOutline outline = font.outline(glyphIndex);
			int[] computed = outline == null ? null : outline.bounds();

			if (declared == null || computed == null) {
				assertArrayEquals(declared, computed, "declared/computed bounds disagree for '" + c + "'");
				continue;
			}

			assertNotNull(computed);

			for (int i = 0; i < 4; i++) {
				int delta = Math.abs(declared[i] - computed[i]);

				if (delta > TOLERANCE) {
					throw new AssertionError(String.format(
							"glyph '%c' bounds differ by %d on axis %d: declared=%s computed=%s",
							c, delta, i, java.util.Arrays.toString(declared),
							java.util.Arrays.toString(computed)));
				}
			}

			checked++;
		}

		// Every printable ASCII glyph that has contours should have been compared.
		if (checked < 90) {
			throw new AssertionError("only " + checked + " ASCII glyphs were comparable");
		}
	}

	@Test
	void rasterisedGlyphsHaveSanePixelHeights() {
		var path = SystemFonts.find();
		Assumptions.assumeTrue(path != null, "no system TrueType font available");

		TtfFont font;

		try {
			font = TtfFont.parse(path);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}

		// 9 pixels of em: no ASCII glyph should be taller than a few em.
		float scale = 9.0F / font.unitsPerEm();

		for (char c = 32; c < 127; c++) {
			GlyphOutline outline = font.outline(font.glyphIndex(c));

			if (outline == null) {
				continue;
			}

			var bitmap = GlyphRasterizer.rasterize(outline, scale);

			if (bitmap == null) {
				continue;
			}

			if (bitmap.height > 40) {
				throw new AssertionError("glyph '" + c + "' rasterised to " + bitmap.height
						+ "px tall, which means its coordinates are garbled");
			}
		}
	}
}
