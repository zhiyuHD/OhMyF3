package com.ohmyf3.font;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Glyph contours in font units, as read from the {@code glyf} table.
 *
 * <p>Each contour is a flat {@code float[]} of triples {@code (x, y, onCurve)} where
 * {@code onCurve} is {@code 0} or {@code 1}. Keeping the original on/off-curve points,
 * rather than pre-flattening them, lets the rasterizer pick a subdivision density that suits
 * the requested pixel size.
 *
 * <p>Coordinates follow the font convention: x grows right, <em>y grows up</em>, and the
 * baseline is {@code y = 0}.
 */
public final class GlyphOutline {
	private final List<float[]> contours;

	public GlyphOutline(List<float[]> contours) {
		List<float[]> copy = new ArrayList<>(contours.size());

		for (float[] contour : contours) {
			copy.add(contour.clone());
		}

		this.contours = Collections.unmodifiableList(copy);
	}

	/**
	 * Groups a glyph's points (as stored in the {@code glyf} table) into per-contour arrays.
	 *
	 * @param contourEnds inclusive index of the last point of each contour
	 */
	static GlyphOutline fromGlyfArrays(int[] contourEnds, int[] xs, int[] ys, boolean[] onCurve) {
		if (contourEnds.length == 0) {
			return new GlyphOutline(Collections.emptyList());
		}

		List<float[]> contours = new ArrayList<>(contourEnds.length);
		int first = 0;

		for (int end : contourEnds) {
			int count = end - first + 1;

			if (count <= 0) {
				first = end + 1;
				continue;
			}

			float[] contour = new float[count * 3];

			for (int i = 0; i < count; i++) {
				int point = first + i;
				contour[i * 3] = xs[point];
				contour[i * 3 + 1] = ys[point];
				contour[i * 3 + 2] = onCurve[point] ? 1 : 0;
			}

			contours.add(contour);
			first = end + 1;
		}

		return new GlyphOutline(contours);
	}

	public List<float[]> contours() {
		return this.contours;
	}

	public boolean isEmpty() {
		return this.contours.isEmpty();
	}

	/** Bounds in font units as {@code {minX, minY, maxX, maxY}}, or {@code null} when empty. */
	public int[] bounds() {
		if (this.contours.isEmpty()) {
			return null;
		}

		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;

		for (float[] contour : this.contours) {
			for (int i = 0; i < contour.length; i += 3) {
				minX = Math.min(minX, contour[i]);
				maxX = Math.max(maxX, contour[i]);
				minY = Math.min(minY, contour[i + 1]);
				maxY = Math.max(maxY, contour[i + 1]);
			}
		}

		return new int[]{
				(int) Math.floor(minX), (int) Math.floor(minY),
				(int) Math.ceil(maxX), (int) Math.ceil(maxY)
		};
	}
}
