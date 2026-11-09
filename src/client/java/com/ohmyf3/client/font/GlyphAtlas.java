package com.ohmyf3.client.font;

import com.mojang.blaze3d.platform.NativeImage;

import com.ohmyf3.font.CoverageBitmap;

/**
 * A shelf-packed glyph atlas backed by a {@link NativeImage}.
 *
 * <p>Glyphs are stored as light-grey pixels whose alpha carries the rasterizer's coverage.
 * The tint cannot be supplied at draw time in 26.3's GUI pipeline, so the text colour is
 * baked in here; that is fine because the debug screen uses a single text colour for every
 * line, including lines contributed by other mods.
 */
final class GlyphAtlas {
	static final int SIZE = 1024;
	private static final int PADDING = 1;

	private final NativeImage image;
	private int cursorX;
	private int cursorY;
	private int rowHeight;
	private boolean dirty;

	GlyphAtlas(NativeImage image) {
		this.image = image;
	}

	NativeImage image() {
		return this.image;
	}

	boolean isDirty() {
		return this.dirty;
	}

	void clearDirty() {
		this.dirty = false;
	}

	/**
	 * Copies a rasterised glyph into the atlas.
	 *
	 * @param colour ARGB colour to bake into the glyph's opaque pixels
	 * @return the placed rectangle, or {@code null} when the atlas is full
	 */
	Rect add(CoverageBitmap bitmap, int colour) {
		if (bitmap.width + PADDING * 2 > SIZE || bitmap.height + PADDING * 2 > SIZE) {
			return null; // a single glyph larger than the atlas cannot be stored
		}

		if (this.cursorX + bitmap.width + PADDING > SIZE) {
			this.cursorX = PADDING;
			this.cursorY += this.rowHeight + PADDING;
			this.rowHeight = 0;
		}

		if (this.cursorY + bitmap.height + PADDING > SIZE) {
			return null; // atlas exhausted
		}

		int originX = this.cursorX;
		int originY = this.cursorY;

		int red = colour >> 16 & 0xFF;
		int green = colour >> 8 & 0xFF;
		int blue = colour & 0xFF;

		for (int y = 0; y < bitmap.height; y++) {
			for (int x = 0; x < bitmap.width; x++) {
				int alpha = bitmap.coverage[y * bitmap.width + x] & 0xFF;

				if (alpha == 0) {
					continue;
				}

				// The colour's own alpha scales the coverage so translucent plates
				// (card backgrounds) can be baked here too.
				int scaledAlpha = alpha * ((colour >>> 24) & 0xFF) / 255;
				int argb = scaledAlpha << 24 | red << 16 | green << 8 | blue;
				this.image.setPixel(originX + x, originY + y, argb);
			}
		}

		this.cursorX += bitmap.width + PADDING;
		this.rowHeight = Math.max(this.rowHeight, bitmap.height);
		this.dirty = true;

		return new Rect(originX, originY, bitmap.width, bitmap.height);
	}

	/**
	 * A placed glyph. UVs take a half-texel inset so linear filtering cannot sample the
	 * neighbouring glyph at the quad's edge.
	 */
	record Rect(int x, int y, int width, int height) {
		float u0(float inset) {
			return (this.x + inset) / SIZE;
		}

		float u1(float inset) {
			return (this.x + this.width - inset) / SIZE;
		}

		float v0(float inset) {
			return (this.y + inset) / SIZE;
		}

		float v1(float inset) {
			return (this.y + this.height - inset) / SIZE;
		}
	}
}
