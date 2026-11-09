package com.ohmyf3.font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal, dependency-free TrueType ({@code glyf} flavour) reader.
 *
 * <p>Only the tables needed to turn text into crisp pixels are parsed: {@code head},
 * {@code maxp}, {@code hhea}, {@code hmtx}, {@code cmap}, {@code loca} and {@code glyf}.
 * CFF/OTF fonts are rejected instead of being mis-parsed, because they store outlines in a
 * completely different table.
 *
 * <p>The class is immutable apart from the composite-glyph recursion budget, and safe to
 * share between threads as long as glyph outlines are requested one at a time.
 */
public final class TtfFont {
	/** Largest composite nesting we follow; the spec's limit is far lower in practice. */
	private static final int MAX_COMPOSITE_DEPTH = 5;

	private final byte[] data;
	private final int unitsPerEm;
	private final int numGlyphs;
	private final int indexToLocFormat;
	private final int numberOfHMetrics;
	private final int ascender;
	private final int descender;
	private final int lineGap;

	private final int hmtxOffset;
	private final int locaOffset;
	private final int glyfOffset;
	private final int cmapOffset;
	private final int cmapFormat;

	/** Wideness of every glyph on the font's horizontal metrics, in font units. */
	private final int[] advanceWidths;

	private TtfFont(byte[] data) {
		this.data = data;

		int head = requireTable("head");
		int maxp = requireTable("maxp");
		int hhea = requireTable("hhea");

		this.unitsPerEm = u16(head + 18);
		this.indexToLocFormat = s16(head + 50);
		this.numGlyphs = u16(maxp + 4);

		this.ascender = s16(hhea + 4);
		this.descender = s16(hhea + 6);
		this.lineGap = s16(hhea + 8);
		this.numberOfHMetrics = u16(hhea + 34);

		this.hmtxOffset = requireTable("hmtx");
		this.locaOffset = requireTable("loca");
		this.glyfOffset = requireTable("glyf");

		int[] cmap = selectCmap();
		this.cmapOffset = cmap[0];
		this.cmapFormat = cmap[1];

		this.advanceWidths = readAdvanceWidths();
	}

	// ---------------------------------------------------------------- loading

	public static TtfFont parse(Path path) throws IOException {
		return parse(Files.readAllBytes(path));
	}

	public static TtfFont parse(byte[] data) {
		if (data.length < 12) {
			throw new IllegalArgumentException("not a font file: too short");
		}

		long version = u32Static(data, 0);

		// 0x00010000 and 'true' are both TrueType outlines; 'OTTO' is CFF, which we do not support.
		if (version == 0x4F54544FL) {
			throw new IllegalArgumentException("CFF/OTF outlines are not supported, use a TrueType (.ttf) font");
		}

		if (version != 0x00010000L && version != 0x74727565L) {
			throw new IllegalArgumentException(String.format("unknown sfnt version 0x%08x", version));
		}

		return new TtfFont(data);
	}

	// ---------------------------------------------------------------- metrics

	public int unitsPerEm() {
		return this.unitsPerEm;
	}

	public int glyphCount() {
		return this.numGlyphs;
	}

	public int ascender() {
		return this.ascender;
	}

	public int descender() {
		return this.descender;
	}

	public int lineGap() {
		return this.lineGap;
	}

	/** Horizontal advance of a glyph in font units. */
	public int advanceWidth(int glyphIndex) {
		if (glyphIndex < 0 || glyphIndex >= this.numGlyphs) {
			return 0;
		}

		return this.advanceWidths[glyphIndex];
	}

	// ---------------------------------------------------------------- cmap

	/**
	 * Maps a Unicode code point to a glyph index, or {@code 0} (the .notdef glyph) when the
	 * font has nothing for it.
	 */
	public int glyphIndex(int codePoint) {
		return this.cmapFormat == 12 ? lookupCmap12(codePoint) : lookupCmap4(codePoint);
	}

