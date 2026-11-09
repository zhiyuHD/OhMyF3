package com.ohmyf3.font;

/**
 * An antialiased coverage bitmap shared by glyph rasterisation and procedural icons.
 *
 * <p>{@code left}/{@code top} are the bitmap's offset from the pen position, in pixels with
 * y growing downwards.
 */
public final class CoverageBitmap {
	public final int width;
	public final int height;
	public final int left;
	public final int top;
	/** Row-major coverage in {@code [0, 255]}. */
	public final byte[] coverage;

	public CoverageBitmap(int width, int height, int left, int top, byte[] coverage) {
		this.width = width;
		this.height = height;
		this.left = left;
		this.top = top;
		this.coverage = coverage;
	}
}
