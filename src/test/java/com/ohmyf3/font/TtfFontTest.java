package com.ohmyf3.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Parser tests against whatever TrueType font this machine provides. They skip themselves on
 * systems without one, so the build stays portable.
 */
class TtfFontTest {
	private static TtfFont systemFont() {
		var path = SystemFonts.find();
		Assumptions.assumeTrue(path != null, "no system TrueType font available");

		try {
			return TtfFont.parse(path);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	@Test
	void readsCoreMetrics() {
		TtfFont font = systemFont();

		assertTrue(font.unitsPerEm() > 0, "unitsPerEm");
		assertTrue(font.glyphCount() > 100, "glyph count");
		assertTrue(font.ascender() > 0, "ascender should be above the baseline");
		assertTrue(font.descender() < 0, "descender should be below the baseline");
	}

	@Test
	void mapsAsciiToRealGlyphs() {
		TtfFont font = systemFont();

		for (char c = 'A'; c <= 'Z'; c++) {
			assertTrue(font.glyphIndex(c) > 0, "no glyph for " + c);
		}

		// Space is blank but must still exist and advance the pen.
		int space = font.glyphIndex(' ');
		assertTrue(space > 0, "no glyph for space");
		assertTrue(font.advanceWidth(space) > 0, "space must have an advance");
	}

	@Test
	void rejectsUnmappedCodePointsWithoutCrashing() {
		TtfFont font = systemFont();

		// A private-use code point that essentially no font covers.
		assertEquals(0, font.glyphIndex(0xF8FF));
	}

	@Test
	void producesOutlinesForLetters() {
		TtfFont font = systemFont();

		GlyphOutline outline = font.outline(font.glyphIndex('A'));
		assertNotNull(outline, "'A' should have an outline");
		assertTrue(!outline.isEmpty(), "'A' outline should have contours");

		int[] bounds = outline.bounds();
		assertNotNull(bounds);
		assertTrue(bounds[2] > bounds[0], "outline width");
		assertTrue(bounds[3] > bounds[1], "outline height");
	}

	@Test
	void blankGlyphsHaveNoOutline() {
		TtfFont font = systemFont();

		GlyphOutline space = font.outline(font.glyphIndex(' '));
		assertTrue(space == null || space.isEmpty(), "space should have no contours");
	}

	@Test
	void parsesCompositeGlyphs() {
		TtfFont font = systemFont();

		// Composites are built from other glyphs and exercise the recursion.
		// 'Ä' is almost always composite in a Latin font; if the font lacks it, 'Á' is tried.
		int accented = font.glyphIndex('Ä');

		if (accented == 0) {
			accented = font.glyphIndex('Á');
		}

		Assumptions.assumeTrue(accented > 0, "font has no accented glyph to test");

		GlyphOutline outline = font.outline(accented);
		assertNotNull(outline);
		assertTrue(!outline.isEmpty());
	}
}
