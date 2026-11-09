package com.ohmyf3.client.ui;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.ohmyf3.client.font.TtfTextRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Rounded rectangles for the Material 3 surfaces.
 *
 * <p>The GUI extractor can only fill axis-aligned rectangles, so rounded shapes are rasterised into
 * a texture and blitted. Coverage is analytic (a signed distance field) rather than blocky: the
 * edge alpha is {@code 0.5 - sdf}, which gives the same smoothing as the glyphs without
 * supersampling.
 *
 * <p>Textures are cached per shape and colour, so a screen with a dozen rows only builds a handful
 * of them, once.
 */
public final class Shapes {
	private static final Map<String, Shape> CACHE = new HashMap<>();

	private static int nextId;

	private Shapes() {
	}

	/** Filled rounded rectangle with a 1px outline of the same colour, for crisp edges. */
	public static void filled(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
			int fill) {
		blit(graphics, x, y, width, height, radius, fill, fill, false);
	}

	/** Rounded rectangle with an explicit outline colour. */
	public static void outlined(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
			int fill, int outline) {
		blit(graphics, x, y, width, height, radius, fill, outline, true);
	}

	/** A circle, used for switch thumbs and radio dots. */
	public static void circle(GuiGraphicsExtractor graphics, float centerX, float centerY, float radius, int color) {
		int size = Math.round(radius * 2.0F);

		if (size <= 0) {
			return;
		}

		blit(graphics, Math.round(centerX - radius), Math.round(centerY - radius), size, size,
				Math.max(1, size / 2), color, color, false);
	}

	/** Dashed-looking outline used while the layout is being edited. */
	public static void dragOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		for (int i = 0; i < width; i += 4) {
			graphics.fill(x + i, y, x + Math.min(i + 2, width), y + 1, color);
			graphics.fill(x + i, y + height - 1, x + Math.min(i + 2, width), y + height, color);
		}

		for (int j = 0; j < height; j += 4) {
			graphics.fill(x, y + j, x + 1, y + Math.min(j + 2, height), color);
			graphics.fill(x + width - 1, y + j, x + width, y + Math.min(j + 2, height), color);
		}
	}

	private static void blit(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
			int fill, int outline, boolean hasOutline) {
		if (width <= 0 || height <= 0) {
			return;
		}

		GpuSampler sampler = TtfTextRenderer.sampler();

		if (sampler == null) {
			graphics.fill(x, y, x + width, y + height, fill);
			return;
		}

		Shape shape = CACHE.get(key(width, height, radius, fill, outline, hasOutline));

		if (shape == null) {
			shape = build(width, height, radius, fill, outline, hasOutline);
			CACHE.put(key(width, height, radius, fill, outline, hasOutline), shape);
		}

		if (shape.view != null) {
			graphics.blit(shape.view, sampler, x, y, x + width, y + height, 0.0F, 1.0F, 0.0F, 1.0F);
		}
	}

	private static String key(int width, int height, int radius, int fill, int outline, boolean hasOutline) {
		return width + "x" + height + "r" + radius + "f" + fill + "o" + outline + (hasOutline ? "1" : "0");
	}

	private static Shape build(int width, int height, int radius, int fill, int outline, boolean hasOutline) {
		int scale = Math.max(1, Math.round(deviceScale()));
		int pixelWidth = width * scale;
		int pixelHeight = height * scale;
		float corner = Math.min(Math.min(radius, width / 2.0F), height / 2.0F) * scale;

		NativeImage image = new NativeImage(NativeImage.Format.RGBA, pixelWidth, pixelHeight, true);

		int fillA = fill >>> 24 & 0xFF;
		int fillR = fill >> 16 & 0xFF;
		int fillG = fill >> 8 & 0xFF;
		int fillB = fill & 0xFF;
		int outA = outline >>> 24 & 0xFF;
		int outR = outline >> 16 & 0xFF;
		int outG = outline >> 8 & 0xFF;
		int outB = outline & 0xFF;

		for (int py = 0; py < pixelHeight; py++) {
			for (int px = 0; px < pixelWidth; px++) {
				float sampleX = px + 0.5F;
				float sampleY = py + 0.5F;
				float distance = roundedRectDistance(sampleX, sampleY, pixelWidth, pixelHeight, corner);
				float coverage = clamp01(0.5F - distance);

				if (coverage <= 0.0F) {
					continue;
				}

				int r = fillR;
				int g = fillG;
				int b = fillB;
				int a = fillA;

				if (hasOutline) {
					// Outline occupies the outermost pixel of the shape.
					float ring = clamp01(0.5F - Math.abs(distance + scale * 0.5F) + scale * 0.5F);
					float mix = Math.max(0.0F, Math.min(1.0F, ring));

					if (mix > 0.0F) {
						r = Math.round(fillR + (outR - fillR) * mix);
						g = Math.round(fillG + (outG - fillG) * mix);
						b = Math.round(fillB + (outB - fillB) * mix);
						a = Math.round(fillA + (outA - fillA) * mix);
					}
				}

				image.setPixel(px, py, Math.round(a * coverage) << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b));
			}
		}

		Shape shape = new Shape();
		shape.image = image;
		shape.texture = new DynamicTexture(() -> "ohmyf3 shape " + nextId++, image);
		Identifier id = Identifier.fromNamespaceAndPath("ohmyf3", "shape_" + nextId);
		Minecraft.getInstance().getTextureManager().register(id, shape.texture);
		shape.view = shape.texture.getTextureView();
		return shape;
	}

	/** Signed distance to a rounded rectangle; negative inside, positive outside. */
	private static float roundedRectDistance(float x, float y, float width, float height, float radius) {
		float halfWidth = width / 2.0F;
		float halfHeight = height / 2.0F;
		float dx = Math.abs(x - halfWidth) - (halfWidth - radius);
		float dy = Math.abs(y - halfHeight) - (halfHeight - radius);
		float outsideX = Math.max(dx, 0.0F);
		float outsideY = Math.max(dy, 0.0F);
		return (float) Math.sqrt(outsideX * outsideX + outsideY * outsideY)
				+ Math.min(Math.max(dx, dy), 0.0F) - radius;
	}

	private static float clamp01(float value) {
		return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
	}

	private static int clamp(int value) {
		return value < 0 ? 0 : Math.min(value, 255);
	}

	private static float deviceScale() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.getWindow().getGuiScale();
	}

	private static final class Shape {
		private NativeImage image;
		private DynamicTexture texture;
		private GpuTextureView view;
	}
}
