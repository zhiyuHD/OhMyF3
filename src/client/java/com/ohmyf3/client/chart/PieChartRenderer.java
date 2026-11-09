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

/**
 * The F3+L profiler hotspot pie, drawn as a flat anti-aliased 2D chart with the slice names in
 * the system font.
 *
 * <p>Vanilla fills the same rectangle through {@code GuiGraphicsExtractor.profilerChart}: an
 * extruded ("3D") pie assembled per scanline, so the curved rim is stair-stepped and the slices
 * carry no text at all — everything is in a legend beside it.
 *
 * <p>This only replaces the pie itself; the caller keeps its layout, legend and tree navigation.
 * Slices are rasterised into a small texture with 3×3 supersampling, so the rim and the boundaries
 * are coverage-based rather than blocky, and each slice wide enough to hold its label gets
 * {@code name 42%} printed inside it.
 */
public final class PieChartRenderer {
	/** Samples per axis; 3 is enough for a smooth rim without a per-frame cost. */
	private static final int SUBSAMPLES = 3;

	/** Slices narrower than this (in degrees) get no label — the text would not fit. */
	private static final float LABEL_MIN_DEGREES = 16.0F;

	private static final int LABEL_COLOR = 0xFFFFFFFF;
	private static final int LABEL_SHADOW = 0xC0101010;
	private static final int RIM_COLOR = 0xFF101014;

	private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("ohmyf3", "profiler_pie");

	private static NativeImage image;
	private static DynamicTexture texture;
	private static GpuTextureView view;

	/** Cheap fingerprint of what was last rasterised; the profiler updates every frame. */
	private static String lastKey = "";

	private PieChartRenderer() {
	}

	/**
	 * Draws the pie inside {@code [x1,y1]-[x2,y2]}.
	 *
	 * @return false when nothing could be drawn, so the caller can fall back to vanilla
	 */
	public static boolean render(GuiGraphicsExtractor graphics, List<ResultField> fields,
			int x1, int y1, int x2, int y2) {
		if (fields == null || fields.isEmpty() || TtfTextRenderer.sampler() == null) {
			return false;
		}

		float radius = Math.min(x2 - x1, y2 - y1) / 2.0F;

		if (radius < 6.0F) {
			return false;
		}

		int size = Math.round(radius * 2.0F);

		if (!ensureTexture(size)) {
			return false;
		}

		double total = 0.0;

		for (ResultField field : fields) {
			total += Math.max(0.0, field.globalPercentage);
		}

		if (total <= 0.0) {
			return false;
		}

		String key = size + ":" + fingerprint(fields);

		if (!key.equals(lastKey)) {
			rasterise(fields, total, size);
			lastKey = key;
		}

		float centerX = (x1 + x2) / 2.0F;
		float centerY = (y1 + y2) / 2.0F;
		int left = Math.round(centerX - size / 2.0F);
		int top = Math.round(centerY - size / 2.0F);

		graphics.blit(view, TtfTextRenderer.sampler(), left, top, left + size, top + size,
				0.0F, 1.0F, 0.0F, 1.0F);
		drawSliceLabels(graphics, fields, total, centerX, centerY, radius);
		return true;
	}

	/** Percentages rounded to 0.5% keep the cache warm while the numbers drift every frame. */
	private static String fingerprint(List<ResultField> fields) {
		StringBuilder builder = new StringBuilder(fields.size() * 8);

		for (ResultField field : fields) {
			builder.append(field.name).append('|').append(Math.round(field.globalPercentage * 2.0)).append(';');
		}

		return builder.toString();
	}

	private static boolean ensureTexture(int size) {
		if (image != null && image.getWidth() == size) {
			return true;
		}

		if (image != null) {
			// The chart is re-laid out when the GUI scale changes; a fresh texture is cheapest.
			texture.close();
			image = null;
			view = null;
			lastKey = "";
		}

		image = new NativeImage(NativeImage.Format.RGBA, size, size, true);
		texture = new DynamicTexture(() -> "ohmyf3 profiler pie", image);
		Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
		view = texture.getTextureView();
		return view != null;
	}

	/**
	 * Rasterises the slices with supersampled coverage: every sample point is classified by its
	 * angle, so boundaries are exact instead of stair-stepped, and the rim is smoothed the same way
	 * the mod's glyphs are.
	 */
	private static void rasterise(List<ResultField> fields, double total, int size) {
		double[] starts = new double[fields.size()];
		double[] ends = new double[fields.size()];
		int[] colors = new int[fields.size()];
		double cursor = 0.0;

		for (int i = 0; i < fields.size(); i++) {
			double share = Math.max(0.0, fields.get(i).globalPercentage) / total;
			starts[i] = cursor;
			cursor += share * 360.0;
			ends[i] = cursor;
			colors[i] = sliceColor(i);
		}

		float center = size / 2.0F;
		float radius = size / 2.0F - 1.0F;

		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				double rSum = 0;
				double gSum = 0;
				double bSum = 0;
				int covered = 0;

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
						rSum += color >> 16 & 0xFF;
						gSum += color >> 8 & 0xFF;
						bSum += color & 0xFF;
						covered++;
					}
				}

				if (covered == 0) {
					image.setPixel(px, py, 0);
					continue;
				}

				int samples = SUBSAMPLES * SUBSAMPLES;
				int alpha = Math.round(255.0F * covered / samples);
				int r = clamp((int) Math.round(rSum / covered));
				int g = clamp((int) Math.round(gSum / covered));
				int b = clamp((int) Math.round(bSum / covered));

				image.setPixel(px, py, alpha << 24 | r << 16 | g << 8 | b);
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

		return starts.length == 0 ? -1 : starts.length - 1;
	}

	private static int sliceColor(int index) {
		// Evenly spread hues read better than vanilla's name-hash greys, and stay stable per slice.
		float hue = (index * 0.6180339F) % 1.0F;
		return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.55F, 0.95F);
	}

	private static int clamp(int value) {
		return value < 0 ? 0 : Math.min(value, 255);
	}

	private static void drawSliceLabels(GuiGraphicsExtractor graphics, List<ResultField> fields, double total,
			float centerX, float centerY, float radius) {
		double cursor = 0.0;

		for (ResultField field : fields) {
			double share = Math.max(0.0, field.globalPercentage) / total;
			double startDegrees = cursor;
			cursor += share * 360.0;
			double endDegrees = cursor;

			if ((endDegrees - startDegrees) < LABEL_MIN_DEGREES) {
				continue;
			}

			double mid = Math.toRadians((startDegrees + endDegrees) / 2.0 - 90.0);
			float labelRadius = radius * 0.62F;
			float textX = centerX + (float) Math.cos(mid) * labelRadius;
			float textY = centerY + (float) Math.sin(mid) * labelRadius;

			String label = shortName(field.name) + " " + Math.round(share * 100.0) + "%";
			int width = TtfTextRenderer.stringWidth(label);

			// The chord the label has to fit across; skip rather than overflow into a neighbour.
			if (width > (endDegrees - startDegrees) / 360.0 * Math.PI * radius * 1.35) {
				continue;
			}

			int baseline = Math.round(textY + TtfTextRenderer.lineHeight() / 2.0F) - 1;
			float left = textX - width / 2.0F;

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
