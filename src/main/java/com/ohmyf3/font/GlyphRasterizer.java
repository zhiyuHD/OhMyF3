package com.ohmyf3.font;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns glyph outlines into antialiased 8-bit coverage bitmaps.
 *
 * <p>Quadratic Bézier segments are flattened adaptively, then filled with the non-zero
 * winding rule (which is what TrueType requires). Horizontal coverage is computed exactly
 * per pixel, and each pixel row is sampled {@value #SUBSAMPLES} times vertically, which is
 * plenty of antialiasing for a HUD font and keeps rasterisation cheap enough to do lazily
 * per glyph.
 */
public final class GlyphRasterizer {
	private static final int SUBSAMPLES = 8;

	private GlyphRasterizer() {
	}

	/**
	 * Rasterises an outline.
	 *
	 * @param scale pixels per font unit, i.e. {@code pixelSize / unitsPerEm}
	 */
	public static CoverageBitmap rasterize(GlyphOutline outline, float scale) {
		if (outline == null || outline.isEmpty()) {
			return null;
		}

		// Convert to pixel space, flipping y because the font's y axis points up.
		List<float[]> pixelContours = new ArrayList<>(outline.contours().size());
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;

		for (float[] contour : outline.contours()) {
			float[] converted = new float[contour.length];

			for (int i = 0; i < contour.length; i += 3) {
				float x = contour[i] * scale;
				float y = -contour[i + 1] * scale;
				converted[i] = x;
				converted[i + 1] = y;
				converted[i + 2] = contour[i + 2];

				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}

			pixelContours.add(converted);
		}

		if (minX > maxX) {
			return null;
		}

		// One pixel of padding on every side so the antialiased edge is never clipped.
		int left = (int) Math.floor(minX) - 1;
		int top = (int) Math.floor(minY) - 1;
		int right = (int) Math.ceil(maxX) + 1;
		int bottom = (int) Math.ceil(maxY) + 1;

		int width = Math.max(1, right - left);
		int height = Math.max(1, bottom - top);

		List<float[]> edges = new ArrayList<>();
		flatten(pixelContours, edges);

		if (edges.isEmpty()) {
			return null;
		}

		float[] coverage = new float[width * height];
		fill(edges, width, height, left, top, coverage);

		byte[] bytes = new byte[coverage.length];

		for (int i = 0; i < coverage.length; i++) {
			float c = Math.min(1.0F, Math.max(0.0F, coverage[i]));
			bytes[i] = (byte) Math.round(c * 255.0F);
		}

		return new CoverageBitmap(width, height, left, top, bytes);
	}

	// ---------------------------------------------------------------- outline → edges

	private static void flatten(List<float[]> contours, List<float[]> edges) {
		for (float[] contour : contours) {
			int points = contour.length / 3;

			if (points < 2) {
				continue;
			}

			int startIndex = -1;

			for (int i = 0; i < points; i++) {
				if (contour[i * 3 + 2] != 0) {
					startIndex = i;
					break;
				}
			}

			float startX;
			float startY;

			if (startIndex >= 0) {
				startX = contour[startIndex * 3];
				startY = contour[startIndex * 3 + 1];
			} else {
				// A contour made only of off-curve points starts at the implied midpoint.
				startIndex = 0;
				startX = (contour[0] + contour[3]) * 0.5F;
				startY = (contour[1] + contour[4]) * 0.5F;
			}

			float currentX = startX;
			float currentY = startY;

			// Walk the contour once, wrapping at the end.
			for (int step = 1; step <= points; step++) {
				int index = (startIndex + step) % points;
				float x = contour[index * 3];
				float y = contour[index * 3 + 1];
				boolean onCurve = contour[index * 3 + 2] != 0;

				if (onCurve) {
					addLine(edges, currentX, currentY, x, y);
					currentX = x;
					currentY = y;
					continue;
				}

				int nextIndex = (startIndex + step + 1) % points;
				float nextX = contour[nextIndex * 3];
				float nextY = contour[nextIndex * 3 + 1];
				boolean nextOnCurve = contour[nextIndex * 3 + 2] != 0;

				if (nextOnCurve) {
					addQuadratic(edges, currentX, currentY, x, y, nextX, nextY);
					currentX = nextX;
					currentY = nextY;
					step++; // the next point was consumed as the curve end
				} else {
					// Two consecutive off-curve points imply an on-curve point between them.
					float midX = (x + nextX) * 0.5F;
					float midY = (y + nextY) * 0.5F;
					addQuadratic(edges, currentX, currentY, x, y, midX, midY);
					currentX = midX;
					currentY = midY;
				}
			}

			// Close the contour.
			addLine(edges, currentX, currentY, startX, startY);
		}
	}

	private static void addLine(List<float[]> edges, float x0, float y0, float x1, float y1) {
		if (y0 == y1) {
			return; // horizontal edges never cross a scanline
		}

		edges.add(new float[]{x0, y0, x1, y1});
	}

	private static void addQuadratic(List<float[]> edges, float x0, float y0, float cx, float cy, float x1,
			float y1) {
		// Subdivision count grows with the curve's chord length so big glyphs stay smooth.
		float length = Math.abs(x0 - cx) + Math.abs(y0 - cy) + Math.abs(cx - x1) + Math.abs(cy - y1);
		int steps = Math.max(4, Math.min(32, Math.round(length * 1.5F)));

		float previousX = x0;
		float previousY = y0;

		for (int i = 1; i <= steps; i++) {
			float t = (float) i / steps;
			float inverse = 1.0F - t;
			float x = inverse * inverse * x0 + 2 * inverse * t * cx + t * t * x1;
			float y = inverse * inverse * y0 + 2 * inverse * t * cy + t * t * y1;

			addLine(edges, previousX, previousY, x, y);
			previousX = x;
			previousY = y;
		}
	}

	// ---------------------------------------------------------------- scanline fill

	private static void fill(List<float[]> edges, int width, int height, int left, int top, float[] coverage) {
		List<float[]> crossings = new ArrayList<>(32);

		for (int row = 0; row < height; row++) {
			for (int sample = 0; sample < SUBSAMPLES; sample++) {
				float y = top + row + (sample + 0.5F) / SUBSAMPLES;
				crossings.clear();

				for (float[] edge : edges) {
					float y0 = edge[1];
					float y1 = edge[3];

					boolean crosses = y0 <= y && y < y1 || y1 <= y && y < y0;

					if (!crosses) {
						continue;
					}

					float t = (y - y0) / (y1 - y0);
					float x = edge[0] + t * (edge[2] - edge[0]);
					crossings.add(new float[]{x, y1 > y0 ? 1.0F : -1.0F});
				}

				if (crossings.isEmpty()) {
					continue;
				}

				crossings.sort((a, b) -> Float.compare(a[0], b[0]));

				int winding = 0;
				float spanStart = 0;

				for (float[] crossing : crossings) {
					if (winding == 0) {
						spanStart = crossing[0];
					}

					winding += (int) crossing[1];

					if (winding == 0) {
						addSpan(coverage, width, row, spanStart - left, crossing[0] - left);
					}
				}
			}
		}

		// Average the vertical subsamples.
		float scale = 1.0F / SUBSAMPLES;

		for (int i = 0; i < coverage.length; i++) {
			coverage[i] *= scale;
		}
	}

	private static void addSpan(float[] coverage, int width, int row, float x0, float x1) {
		if (x1 <= x0) {
			return;
		}

		int first = (int) Math.floor(x0);
		int last = (int) Math.ceil(x1) - 1;

		first = Math.max(0, first);
		last = Math.min(width - 1, last);

		int base = row * width;

		for (int x = first; x <= last; x++) {
			float overlap = Math.min(x1, x + 1.0F) - Math.max(x0, x);

			if (overlap > 0) {
				coverage[base + x] += overlap;
			}
		}
	}
}