	private int lookupCmap4(int codePoint) {
		if (codePoint > 0xFFFF) {
			return 0;
		}

		int off = this.cmapOffset;
		int segCountX2 = u16(off + 6);
		int segCount = segCountX2 / 2;

		int endCodes = off + 14;
		int startCodes = endCodes + segCountX2 + 2; // +1 reservedPad
		int idDeltas = startCodes + segCountX2;
		int idRangeOffsets = idDeltas + segCountX2;

		for (int i = 0; i < segCount; i++) {
			int end = u16(endCodes + i * 2);

			if (codePoint > end) {
				continue;
			}

			int start = u16(startCodes + i * 2);

			if (codePoint < start) {
				return 0;
			}

			int idDelta = s16(idDeltas + i * 2);
			int idRangeOffset = u16(idRangeOffsets + i * 2);

			if (idRangeOffset == 0) {
				return (codePoint + idDelta) & 0xFFFF;
			}

			int glyphOffset = idRangeOffsets + i * 2 + idRangeOffset + (codePoint - start) * 2;

			if (glyphOffset + 1 >= this.data.length) {
				return 0;
			}

			int glyph = u16(glyphOffset);
			return glyph == 0 ? 0 : (glyph + idDelta) & 0xFFFF;
		}

		return 0;
	}

	private int lookupCmap12(int codePoint) {
		int off = this.cmapOffset;
		long nGroups = u32(off + 12);

		// The groups are sorted, so binary search instead of a linear walk.
		int low = 0;
		int high = (int) Math.min(nGroups, Integer.MAX_VALUE) - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1;
			int group = off + 16 + mid * 12;

			long start = u32(group);
			long end = u32(group + 4);

			if (codePoint < start) {
				high = mid - 1;
			} else if (codePoint > end) {
				low = mid + 1;
			} else {
				long startGlyph = u32(group + 8);
				long glyph = startGlyph + (codePoint - start);
				return glyph <= Integer.MAX_VALUE ? (int) glyph : 0;
			}
		}

