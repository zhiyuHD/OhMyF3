package com.ohmyf3.client.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ohmyf3.font.CoverageBitmap;
import com.ohmyf3.font.GlyphOutline;
import com.ohmyf3.font.GlyphRasterizer;
import com.ohmyf3.font.IconPainter;
import com.ohmyf3.font.KeywordIcons;
import com.ohmyf3.font.SystemFonts;
import com.ohmyf3.font.TtfFont;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the debug screen with a system TrueType font, grouped cards and keyword icons.
 *
 * <p>Vanilla hands us two flat columns of strings. Instead of drawing them as a wall of text,
 * blank lines are used as group separators and every group becomes a rounded card with a
 * leading icon column. Rows share one gutter, so the text lines up no matter which rows
 * happen to have an icon.
 *
 * <p>Glyphs are rasterised at the current device-pixel ratio and padded to the device grid,
 * so one atlas texel lands on exactly one screen pixel; positions that do not fall on a whole
 * GUI pixel are nudged with a fractional pose translation.
 */
public final class TtfTextRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger("ohmyf3/font");

	/** Font pixel size in GUI units. */
	private static final int PIXEL_SIZE = 9;

	private static final int ICON_SIZE = 10;
	private static final int ICON_GAP = 3;

	/** Card geometry, in GUI units. */
	private static final int CARD_PADDING = 3;
	private static final int CARD_GAP = 4;
	private static final int CARD_CORNER = 4;
	private static final int CARD_RADIUS = 3;
	/** Width reserved for the icon column. */
	private static final int GUTTER = ICON_SIZE + ICON_GAP;

	private static final int CARD_FILL = 0xC8141418;
	private static final int CARD_BORDER = 0x2AFFFFFF;

	private static final int DEFAULT_COLOR = 0xFFE0E0E0;
	private static final int FORMATTING_CODE = '\u00a7';

	private static final int[] LEGACY_COLORS = {
			0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA,
			0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
			0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF,
			0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF
	};

	private static final Identifier ATLAS_ID = Identifier.fromNamespaceAndPath("ohmyf3", "font_atlas");
	private static final float TEXEL_INSET = 0.5F;

	private static boolean attempted;
	private static boolean ready;

	private static TtfFont font;
	private static GlyphAtlas atlas;
	private static DynamicTexture texture;
	private static GpuTextureView textureView;
	private static GpuSampler sampler;

	private static float fontScale;
	private static int ascent;
	private static int lineHeight;

	private static int deviceScale = -1;

	private static final Map<GlyphKey, Sprite> GLYPHS = new HashMap<>();
	private static final Map<IconKey, Sprite> ICONS = new HashMap<>();

	private static GlyphAtlas.Rect cardFill;
	private static GlyphAtlas.Rect cardBorder;

	private TtfTextRenderer() {
	}

	private record Sprite(GlyphAtlas.Rect rect, int left, int top, int width, int height, float advance) {
	}

	private record Cell(Sprite sprite, float advance) {
	}

	/** One debug line: an optional leading icon plus its glyphs. */
	private record Row(IconPainter.Icon icon, List<Cell> cells) {
		static final Row EMPTY = new Row(null, List.of());
	}

	private record GlyphKey(int codePoint, int color) {
	}

	private record IconKey(IconPainter.Icon icon, int color) {
	}

	public static boolean isReady() {
		ensureInitialised();
		return ready;
	}

	public static int lineHeight() {
		return lineHeight;
	}

	public static void drawLines(GuiGraphicsExtractor graphics, List<String> lines, boolean alignLeft,
			int screenWidth) {
		ensureInitialised();

		if (!ready) {
			return;
		}

		float poseScale = graphics.pose().m00();
		int target = Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale() * poseScale));

		if (target != deviceScale) {
			rebuildAtlas(target);
			LOGGER.info("device scale {} (window guiScale {}, pose scale {})", target,
					Minecraft.getInstance().getWindow().getGuiScale(), poseScale);
		}

		// Layout first: this rasterises anything not yet cached, so one upload covers the
		// whole frame before a single quad is emitted.
		List<Row> rows = new ArrayList<>(lines.size());

		for (String line : lines) {
			rows.add(line == null || line.isEmpty() ? Row.EMPTY : layout(line));
		}

		if (atlas.isDirty()) {
			texture.upload();
			atlas.clearDirty();
		}

		drawColumn(graphics, rows, alignLeft, screenWidth);
	}

	// ---------------------------------------------------------------- cards

	private static void drawColumn(GuiGraphicsExtractor graphics, List<Row> rows, boolean alignLeft,
			int screenWidth) {
		int widest = 0;

		for (Row row : rows) {
			widest = Math.max(widest, widthOf(row.cells()));
		}

		if (widest == 0) {
			return;
		}

		int cardWidth = CARD_PADDING * 2 + GUTTER + widest;
		int x = alignLeft ? 2 : screenWidth - 2 - cardWidth;
		int y = 2;

		for (int index = 0; index < rows.size(); ) {
			if (!hasContent(rows.get(index))) {
				index++;
				continue;
			}

			int start = index;

			while (index < rows.size() && hasContent(rows.get(index))) {
				index++;
			}

			int count = index - start;
			int cardHeight = CARD_PADDING * 2 + count * lineHeight;

			drawCard(graphics, x, y, cardWidth, cardHeight);

			for (int row = 0; row < count; row++) {
				drawRow(graphics, rows.get(start + row), x, y + CARD_PADDING + row * lineHeight);
			}

			y += cardHeight + CARD_GAP;
		}
	}

	private static boolean hasContent(Row row) {
		return row.icon() != null || !row.cells().isEmpty();
	}

	private static void drawCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		if (cardFill == null) {
			return;
		}

		blitNineSlice(graphics, cardFill, x, y, width, height);

		if (cardBorder != null) {
			blitNineSlice(graphics, cardBorder, x, y, width, height);
		}
	}

	/** Draws a rounded-corner texture as nine stretched regions. */
	private static void blitNineSlice(GuiGraphicsExtractor graphics, GlyphAtlas.Rect texture, int x, int y,
			int width, int height) {
		int corner = CARD_CORNER;

		if (width <= corner * 2 || height <= corner * 2) {
			blitRegion(graphics, texture, 0, 0, texture.width(), texture.height(), x, y, x + width, y + height);
			return;
		}

		int source = corner * deviceScale;
		int right = texture.width() - source;
		int bottom = texture.height() - source;

		// Corners.
		blitRegion(graphics, texture, 0, 0, source, source, x, y, x + corner, y + corner);
		blitRegion(graphics, texture, right, 0, texture.width(), source, x + width - corner, y, x + width,
				y + corner);
		blitRegion(graphics, texture, 0, bottom, source, texture.height(), x, y + height - corner,
				x + corner, y + height);
		blitRegion(graphics, texture, right, bottom, texture.width(), texture.height(),
				x + width - corner, y + height - corner, x + width, y + height);

		// Edges.
		blitRegion(graphics, texture, source, 0, right, source, x + corner, y, x + width - corner, y + corner);
		blitRegion(graphics, texture, source, bottom, right, texture.height(), x + corner,
				y + height - corner, x + width - corner, y + height);
		blitRegion(graphics, texture, 0, source, source, bottom, x, y + corner, x + corner,
				y + height - corner);
		blitRegion(graphics, texture, right, source, texture.width(), bottom, x + width - corner,
				y + corner, x + width, y + height - corner);

		// Centre.
		blitRegion(graphics, texture, source, source, right, bottom, x + corner, y + corner,
				x + width - corner, y + height - corner);
	}

	private static void blitRegion(GuiGraphicsExtractor graphics, GlyphAtlas.Rect texture, int sx0, int sy0,
			int sx1, int sy1, int dx0, int dy0, int dx1, int dy1) {
		float u0 = (texture.x() + sx0 + TEXEL_INSET) / GlyphAtlas.SIZE;
		float u1 = (texture.x() + sx1 - TEXEL_INSET) / GlyphAtlas.SIZE;
		float v0 = (texture.y() + sy0 + TEXEL_INSET) / GlyphAtlas.SIZE;
		float v1 = (texture.y() + sy1 - TEXEL_INSET) / GlyphAtlas.SIZE;

		graphics.blit(textureView, sampler, dx0, dy0, dx1, dy1, u0, u1, v0, v1);
	}

	private static void drawRow(GuiGraphicsExtractor graphics, Row row, int cardX, int rowY) {
		if (row.icon() != null) {
			Sprite icon = icon(row.icon(), iconColor(row.icon()));

			if (icon.rect() != null) {
				int iconY = rowY + (lineHeight - ICON_SIZE) / 2;
				blitRegion(graphics, icon.rect(), 0, 0, icon.rect().width(), icon.rect().height(),
						cardX + CARD_PADDING, iconY, cardX + CARD_PADDING + ICON_SIZE, iconY + ICON_SIZE);
			}
		}

		int baseline = rowY + ascent;
		float penX = cardX + CARD_PADDING + GUTTER;

		for (Cell cell : row.cells()) {
			Sprite sprite = cell.sprite();

			if (sprite.rect() != null) {
				float targetX = penX + sprite.left();
				int x0 = Math.round(targetX);
				float remainder = targetX - x0;
				int y0 = baseline + sprite.top();

				boolean shifted = Math.abs(remainder) > 0.001F;

				if (shifted) {
					graphics.pose().pushMatrix();
					graphics.pose().translate(remainder, 0.0F);
				}

				blitRegion(graphics, sprite.rect(), 0, 0, sprite.rect().width(), sprite.rect().height(),
						x0, y0, x0 + sprite.width(), y0 + sprite.height());

				if (shifted) {
					graphics.pose().popMatrix();
				}
			}

			penX += cell.advance();
		}
	}

	/** Icons get their own accent colour so they read as a group at a glance. */
	private static int iconColor(IconPainter.Icon icon) {
		return switch (icon) {
			case GAUGE -> 0xFF8FD9A8;
			case MEMORY -> 0xFFE0BE72;
			case GRID -> 0xFF8FB8E8;
			case CROSSHAIR -> 0xFFE89A9A;
			case SODIUM -> 0xFF86D98F;
			case BOLT -> 0xFFE8C86A;
			case CHIP -> 0xFFB9A6E8;
			case CUP -> 0xFFD8A87A;
			case DICE -> 0xFFE6CC7A;
		};
	}

	private static int widthOf(List<Cell> cells) {
		float width = 0;

		for (Cell cell : cells) {
			width += cell.advance();
		}

		return Math.round(width);
	}

	// ---------------------------------------------------------------- layout

	private static Row layout(String line) {
		List<Cell> cells = new ArrayList<>(line.length());
		IconPainter.Icon rowIcon = null;
		int color = DEFAULT_COLOR;

		for (int i = 0; i < line.length(); ) {
			char c = line.charAt(i);

			if (c == FORMATTING_CODE && i + 1 < line.length()) {
				color = colorFor(line.charAt(i + 1), color);
				i += 2;
				continue;
			}

			if (rowIcon == null) {
				rowIcon = KeywordIcons.iconStartingAt(line, i);
			}

			int codePoint = line.codePointAt(i);
			i += Character.charCount(codePoint);
			Sprite sprite = glyph(codePoint, color);
			cells.add(new Cell(sprite, sprite.advance()));
		}

		return new Row(rowIcon, cells);
	}

	private static int colorFor(char code, int current) {
		if (code == 'r' || code == 'R') {
			return DEFAULT_COLOR;
		}

		int index = "0123456789abcdef".indexOf(Character.toLowerCase(code));
		return index >= 0 ? LEGACY_COLORS[index] : current;
	}

	// ---------------------------------------------------------------- sprite cache

	private static Sprite glyph(int codePoint, int color) {
		GlyphKey key = new GlyphKey(codePoint, color);
		Sprite cached = GLYPHS.get(key);

		if (cached != null) {
			return cached;
		}

		int glyphIndex = font.glyphIndex(codePoint);
		float advance = Math.max(1.0F, font.advanceWidth(glyphIndex) * (PIXEL_SIZE / (float) font.unitsPerEm()));
		GlyphOutline outline = font.outline(glyphIndex);
		CoverageBitmap bitmap = outline == null ? null : GlyphRasterizer.rasterize(outline, fontScale);

		Sprite sprite = bitmap == null
				? new Sprite(null, 0, 0, 0, 0, advance)
				: place(alignToDeviceGrid(bitmap), color, advance);

		GLYPHS.put(key, sprite);
		return sprite;
	}

	private static Sprite icon(IconPainter.Icon icon, int color) {
		IconKey key = new IconKey(icon, color);
		Sprite cached = ICONS.get(key);

		if (cached != null) {
			return cached;
		}

		CoverageBitmap bitmap = IconPainter.paint(icon, ICON_SIZE * deviceScale);
		Sprite sprite = place(bitmap, color, ICON_SIZE);
		ICONS.put(key, sprite);
		return sprite;
	}

	private static Sprite place(CoverageBitmap bitmap, int color, float advance) {
		GlyphAtlas.Rect rect = atlas.add(bitmap, color);

		if (rect == null) {
			return new Sprite(null, 0, 0, 0, 0, advance);
		}

		return new Sprite(rect, bitmap.left / deviceScale, bitmap.top / deviceScale,
				Math.max(1, bitmap.width / deviceScale), Math.max(1, bitmap.height / deviceScale), advance);
	}

	/**
	 * Pads a bitmap so its origin and size are multiples of the device scale. Rounding then
	 * only touches transparent padding, never the glyph itself.
	 */
	private static CoverageBitmap alignToDeviceGrid(CoverageBitmap bitmap) {
		int scale = deviceScale;
		int shiftX = Math.floorMod(bitmap.left, scale);
		int shiftY = Math.floorMod(bitmap.top, scale);
		int width = (shiftX + bitmap.width + scale - 1) / scale * scale;
		int height = (shiftY + bitmap.height + scale - 1) / scale * scale;
		byte[] coverage = new byte[width * height];

		for (int y = 0; y < bitmap.height; y++) {
			System.arraycopy(bitmap.coverage, y * bitmap.width, coverage,
					(y + shiftY) * width + shiftX, bitmap.width);
		}

		return new CoverageBitmap(width, height, bitmap.left - shiftX, bitmap.top - shiftY, coverage);
	}

	// ---------------------------------------------------------------- atlas

	private static void rebuildAtlas(int newDeviceScale) {
		deviceScale = newDeviceScale;
		fontScale = PIXEL_SIZE * deviceScale / (float) font.unitsPerEm();

		// `true` zeroes the buffer: uninitialised memory leaks garbage into the texture.
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, GlyphAtlas.SIZE, GlyphAtlas.SIZE, true);
		atlas = new GlyphAtlas(image);
		GLYPHS.clear();
		ICONS.clear();

		if (texture == null) {
			texture = new DynamicTexture(() -> "ohmyf3 system font atlas", image);
			Minecraft.getInstance().getTextureManager().register(ATLAS_ID, texture);
			textureView = texture.getTextureView();
			sampler = texture.getSampler();
		} else {
			texture.setPixels(image);
		}

		buildCardTextures();
		texture.upload();
		atlas.clearDirty();
	}

	private static void buildCardTextures() {
		int size = (CARD_CORNER * 2 + 2) * deviceScale;
		float radius = CARD_RADIUS * deviceScale;

		cardFill = atlas.add(roundedRect(size, radius, 0), CARD_FILL);
		cardBorder = atlas.add(roundedRect(size, radius, Math.max(1, deviceScale / 2.0F)), CARD_BORDER);
	}

	/**
	 * Antialiased rounded rectangle coverage.
	 *
	 * @param ring thickness of an outline to keep, or {@code 0} for a solid shape
	 */
	private static CoverageBitmap roundedRect(int size, float radius, float ring) {
		byte[] coverage = new byte[size * size];
		float half = size / 2.0F;
		float innerHalf = half - ring;
		int samples = 4;
		int total = samples * samples;

		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				int inside = 0;

				for (int sy = 0; sy < samples; sy++) {
					for (int sx = 0; sx < samples; sx++) {
						float x = px + (sx + 0.5F) / samples;
						float y = py + (sy + 0.5F) / samples;
						float outer = roundedDistance(x, y, half, half, half, half, radius);

						if (outer > 0) {
							continue;
						}

						if (ring > 0) {
							float inner = roundedDistance(x, y, half, half, innerHalf, innerHalf,
									Math.max(0, radius - ring));

							if (inner <= 0) {
								continue;
							}
						}

						inside++;
					}
				}

				coverage[py * size + px] = (byte) Math.round(255.0F * inside / total);
			}
		}

		return new CoverageBitmap(size, size, 0, 0, coverage);
	}

	/** Signed distance to a rounded rectangle centred at (cx, cy). */
	private static float roundedDistance(float x, float y, float cx, float cy, float halfWidth, float halfHeight,
			float radius) {
		float dx = Math.abs(x - cx) - (halfWidth - radius);
		float dy = Math.abs(y - cy) - (halfHeight - radius);
		float outsideX = Math.max(dx, 0);
		float outsideY = Math.max(dy, 0);
		return (float) Math.sqrt(outsideX * outsideX + outsideY * outsideY) + Math.min(Math.max(dx, dy), 0)
				- radius;
	}

	// ---------------------------------------------------------------- initialisation

	private static void ensureInitialised() {
		if (attempted) {
			return;
		}

		attempted = true;

		try {
			Path path = SystemFonts.find();

			if (path == null) {
				LOGGER.warn("No usable TrueType font found on this system; F3 keeps vanilla text");
				return;
			}

			font = TtfFont.parse(path);
			ascent = Math.round(font.ascender() * PIXEL_SIZE / (float) font.unitsPerEm());
			lineHeight = Math.max(10, Math.round((font.ascender() - font.descender()) * PIXEL_SIZE
					/ (float) font.unitsPerEm()));

			rebuildAtlas(1);
			ready = true;

			LOGGER.info("Using system font {} ({} glyphs, {}px, line height {})",
					path.getFileName(), font.glyphCount(), PIXEL_SIZE, lineHeight);
		} catch (Throwable throwable) {
			LOGGER.warn("System font setup failed; F3 keeps vanilla text", throwable);
		}
	}
}
