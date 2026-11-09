package com.ohmyf3.client.chart;

import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.ohmyf3.client.font.TtfTextRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ResultField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The F3 profiler's hotspot pie, drawn flat and anti-aliased with the slice names in the system
 * font.
 *
 * <p>Vanilla renders the chart through {@code GuiGraphicsExtractor.profilerChart}, which builds
 * an extruded ("3D") pie out of flat quads per scanline — the curved edges are blocky and there
 * is no text on the slices, only a legend beside it.
 *
 * <p>This draws a 2D pie instead: the slices are rasterised once into a small texture with 3×3
 * supersampling, so the rim and the slice boundaries are smooth, and each slice that is large
 * enough gets its name (and share) printed inside it with the TrueType font.
 */
public final class PieChartRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger("ohmyf3/pie");

	/** Radius in GUI pixels; the chart panel is this wide plus a legend underneath. */
	public static final int RADIUS = 100;

	/** Texture side, in pixels: {@code 2 * RADIUS + 4} keeps a little room for the rim. */
	private static final int SIZE = RADIUS * 2 + 4;

	/** Samples per axis; 3 gives clean edges without a per-frame cost worth caching around. */
	private static final int SUBSAMPLES = 3;

	/** Slices narrower than this (in degrees) get no label — the text would not fit. */
	private static final float LABEL_MIN_DEGREES = 16.0F;

	private static final int LABEL_COLOR = 0xFFFFFFFF;
	private static final int LABEL_SHADOW = 0xC0101010;
	private static final int PANEL_FILL = 0xC8141418;
	private static final int PANEL_BORDER = 0x2AFFFFFF;
	private static final int GAP_COLOR = 0xFF101014;

	private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("ohmyf3", "profiler_pie");

	private static NativeImage image;
	private static DynamicTexture texture;
	private static GpuTextureView view;

	/** Cheap fingerprint of what was last rasterised; the profiler updates every frame. */
	private static String lastKey = "";

	private PieChartRenderer() {
	}

	/**
	 * Draws the chart with its top-left corner at {@code (x, y)}.
	 *
	 * @return the height taken, so the caller can place anything below it
	 */
	public static int render(GuiGraphicsExtractor graphics, ProfileResults results, String treePath, int x, int y) {
		List<ResultField> fields = results == null ? List.of() : results.getTimes(treePath == null ? "" : treePath);

		if (fields.isEmpty()) {
			return 0;
		}

		ensureTexture();

		String key = fingerprint(fields);

		if (!key.equals(lastKey)) {
			rasterise(fields);
			lastKey = key;
		}

		if (view == null) {
			return 0;
		}

		int panelWidth = RADIUS * 2 + 2 * PADDING;

		// Panel behind the pie, so a bright sky cannot wash the labels out.
		graphics.fill(x, y, x + panelWidth, y + SIZE + 2 * PADDING, PANEL_FILL);
		graphics.outline(x, y, x + panelWidth, y + SIZE + 2 * PADDING, PANEL_BORDER);

		int pieX = x + PADDING + 2;
		int pieY = y + PADDING + 2;
		Minecraft minecraft = Minecraft.getInstance();

		if (TtfTextRenderer.sampler() != null) {
			graphics.blit(view, TtfTextRenderer.sampler(), pieX, pieY, pieX + SIZE, pieY + SIZE,
					0.0F, 1.0F, 0.0F, 1.0F);
		}

		drawSliceLabels(graphics, fields, pieX, pieY);

		return SIZE + 2 * PADDING;
	}

	private static final int PADDING = 4;

	/** Percentages rounded to 0.5% keep the cache warm while the numbers drift every frame. */
	private static String fingerprint(List<ResultField> fields) {
		StringBuilder builder = new StringBuilder(fields.size() * 8);

		for (ResultField field : fields) {
			builder.append(field.name).append('|').append(Math.round(field.globalPercentage * 2.0)).append(';');
		}

		return builder.toString();
	}

	private static void ensureTexture() {
		if (image != null) {
			return;
		}

		image = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, true);
		texture = new DynamicTexture(() -> "ohmyf3 profiler pie", image);
		Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
		view = texture.getTextureView();
	}

	/**
	 * Rasterises the slices with supersampled coverage.
	 *
	 * <p>Every sample point is classified by its angle, which keeps the slice boundaries exact
	 * (no scanline stair-stepping) and gives the rim the same coverage-based smoothing as the mod's
	 * glyphs.
	 */
	private static void rasterise(List<ResultField> fields) {
		if (image == null) {
			return;
		}

		double total = 0.0;

		for (ResultField field : fields) {
			total += Math.max(0.0, field.globalPercentage);
		}

		if (total <= 0.0) {
			image.fillRect(0, 0, SIZE, SIZE, 0);
			texture.upload();
			return;
		}

		// Cumulative start angle per slice, in degrees starting at 12 o'clock.
		double[] starts = new double[fields.size()];
		double[] ends = new double[fields.size()];
		int[] colors = new int[fields.size()];
		double cursor = 0.0;

		for (int i = 0; i < fields.size(); i++) {
			double share = Math.max(0.0, fields.get(i).globalPercentage) / total;
			starts[i] = cursor;
			cursor += share * 360.0;
			ends[i] = cursor;
			colors[i] = sliceColor(i, fields.get(i));
		}

		float center = SIZE / 2.0F;
		float radius = RADIUS;

		for (int py = 0; py < SIZE; py++) {
			for (int px = 0; px < SIZE; px++) {
				double rSum = 0;
				double gSum = 0;
				double bSum = 0;
				double aSum = 0;

				for (int sy = 0; sy < SUBSAMPLES; sy++) {
					for (int sx = 0; sx < SUBSAMPLES; sx++) {
						float sampleX = px + (sx + 0.5F) / SUBSAMPLES;
						float sampleY = py + (sy + 0.5F) / SUBSAMPLES;
						float dx = sampleX - center;
						float dy = sampleY - center;
						float distance = (float) Math.sqrt(dx * dx + dy * dy);

						if (distance > radius) {
							continue;
						}

						double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;   // 0° at 12 o'clock

						if (angle < 0) {
							angle += 360.0;
						}

						int slice = sliceAt(starts, ends, angle);

						if (slice < 0) {
							continue;
						}

						int color = colors[slice];

						rSum += (color >> 16 & 0xFF);
						gSum += (color >> 8 & 0xFF);
						bSum += (color & 0xFF);
						aSum += 255;
					}
				}

				double samples = SUBSAMPLES * SUBSAMPLES;
				int alpha = (int) Math.round(aSum / samples);

				if (alpha <= 0) {
					image.setPixel(px, py, 0);
					continue;
				}

				int coverage = (int) Math.round(255.0 * (aSum / (samples * 255.0)));
				int r = (int) Math.round(rSum / (aSum / 255.0));
				int g = (int) Math.round(gSum / (aSum / 255.0));
				int b = (int) Math.round(bSum / (aSum / 255.0));

				image.setPixel(px, py, coverage << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b));
			}
		}

		texture.upload();
	}

	private static int sliceAt(double[] starts, double[] ends, double angle) {
		for (int i = 0; i < starts.length; i++) {
			if (angle >= starts[i] && angle < ends[i]) {
				return i;
			}
		}

		// The last slice owns the 360° wrap-around.
		return starts.length == 0 ? -1 : starts.length - 1;
	}

	private static int sliceColor(int index, ResultField field) {
		// Evenly spread hues read better than vanilla's name-hash greys, and stay stable per slice.
		float hue = (index * 0.6180339F) % 1.0F;
		int rgb = java.awt.Color.HSBtoRGB(hue, 0.55F, 0.95F);
		return 0xFF000000 | rgb;
	}

	private static int clamp(int value) {
		return value < 0 ? 0 : Math.min(value, 255);
	}

	private static void drawSliceLabels(GuiGraphicsExtractor graphics, List<ResultField> fields, int pieX, int pieY) {
		double total = 0.0;

		for (ResultField field : fields) {
			total += Math.max(0.0, field.globalPercentage);
		}

		if (total <= 0.0) {
			return;
		}

		double cursor = 0.0;
		float centerX = pieX + SIZE / 2.0F;
		float centerY = pieY + SIZE / 2.0F;

		for (ResultField field : fields) {
			double share = Math.max(0.0, field.globalPercentage) / total;
			double startDegrees = cursor;
			cursor += share * 360.0;
			double endDegrees = cursor;

			if ((endDegrees - startDegrees) < LABEL_MIN_DEGREES) {
				continue;
			}

			double mid = Math.toRadians((startDegrees + endDegrees) / 2.0 - 90.0);
			float labelRadius = RADIUS * 0.62F;
			float textX = centerX + (float) Math.cos(mid) * labelRadius;
			float textY = centerY + (float) Math.sin(mid) * labelRadius;

			String label = shortName(field.name) + " " + Math.round(share * 100.0) + "%";
			int width = TtfTextRenderer.stringWidth(label);

			// Skip rather than overflow the slice.
			if (width > (endDegrees - startDegrees) / 360.0 * Math.PI * RADIUS * 1.35) {
				continue;
			}

			int baseline = Math.round(textY + TtfTextRenderer.lineHeight() / 2.0F) - 1;
			float left = textX - width / 2.0F;

			// Shadow first: a flat colour on a mid-tone slice is otherwise hard to read.
			TtfTextRenderer.drawString(graphics, label, left + 1, baseline + 1, LABEL_SHADOW);
			TtfTextRenderer.drawString(graphics, label, left, baseline, LABEL_COLOR);
		}
	}

	/** Strips the profiler's nested path down to the last segment. */
	private static String shortName(String name) {
		if (name == null || name.isEmpty()) {
			return "?";
		}

		String demangled = ProfileResults.demanglePath(name);
		int slash = demangled.lastIndexOf('/');

		if (slash >= 0 && slash + 1 < demangled.length()) {
			demangled = demangled.substring(slash + 1);
		}

		return demangled.length() > 18 ? demangled.substring(0, 17) + "…" : demangled;
	}
}