		return 0;
	}

	private int[] selectCmap() {
		int cmap = requireTable("cmap");
		int count = u16(cmap + 2);

		int best = -1;
		int bestFormat = 0;

		for (int i = 0; i < count; i++) {
			int record = cmap + 4 + i * 8;
			int platform = u16(record);
			int encoding = u16(record + 2);
			int subtable = cmap + (int) u32(record + 4);
			int format = u16(subtable);

			// Full-repertoire Unicode first, then the BMP table; everything else is ignored.
			boolean full = platform == 3 && encoding == 10 || platform == 0 && encoding >= 4;
			boolean bmp = platform == 3 && encoding == 1 || platform == 0 && encoding <= 3;

			if (format == 12 && full) {
				return new int[]{subtable, 12};
			}

			if (format == 4 && bmp && best == -1) {
				best = subtable;
				bestFormat = 4;
			}
		}

		if (best == -1) {
			throw new IllegalArgumentException("font has no Unicode cmap subtable");
		}

		return new int[]{best, bestFormat};
	}

	// ---------------------------------------------------------------- hmtx

	private int[] readAdvanceWidths() {
		int[] widths = new int[this.numGlyphs];
		int last = 0;

		for (int i = 0; i < this.numGlyphs; i++) {
			if (i < this.numberOfHMetrics) {
				last = u16(this.hmtxOffset + i * 4);
			}

			// Glyphs past numberOfHMetrics reuse the last advance, per the spec.
			widths[i] = last;
		}

		return widths;
	}

	// ---------------------------------------------------------------- glyf

	/**
	 * The bounding box the font itself declares for a glyph, as
	 * {@code {minX, minY, maxX, maxY}} in font units, or {@code null} when blank.
	 * Useful for cross-checking parsed outlines.
	 */
	public int[] declaredBounds(int glyphIndex) {
		int start = glyphOffset(glyphIndex);
		int end = glyphOffset(glyphIndex + 1);

		if (start == end) {
			return null;
		}

		int glyf = this.glyfOffset + start;
		return new int[]{s16(glyf + 2), s16(glyf + 4), s16(glyf + 6), s16(glyf + 8)};
	}

	/** Returns the outline of a glyph in font units, or {@code null} for blank glyphs. */
	public GlyphOutline outline(int glyphIndex) {
		return outline(glyphIndex, 0);
	}

	private GlyphOutline outline(int glyphIndex, int depth) {
		if (glyphIndex < 0 || glyphIndex >= this.numGlyphs || depth > MAX_COMPOSITE_DEPTH) {
			return null;
		}

		int start = glyphOffset(glyphIndex);
		int end = glyphOffset(glyphIndex + 1);

		if (start == end) {
			return null; // empty glyph, e.g. space
		}

		int glyf = this.glyfOffset + start;
		int contourCount = s16(glyf);

		if (contourCount >= 0) {
			return readSimpleGlyph(glyf, contourCount);
		}

		return readCompositeGlyph(glyf, depth);
	}

	private int glyphOffset(int glyphIndex) {
		if (this.indexToLocFormat == 0) {
			return u16(this.locaOffset + glyphIndex * 2) * 2;
		}

		return (int) u32(this.locaOffset + glyphIndex * 4);
	}

	private GlyphOutline readSimpleGlyph(int glyf, int contourCount) {
		int cursor = glyf + 10;
		int[] endPoints = new int[contourCount];

		for (int i = 0; i < contourCount; i++) {
			endPoints[i] = u16(cursor);
			cursor += 2;
		}

		int pointCount = contourCount == 0 ? 0 : endPoints[contourCount - 1] + 1;
		int instructionLength = u16(cursor);
		cursor += 2 + instructionLength;

		boolean[] onCurve = new boolean[pointCount];
		int[] xs = new int[pointCount];
		int[] ys = new int[pointCount];
		int[] pointFlags = new int[pointCount];

		// Flags are run-length encoded: REPEAT_FLAG is followed by one byte giving how many
		// extra points reuse the same flag byte. The raw byte is kept because the x and y
		// axes use *different* bits of it (see readCoordinates).
		int point = 0;

		while (point < pointCount) {
			int flag = this.data[cursor++] & 0xFF;
			pointFlags[point] = flag;
			onCurve[point] = (flag & 0x01) != 0;
			point++;

			if ((flag & 0x08) != 0) {
				int repeats = this.data[cursor++] & 0xFF;

				for (int r = 0; r < repeats && point < pointCount; r++) {
					pointFlags[point] = flag;
					onCurve[point] = (flag & 0x01) != 0;
					point++;
				}
			}
		}

		cursor = readCoordinates(cursor, pointFlags, true, xs);
		cursor = readCoordinates(cursor, pointFlags, false, ys);

		int[] contourEnds = new int[contourCount];
		for (int i = 0; i < contourCount; i++) {
			contourEnds[i] = endPoints[i];
		}

		return GlyphOutline.fromGlyfArrays(contourEnds, xs, ys, onCurve);
	}

	/**
	 * Decodes one coordinate axis.
	 *
	 * <p>The two axes are encoded with different flag bits: x uses {@code 0x02}/{@code 0x10}
	 * (short vector / same-or-positive) while y uses {@code 0x04}/{@code 0x20}. Sharing one
	 * pair of bits between the axes silently corrupts every y coordinate, which stretches
	 * glyphs vertically.
	 */
	private int readCoordinates(int cursor, int[] pointFlags, boolean xAxis, int[] out) {
		int shortBit = xAxis ? 0x02 : 0x04;
		int sameOrPositiveBit = xAxis ? 0x10 : 0x20;
		int value = 0;

		for (int i = 0; i < out.length; i++) {
			int flag = pointFlags[i];

			if ((flag & shortBit) != 0) {
				int delta = this.data[cursor++] & 0xFF;
				value += (flag & sameOrPositiveBit) != 0 ? delta : -delta;
			} else if ((flag & sameOrPositiveBit) == 0) {
				value += s16(cursor);
				cursor += 2;
			}

			out[i] = value;
		}

		return cursor;
	}

	private GlyphOutline readCompositeGlyph(int glyf, int depth) {
		List<float[]> contours = new ArrayList<>();
		int cursor = glyf + 10;
		int flags;

		do {
			flags = u16(cursor);
			int componentGlyph = u16(cursor + 2);
			cursor += 4;

			int arg1;
			int arg2;

			if ((flags & 1) != 0) { // ARG_1_AND_2_ARE_WORDS
				arg1 = s16(cursor);
				arg2 = s16(cursor + 2);
				cursor += 4;
			} else {
				arg1 = (byte) this.data[cursor];
				arg2 = (byte) this.data[cursor + 1];
				cursor += 2;
			}

			boolean offsetsAreXY = (flags & 2) != 0; // ARGS_ARE_XY_VALUES
			float dx = 0;
			float dy = 0;

			if (offsetsAreXY) {
				dx = arg1;
				dy = arg2;
			}

			float a = 1;
			float b = 0;
			float c = 0;
			float d = 1;

			if ((flags & 8) != 0) { // WE_HAVE_A_SCALE
				a = d = f2dot14(cursor);
				cursor += 2;
			} else if ((flags & 0x40) != 0) { // WE_HAVE_AN_X_AND_Y_SCALE
				a = f2dot14(cursor);
				d = f2dot14(cursor + 2);
				cursor += 4;
			} else if ((flags & 0x80) != 0) { // WE_HAVE_A_TWO_BY_TWO
				a = f2dot14(cursor);
				b = f2dot14(cursor + 2);
				c = f2dot14(cursor + 4);
				d = f2dot14(cursor + 6);
				cursor += 8;
			}

			GlyphOutline component = outline(componentGlyph, depth + 1);

			if (component != null) {
				for (float[] contour : component.contours()) {
					contours.add(transform(contour, a, b, c, d, dx, dy));
				}
			}
		} while ((flags & 0x20) != 0); // MORE_COMPONENTS

		return contours.isEmpty() ? null : new GlyphOutline(contours);
	}

	private static float[] transform(float[] contour, float a, float b, float c, float d, float dx, float dy) {
		float[] out = new float[contour.length];

		for (int i = 0; i < contour.length; i += 3) {
			float x = contour[i];
			float y = contour[i + 1];
			out[i] = a * x + c * y + dx;
			out[i + 1] = b * x + d * y + dy;
			out[i + 2] = contour[i + 2];
		}

		return out;
	}

	// ---------------------------------------------------------------- primitives

	private int requireTable(String tag) {
		int offset = tableOffset(tag);

		if (offset < 0) {
			throw new IllegalArgumentException("font is missing the required '" + tag + "' table");
		}

		return offset;
	}

	private int tableOffset(String tag) {
		int count = u16(4);

		for (int i = 0; i < count; i++) {
			int record = 12 + i * 16;

			if (this.data[record] == tag.charAt(0) && this.data[record + 1] == tag.charAt(1)
					&& this.data[record + 2] == tag.charAt(2) && this.data[record + 3] == tag.charAt(3)) {
				return (int) u32(record + 8);
			}
		}

		return -1;
	}

	private int u16(int offset) {
		return u16Static(this.data, offset);
	}

	private int s16(int offset) {
		return (short) ((this.data[offset] & 0xFF) << 8 | this.data[offset + 1] & 0xFF);
	}

	private long u32(int offset) {
		return u32Static(this.data, offset);
	}

	private static int u16Static(byte[] data, int offset) {
		return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
	}

	private static long u32Static(byte[] data, int offset) {
		return (long) (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16
				| (data[offset + 2] & 0xFF) << 8 | data[offset + 3] & 0xFF;
	}

	private float f2dot14(int cursor) {
		return s16(cursor) / 16384.0F;
	}
}
