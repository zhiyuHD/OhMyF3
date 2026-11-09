package com.ohmyf3.client.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ohmyf3.client.DebugEntryOrigin;
import com.ohmyf3.client.DebugOverlaySlide;
import com.ohmyf3.config.OhMyF3Config;
import com.ohmyf3.font.Labels;
import com.ohmyf3.font.CoverageBitmap;
import com.ohmyf3.font.EntryClassifier;
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
	private static final int CARD_CORNER = 7;
	private static final int CARD_RADIUS = 6;
	/** Width reserved for the icon column. */
	private static final int GUTTER = ICON_SIZE + ICON_GAP;

	/** Width of the icon column, which disappears entirely when icons are disabled. */
	private static int gutter() {
		return OhMyF3Config.get().icons ? GUTTER : 0;
	}

	private static final int CARD_FILL = 0xC8141418;
	private static final int CARD_BORDER = 0x2AFFFFFF;
	private static final int CARD_BORDER_MODS = 0x4AFFB74D;

	/** Gap between the key column and the value column. */
	private static final int KEY_GAP = 6;

	/** Dimmer colour for the {@code key} part of a row. */
	private static final int KEY_COLOR = 0xFF9AA0A6;

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

	/** Screen pixels per GUI unit (does not need to be an integer). */
	private static float guiToScreen = 1.0F;

	private static final Map<GlyphKey, Sprite> GLYPHS = new HashMap<>();
	private static final Map<IconKey, Sprite> ICONS = new HashMap<>();

	private static List<String> lastModRows = List.of();

	/** Last drawn columns, replayed while the overlay slides out. */
	private static List<Row> lastLeftRows = List.of();
	private static List<Row> lastRightRows = List.of();
	private static int lastLeftWidth;
	private static int lastRightWidth;
	private static int lastLeftCardWidth;
	private static int lastRightCardWidth;
	private static float lastPoseScale = 1.0F;


	private static GlyphAtlas.Rect cardFill;
	private static GlyphAtlas.Rect cardBorder;
	private static GlyphAtlas.Rect cardBorderMods;

	private TtfTextRenderer() {
	}

	private record Sprite(GlyphAtlas.Rect rect, int left, int top, int width, int height, float advance) {
	}

	private record Cell(Sprite sprite, float advance) {
	}

	/** One debug row: icon, dim key, bright value. */
	private record Row(IconPainter.Icon icon, List<Cell> keyCells, List<Cell> valueCells, boolean vanilla,
			boolean header, String raw) {
		static final Row EMPTY = new Row(null, List.of(), List.of(), true, false, "");
	}

	private record GlyphKey(int codePoint, int color) {
	}

	private record IconKey(IconPainter.Icon icon, int color) {
	}

	public static boolean isReady() {
		ensureInitialised();
		return ready;
	}

	/** Sampling state shared with the backdrop blur (linear, clamped). */
	public static GpuSampler sampler() {
		return sampler;
	}

	public static int lineHeight() {
		return lineHeight;
	}

	/**
	 * Drives the slide-out animation.
	 *
	 * <p>Once the debug overlay is hidden, vanilla stops calling {@code extractLines} entirely,
	 * so the last drawn columns are cached and replayed here by the per-frame HUD hook with an
	 * outward offset until the animation ends.
	 */
	public static void onFrame(boolean visible, GuiGraphicsExtractor graphics, long nowNanos) {
		if (visible || !ready) {
			return;
		}

		// Vanilla already stopped extracting the overlay, but the shared openness value may
		// still be mid-flight: replay the last columns so closing (and a close interrupted by
		// reopening) keeps moving smoothly.
		float offset = DebugOverlaySlide.offset();

		// Only replay while the close is actually in progress. Once the value settles fully
		// displaced there is nothing left to show; drawing then would leave the columns
		// hanging half off-screen for good.
		if (offset >= 0.999F || (lastLeftRows.isEmpty() && lastRightRows.isEmpty())) {
			return;
		}

		graphics.pose().pushMatrix();
		graphics.pose().scale(lastPoseScale, lastPoseScale);

		if (!lastLeftRows.isEmpty()) {
			drawColumn(graphics, lastLeftRows, true, lastLeftWidth, -offset * travel(true));
		}

		if (!lastRightRows.isEmpty()) {
			drawColumn(graphics, lastRightRows, false, lastRightWidth, offset * travel(false));
		}

		graphics.pose().popMatrix();
	}

	/** Called once per frame, before the overlay assembles its columns. */
	public static void beginFrame() {
		// The backdrop texture is refreshed by BackdropBlur at GUI-render time; nothing to
		// reset here yet, but the hook keeps the frame boundaries explicit.
	}

	public static void drawLines(GuiGraphicsExtractor graphics, List<String> lines, boolean alignLeft,
			int screenWidth) {
		ensureInitialised();

		if (!ready) {
			return;
		}

		float poseScale = graphics.pose().m00();
		int target = Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale() * poseScale));

		guiToScreen = Minecraft.getInstance().getWindow().getGuiScale() * poseScale;

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

		if (alignLeft) {
			logModRows(rows);
		}

		lastPoseScale = poseScale;

		if (alignLeft) {
			lastLeftRows = rows;
			lastLeftWidth = screenWidth;
		} else {
			lastRightRows = rows;
			lastRightWidth = screenWidth;
		}

		drawColumn(graphics, rows, alignLeft, screenWidth, 0.0F);
	}

	// ---------------------------------------------------------------- cards

	private static void drawColumn(GuiGraphicsExtractor graphics, List<Row> rows, boolean alignLeft,
			int screenWidth, float xOffset) {
		List<Card> cards = new ArrayList<>();
		List<Row> current = new ArrayList<>();

		// Vanilla rows keep their blank-line grouping; mod rows are collected into one card.
		for (Row row : rows) {
			if (!row.vanilla()) {
				continue;
			}

			if (!hasContent(row)) {
				if (!current.isEmpty()) {
					cards.add(new Card(current, false));
					current = new ArrayList<>();
				}

				continue;
			}

			current.add(row);
		}

		if (!current.isEmpty()) {
			cards.add(new Card(current, false));
		}

		List<Row> mods = new ArrayList<>();

		for (Row row : rows) {
			if (!row.vanilla() && hasContent(row)) {
				mods.add(row);
			}
		}

		if (!mods.isEmpty()) {
			// A labelled card so third-party rows are clearly their own area.
			Row header = new Row(null, List.of(), cellsFor(OhMyF3Config.get().chineseLabels ? "模组" : "Mods", KEY_COLOR), false, true, "Mods");
			List<Row> withHeader = new ArrayList<>();
			withHeader.add(header);
			withHeader.addAll(mods);
			cards.add(new Card(withHeader, true));
		}

		if (cards.isEmpty()) {
			return;
		}

		int widest = 0;

		for (Card card : cards) {
			widest = Math.max(widest, cardWidth(card));
		}

		int cardWidth = widest;

		if (alignLeft) {
			lastLeftCardWidth = cardWidth;
		} else {
			lastRightCardWidth = cardWidth;
		}

		int x = (alignLeft ? 2 : screenWidth - 2 - cardWidth) + Math.round(xOffset);
		int y = 2;

		for (Card card : cards) {
			int height = CARD_PADDING * 2 + card.rows().size() * lineHeight;
			drawCard(graphics, x, y, cardWidth, height, card.mods());

			int maxKey = maxKeyWidth(card);
			int valueColumn = x + CARD_PADDING + gutter() + (maxKey > 0 ? maxKey + KEY_GAP : 0);

			for (int index = 0; index < card.rows().size(); index++) {
				drawRow(graphics, card.rows().get(index), x, y + CARD_PADDING + index * lineHeight, valueColumn);
			}

			y += height + CARD_GAP;
		}
	}

	private record Card(List<Row> rows, boolean mods) {
	}

	private static int cardWidth(Card card) {
		int maxKey = maxKeyWidth(card);
		int maxRow = 0;

		for (Row row : card.rows()) {
			int key = widthOf(row.keyCells());
			int value = widthOf(row.valueCells());
			maxRow = Math.max(maxRow, key + (key > 0 ? KEY_GAP : 0) + value);
		}

		return CARD_PADDING * 2 + gutter() + maxRow;
	}

	private static int maxKeyWidth(Card card) {
		int maxKey = 0;

		for (Row row : card.rows()) {
			maxKey = Math.max(maxKey, widthOf(row.keyCells()));
		}

		return maxKey;
	}

	private static boolean hasContent(Row row) {
		return row.icon() != null || !row.keyCells().isEmpty() || !row.valueCells().isEmpty();
	}

	private static void drawCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			boolean mods) {
		if (cardFill == null) {
			return;
		}

		if (OhMyF3Config.get().blur) {
			drawBlurredBackdrop(graphics, x, y, width, height);
		}
		blitNineSlice(graphics, cardFill, x, y, width, height);

		GlyphAtlas.Rect border = mods && cardBorderMods != null ? cardBorderMods : cardBorder;

		if (border != null) {
			blitNineSlice(graphics, border, x, y, width, height);
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

	/**
	 * How far a column has to travel to be fully off-screen.
	 *
	 * <p>A fixed distance is not enough: cards are as wide as their content, so a 200px slide
	 * can leave a wide card half visible at the end of the animation.
	 */
	public static float travel(boolean alignLeft) {
		int cardWidth = alignLeft ? lastLeftCardWidth : lastRightCardWidth;
		return Math.max(DebugOverlaySlide.DISTANCE, cardWidth + 16.0F);
	}

	/** Draws the blurred copy inside the card, inset so the rounded corners stay clean. */
	private static void drawBlurredBackdrop(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		if (!BackdropBlur.isReady()) {
			return;
		}

		int inset = Math.max(1, CARD_RADIUS / 2);
		int x0 = x + inset;
		int y0 = y + inset;
		int x1 = x + width - inset;
		int y1 = y + height - inset;

		if (x1 <= x0 || y1 <= y0) {
			return;
		}

		float screenWidth = Minecraft.getInstance().getWindow().getWidth();
		float screenHeight = Minecraft.getInstance().getWindow().getHeight();

		if (screenWidth <= 0 || screenHeight <= 0) {
			return;
		}

		float u0 = Math.max(0.0F, Math.min(1.0F, x0 * guiToScreen / screenWidth));
		float u1 = Math.max(0.0F, Math.min(1.0F, x1 * guiToScreen / screenWidth));

		// The render target's texture has its origin at the bottom-left while screen y grows
		// downwards, so v has to be mirrored or the captured frame shows up upside down.
		float v0 = 1.0F - Math.max(0.0F, Math.min(1.0F, y0 * guiToScreen / screenHeight));
		float v1 = 1.0F - Math.max(0.0F, Math.min(1.0F, y1 * guiToScreen / screenHeight));

		graphics.blit(BackdropBlur.view(), BackdropBlur.sampler(), x0, y0, x1, y1, u0, u1, v0, v1);
	}

	private static void blitRegion(GuiGraphicsExtractor graphics, GlyphAtlas.Rect texture, int sx0, int sy0,
			int sx1, int sy1, int dx0, int dy0, int dx1, int dy1) {
		float u0 = (texture.x() + sx0 + TEXEL_INSET) / GlyphAtlas.SIZE;
		float u1 = (texture.x() + sx1 - TEXEL_INSET) / GlyphAtlas.SIZE;
		float v0 = (texture.y() + sy0 + TEXEL_INSET) / GlyphAtlas.SIZE;
		float v1 = (texture.y() + sy1 - TEXEL_INSET) / GlyphAtlas.SIZE;

		graphics.blit(textureView, sampler, dx0, dy0, dx1, dy1, u0, u1, v0, v1);
	}

	private static void drawRow(GuiGraphicsExtractor graphics, Row row, int cardX, int rowY, int valueColumn) {
		int baseline = rowY + ascent;

		if (row.header()) {
			drawCells(graphics, row.valueCells(), cardX + CARD_PADDING, baseline);
			return;
		}

		if (row.icon() != null) {
			Sprite icon = icon(row.icon(), iconColor(row.icon()));

			if (icon.rect() != null) {
				int iconY = rowY + (lineHeight - ICON_SIZE) / 2;
				blitRegion(graphics, icon.rect(), 0, 0, icon.rect().width(), icon.rect().height(),
						cardX + CARD_PADDING, iconY, cardX + CARD_PADDING + ICON_SIZE, iconY + ICON_SIZE);
			}
		}

		float penX = cardX + CARD_PADDING + gutter();
		penX = drawCells(graphics, row.keyCells(), penX, baseline);

		if (!row.valueCells().isEmpty()) {
			drawCells(graphics, row.valueCells(), Math.max(penX, valueColumn), baseline);
		}
	}

	/** Draws a run of glyphs without any decoration and returns the pen position after it. */
	private static float drawCells(GuiGraphicsExtractor graphics, List<Cell> cells, float penX, int baseline) {
		for (Cell cell : cells) {
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

		return penX;
	}

	/**
	 * Draws one line of text with the system font at {@code (x, baseline)} and returns the
	 * advance width, so callers can lay out several runs on the same line.
	 */
	public static float drawString(GuiGraphicsExtractor graphics, String text, float x, int baseline, int color) {
		if (!ready || text == null || text.isEmpty()) {
			return 0.0F;
		}

		return drawCells(graphics, cellsFor(text, color), x, baseline);
	}

	/** Width of {@code text} in GUI pixels, with the same metrics {@link #drawString} uses. */
	public static int stringWidth(String text) {
		if (!ready || text == null || text.isEmpty()) {
			return 0;
		}

		float width = 0.0F;

		for (Cell cell : cellsFor(text, DEFAULT_COLOR)) {
			width += cell.advance();
		}

		return Math.round(width);
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
			case LEAF -> 0xFF9CD67A;
			case CLOCK -> 0xFF7FC7D9;
			case SUN -> 0xFFE8D06A;
			case COMPASS -> 0xFF6FC7B8;
			case PACKAGE -> 0xFFE0A070;
			case ACTIVITY -> 0xFFE08AB0;
			case WIFI -> 0xFF7FB8E8;
			case SERVER -> 0xFFA0B0C8;
			case SETTINGS -> 0xFFAAB4BF;
			case DOT -> 0xFF8A9099;
		};
	}

	/** Logs which rows were attributed to mods, so the split can be verified from the log. */
	private static void logModRows(List<Row> rows) {
		List<String> mods = new ArrayList<>();

		for (Row row : rows) {
			if (!row.vanilla() && hasContent(row)) {
				mods.add(textOf(row));
			}
		}

		if (!mods.equals(lastModRows)) {
			lastModRows = mods;
			LOGGER.info("mod-attributed F3 rows ({}): {}", mods.size(), mods);
		}
	}

	private static String textOf(Row row) {
		return row.raw();
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
		EntryClassifier.Entry entry = EntryClassifier.classify(line);
		OhMyF3Config config = OhMyF3Config.get();

		String key = config.chineseLabels ? Labels.key(entry.key()) : entry.key();
		String value = config.chineseLabels ? Labels.value(entry.value()) : entry.value();

		List<Cell> keyCells = key.isEmpty() ? List.of() : cellsFor(key, KEY_COLOR);
		List<Cell> valueCells = cellsFor(value, DEFAULT_COLOR);
		IconPainter.Icon icon = config.icons ? entry.icon() : null;

		// Only rows whose owning entry registered outside the minecraft namespace are mod
		// content. Untagged rows are synthesised by the overlay itself (separators, the
		// "to edit" hints) and stay in the vanilla area.
		boolean vanilla = !Boolean.TRUE.equals(DebugEntryOrigin.modded(line));

		return new Row(icon, keyCells, valueCells, vanilla, false, line);
	}

	/** Splits text into glyph cells, honouring {@code §} colour codes. */
	private static List<Cell> cellsFor(String text, int baseColor) {
		List<Cell> cells = new ArrayList<>(text.length());
		int color = baseColor;

		for (int i = 0; i < text.length(); ) {
			char c = text.charAt(i);

			if (c == FORMATTING_CODE && i + 1 < text.length()) {
				color = colorFor(text.charAt(i + 1), baseColor);
				i += 2;
				continue;
			}

			int codePoint = text.codePointAt(i);
			i += Character.charCount(codePoint);
			Sprite sprite = glyph(codePoint, color);
			cells.add(new Cell(sprite, sprite.advance()));
		}

		return cells;
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
		cardBorderMods = atlas.add(roundedRect(size, radius, Math.max(1, deviceScale / 2.0F)), CARD_BORDER_MODS);
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
