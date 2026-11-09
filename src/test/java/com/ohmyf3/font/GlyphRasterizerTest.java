package com.ohmyf3.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class GlyphRasterizerTest {
	/** Builds a closed axis-aligned square contour, in font units (y up). */
	private static GlyphOutline square(int x0, int y0, int x1, int y1) {
		return GlyphOutline.fromGlyfArrays(
				new int[]{3},
				new int[]{x0, x1, x1, x0},
				new int[]{y0, y0, y1, y1},
				new boolean[]{true, true, true, true});
	}

	@Test
	void emptyOutlineProducesNoBitmap() {
		GlyphOutline empty = new GlyphOutline(List.of());
		assertTrue(GlyphRasterizer.rasterize(empty, 0.1F) == null);
		assertTrue(GlyphRasterizer.rasterize(null, 0.1F) == null);
	}

	@Test
	void fillsInteriorSolidlyAndLeavesOutsideEmpty() {
		// 0..1000 font units at 0.01 = 10x10 pixels.
		var bitmap = GlyphRasterizer.rasterize(square(0, 0, 1000, 1000), 0.01F);

		assertNotNull(bitmap);
		assertTrue(bitmap.width >= 10 && bitmap.height >= 10, "bitmap covers the square");

		// Find the pixel column/row range that is actually inside the square.
		int opaque = 0;

		for (byte coverage : bitmap.coverage) {
			if ((coverage & 0xFF) == 255) {
				opaque++;
			}
		}

		// The 10x10 interior should be fully covered.
		assertTrue(opaque >= 100, "expected at least 100 fully covered pixels, got " + opaque);

		// The very first row of the padded bitmap is outside the shape.
		for (int x = 0; x < bitmap.width; x++) {
			assertEquals(0, bitmap.coverage[x] & 0xFF, "top padding row must be empty");
		}
	}

	@Test
	void antialiasesDiagonalEdges() {
		// A triangle: the diagonal must produce partial coverage somewhere.
		GlyphOutline triangle = GlyphOutline.fromGlyfArrays(
				new int[]{2},
				new int[]{0, 1000, 0},
				new int[]{0, 0, 1000},
				new boolean[]{true, true, true});

		var bitmap = GlyphRasterizer.rasterize(triangle, 0.01F);
		assertNotNull(bitmap);

		boolean hasPartial = false;

		for (byte coverage : bitmap.coverage) {
			int value = coverage & 0xFF;

			if (value > 0 && value < 255) {
				hasPartial = true;
				break;
			}
		}

		assertTrue(hasPartial, "diagonal edge should be antialiased");
	}

	@Test
	void curvesAreFlattenedIntoCoverage() {
		// A single quadratic curve closed by two lines: (0,0) -> control (500,1200) -> (1000,0).
		GlyphOutline curve = GlyphOutline.fromGlyfArrays(
				new int[]{3},
				new int[]{0, 500, 1000, 0},
				new int[]{0, 1200, 0, 0},
				new boolean[]{true, false, true, true});

		var bitmap = GlyphRasterizer.rasterize(curve, 0.01F);

		assertNotNull(bitmap);

		int covered = 0;

		for (byte coverage : bitmap.coverage) {
			if ((coverage & 0xFF) > 0) {
				covered++;
			}
		}

		assertTrue(covered > 20, "curve should cover a meaningful area, got " + covered);
	}

	@Test
	void contourOfOnlyOffCurvePointsStillRenders() {
		// Three off-curve points: the flattener has to invent the implied on-curve midpoints.
		GlyphOutline outline = GlyphOutline.fromGlyfArrays(
				new int[]{2},
				new int[]{0, 1000, 500},
				new int[]{0, 0, 800},
				new boolean[]{false, false, false});

		var bitmap = GlyphRasterizer.rasterize(outline, 0.01F);
		assertNotNull(bitmap);

		int covered = 0;

		for (byte coverage : bitmap.coverage) {
			if ((coverage & 0xFF) > 0) {
				covered++;
			}
		}

		assertTrue(covered > 10, "implied midpoints should still enclose an area, got " + covered);
	}

	@Test
	void reportedOffsetsMatchThePixelsThatWereFilled() {
		var bitmap = GlyphRasterizer.rasterize(square(0, 0, 500, 500), 0.01F);
		assertNotNull(bitmap);

		// The square spans x 0..5, y -5..0 in pixel space, plus one pixel of padding.
		assertEquals(-1, bitmap.left);
		assertEquals(-6, bitmap.top);
		assertEquals(7, bitmap.width);
		assertEquals(7, bitmap.height);
	}
}
