package com.ohmyf3.font;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the keyword icons, copied one-to-one from the Lucide icon set
 * ({@code lucide-static} 1.70.0, ISC): a 24x24 viewBox, 2px strokes, round caps and joins.
 *
 * <p>Each icon is the original SVG geometry, flattened to polylines by {@link SvgPath} and
 * stroked by distance field. Because the stroke has round caps, a zero-length segment such
 * as Lucide's {@code M6 18h.01} renders as a dot without any special case.
 *
 * <p>Sampling is {@value #SUBSAMPLES}x{@value #SUBSAMPLES} per pixel, so the strokes are
 * antialiased the same way the text is.
 */
public final class IconPainter {
	/** Lucide's design grid. */
	public static final float VIEWBOX = 24.0F;

	/** Lucide's stroke width on that grid. */
	private static final float STROKE_WIDTH = 2.0F;

	private static final int SUBSAMPLES = 8;

	private IconPainter() {
	}

	/** The icons this mod knows how to draw. */
	public enum Icon {
		/** {@code gauge}: speedometer needle in a half dial. */
		GAUGE,
		/** {@code memory-stick}: RAM stick. */
		MEMORY,
		/** {@code layout-grid}: four tiles. */
		GRID,
		/** {@code crosshair}: targeting reticle. */
		CROSSHAIR,
		/** {@code zap}: lightning bolt. */
		BOLT,
		/** Sodium's own logo: a droplet with an M-shaped notch. */
		SODIUM,
		/** {@code cpu}: processor. */
		CHIP,
		/** {@code coffee}: cup with steam. */
		CUP,
		/** {@code dices}: two dice. */
		DICE
	}

	/**
	 * Rasterises an icon.
	 *
	 * @param pixelSize edge length in pixels at the current device scale
	 */
	public static CoverageBitmap paint(Icon icon, int pixelSize) {
		if (icon == Icon.SODIUM) {
			return paintSodiumLogo(pixelSize);
		}

		List<float[]> geometry = geometry(icon);

		float scale = pixelSize / VIEWBOX;
		float halfStroke = STROKE_WIDTH * scale * 0.5F;

		byte[] coverage = new byte[pixelSize * pixelSize];
		int samples = SUBSAMPLES * SUBSAMPLES;

		for (int py = 0; py < pixelSize; py++) {
			for (int px = 0; px < pixelSize; px++) {
				int inside = 0;

				for (int sy = 0; sy < SUBSAMPLES; sy++) {
					for (int sx = 0; sx < SUBSAMPLES; sx++) {
						float x = (px + (sx + 0.5F) / SUBSAMPLES) / scale;
						float y = (py + (sy + 0.5F) / SUBSAMPLES) / scale;

						if (distanceToGeometry(geometry, x, y) <= STROKE_WIDTH * 0.5F) {
							inside++;
						}
					}
				}

				coverage[py * pixelSize + px] = (byte) Math.round(255.0F * inside / samples);
			}
		}

		return new CoverageBitmap(pixelSize, pixelSize, 0, 0, coverage);
	}

	/** Samples Sodium's embedded coverage mask into an icon bitmap. */
	private static CoverageBitmap paintSodiumLogo(int pixelSize) {
		byte[] coverage = new byte[pixelSize * pixelSize];
		int samples = SUBSAMPLES * SUBSAMPLES;

		for (int py = 0; py < pixelSize; py++) {
			for (int px = 0; px < pixelSize; px++) {
				int total = 0;

				for (int sy = 0; sy < SUBSAMPLES; sy++) {
					for (int sx = 0; sx < SUBSAMPLES; sx++) {
						float u = (px + (sx + 0.5F) / SUBSAMPLES) / pixelSize;
						float v = (py + (sy + 0.5F) / SUBSAMPLES) / pixelSize;
						total += SodiumLogo.coverage(u, v);
					}
				}

				coverage[py * pixelSize + px] = (byte) Math.round((float) total / samples);
			}
		}

		return new CoverageBitmap(pixelSize, pixelSize, 0, 0, coverage);
	}

	/** Smallest distance from a point to any stroked segment, in viewBox units. */
	private static float distanceToGeometry(List<float[]> geometry, float x, float y) {
		float best = Float.MAX_VALUE;

		for (float[] polyline : geometry) {
			for (int i = 0; i + 3 < polyline.length; i += 2) {
				best = Math.min(best, segmentDistance(x, y, polyline[i], polyline[i + 1], polyline[i + 2],
						polyline[i + 3]));

				if (best == 0) {
					return 0;
				}
			}
		}

		return best;
	}

	private static float segmentDistance(float x, float y, float x1, float y1, float x2, float y2) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float lengthSquared = dx * dx + dy * dy;

		float t = lengthSquared == 0 ? 0
				: ((x - x1) * dx + (y - y1) * dy) / lengthSquared;
		t = Math.max(0, Math.min(1, t));

		float nearestX = x1 + t * dx;
		float nearestY = y1 + t * dy;
		float ddx = x - nearestX;
		float ddy = y - nearestY;

		return (float) Math.sqrt(ddx * ddx + ddy * ddy);
	}

	// ---------------------------------------------------------------- Lucide geometry

	private static List<float[]> geometry(Icon icon) {
		List<float[]> shapes = new ArrayList<>();

		switch (icon) {
			case GAUGE -> {
				shapes.addAll(SvgPath.parse("m12 14 4-4"));
				shapes.addAll(SvgPath.parse("M3.34 19a10 10 0 1 1 17.32 0"));
			}
			case MEMORY -> {
				shapes.addAll(SvgPath.parse("M12 12v-2"));
				shapes.addAll(SvgPath.parse("M12 18v-2"));
				shapes.addAll(SvgPath.parse("M16 12v-2"));
				shapes.addAll(SvgPath.parse("M16 18v-2"));
				shapes.addAll(SvgPath.parse("M2 11h1.5"));
				shapes.addAll(SvgPath.parse("M20 18v-2"));
				shapes.addAll(SvgPath.parse("M20.5 11H22"));
				shapes.addAll(SvgPath.parse("M4 18v-2"));
				shapes.addAll(SvgPath.parse("M8 12v-2"));
				shapes.addAll(SvgPath.parse("M8 18v-2"));
				shapes.add(SvgPath.rect(2, 6, 20, 10, 2));
			}
			case GRID -> {
				shapes.add(SvgPath.rect(3, 3, 7, 7, 1));
				shapes.add(SvgPath.rect(14, 3, 7, 7, 1));
				shapes.add(SvgPath.rect(14, 14, 7, 7, 1));
				shapes.add(SvgPath.rect(3, 14, 7, 7, 1));
			}
			case CROSSHAIR -> {
				shapes.add(SvgPath.circle(12, 12, 10));
				shapes.add(SvgPath.line(22, 12, 18, 12));
				shapes.add(SvgPath.line(6, 12, 2, 12));
				shapes.add(SvgPath.line(12, 6, 12, 2));
				shapes.add(SvgPath.line(12, 22, 12, 18));
			}
			case BOLT -> shapes.addAll(SvgPath.parse(
					"M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20"
							+ "a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z"));
			case CHIP -> {
				for (String path : new String[]{
						"M12 20v2", "M12 2v2", "M17 20v2", "M17 2v2", "M2 12h2", "M2 17h2",
						"M2 7h2", "M20 12h2", "M20 17h2", "M20 7h2", "M7 20v2", "M7 2v2"}) {
					shapes.addAll(SvgPath.parse(path));
				}

				shapes.add(SvgPath.rect(4, 4, 16, 16, 2));
				shapes.add(SvgPath.rect(8, 8, 8, 8, 1));
			}
			case CUP -> {
				shapes.addAll(SvgPath.parse("M10 2v2"));
				shapes.addAll(SvgPath.parse("M14 2v2"));
				shapes.addAll(SvgPath.parse("M6 2v2"));
				shapes.addAll(SvgPath.parse(
						"M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h14a4 4 0 1 1 0 8h-1"));
			}
			case DICE -> {
				shapes.add(SvgPath.rect(2, 10, 12, 12, 2));
				shapes.addAll(SvgPath.parse(
						"m17.92 14 3.5-3.5a2.24 2.24 0 0 0 0-3l-5-4.92a2.24 2.24 0 0 0-3 0L10 6"));
				shapes.addAll(SvgPath.parse("M6 18h.01"));
				shapes.addAll(SvgPath.parse("M10 14h.01"));
				shapes.addAll(SvgPath.parse("M15 6h.01"));
				shapes.addAll(SvgPath.parse("M18 9h.01"));
			}
			default -> throw new IllegalArgumentException("unhandled icon " + icon);
		}

		return shapes;
	}
}
