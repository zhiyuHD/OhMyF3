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

	private static Map<GlyphKey, Sprite> GLYPHS = new HashMap<>();
	private static Map<IconKey, Sprite> ICONS = new HashMap<>();

	/**
	 * One baked atlas and its caches, per rasterisation scale.
	 *
	 * <p>The overlay draws at {@code guiScale * zoom} while screens draw at {@code guiScale}. With a
	 * single global atlas each consumer rebuilt it back and forth every frame, and whichever ran
	 * second drew with the other's glyph sizes. Keeping one snapshot per scale means each context
	 * gets glyphs baked for exactly the scale it is drawn at.
	 */
	private record Snapshot(int scale, GlyphAtlas glyphs, DynamicTexture texture, GpuTextureView view,
			GpuSampler sampler, float fontScale, Map<GlyphKey, Sprite> glyphCache, Map<IconKey, Sprite> iconCache,
			GlyphAtlas.Rect cardFill, GlyphAtlas.Rect cardBorder, GlyphAtlas.Rect cardBorderMods) {
	}

	private static final Map<Integer, Snapshot> ATLASES = new HashMap<>();

	private static List<String> lastModRows = List.of();

	/** Last drawn columns, replayed while the overlay slides out. */
	/** Raw lines the overlay last drew, reused by the settings screen's layout editor. */
	private static boolean capturePending;
	private static boolean capturing;

	private static List<String> lastLeftLines = List.of();
	private static List<String> lastRightLines = List.of();

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

	/**
	 * Width of the card the overlay last drew for a column.
	 *
	 * <p>The layout editor places the column's default anchor with this rather than with its sample
	 * card, so the dummy overlay lines up with the real one instead of with the mock content.
	 */
	public static int lastCardWidth(boolean alignLeft) {
		return alignLeft ? lastLeftCardWidth : lastRightCardWidth;
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
			drawColumn(graphics, lastLeftRows, true, lastLeftWidth, -offset * travel(true), 2);
		}

		if (!lastRightRows.isEmpty()) {
			drawColumn(graphics, lastRightRows, false, lastRightWidth, offset * travel(false), 2);
		}

		graphics.pose().popMatrix();
	}

	/** Called once per frame, before the overlay assembles its columns. */
	/**
	 * Asks the next overlay extraction to only record its rows, without drawing them.
	 *
	 * <p>The layout editor calls this before running the overlay itself: that is what lets it show
	 * live F3 content without the player having to open F3 first, and without double-drawing.
	 */
	public static void requestCapture() {
		capturePending = true;
	}

	/** True while an extraction is only recording rows (the layout editor's capture pass). */
	public static boolean isCapturing() {
		return capturing;
	}

	public static void beginFrame() {
		// A capture requested since the last frame turns this whole extraction into a read-only
		// pass: the rows are recorded, nothing is drawn.
		capturing = capturePending;
		capturePending = false;
	}

	public static void drawLines(GuiGraphicsExtractor graphics, List<String> lines, boolean alignLeft,
			int screenWidth) {
		drawLines(graphics, lines, alignLeft, screenWidth, 0.0F);
	}

	/**
	 * Draws a column, splitting rows that are always on from the ones that only appear with F3.
	 *
	 * <p>Always-on rows are pinned to their resting position and never animated; the rest start
	 * {@code slideOffset} pixels to the side and glide in below them. Without the split, closing the
	 * overlay would push the always-on rows off-screen with everything else.
	 */
	public static void drawLines(GuiGraphicsExtractor graphics, List<String> lines, boolean alignLeft,
			int screenWidth, float slideOffset) {
		ensureInitialised();

		if (!ready) {
			return;
		}

		// Remember what the real overlay shows, so the layout editor can preview live content
		// instead of sample data.
		List<String> snapshot = lines == null ? List.of() : List.copyOf(lines);

		if (alignLeft) {
			lastLeftLines = snapshot;
		} else {
			lastRightLines = snapshot;
		}

		float poseScale = graphics.pose().m00();
		int target = Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale() * poseScale));

		guiToScreen = Minecraft.getInstance().getWindow().getGuiScale() * poseScale;

		if (target != deviceScale) {
			useScale(target);
			LOGGER.info("device scale {} (window guiScale {}, pose scale {})", target,
					Minecraft.getInstance().getWindow().getGuiScale(), poseScale);
		}

		if (capturing) {
			return;
		}

		// Layout first: this rasterises anything not yet cached, so one upload covers the
		// whole frame before a single quad is emitted.
		List<Row> rows = new ArrayList<>(lines.size());

		boolean split = slideOffset != 0.0F && lines.stream().anyMatch(TtfTextRenderer::pinnedToOverlay);

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

		if (split) {
			// One pass over the full layout: pinned rows keep their slot, everything else slides.
			drawColumn(graphics, rows, alignLeft, screenWidth, slideOffset, 2, alwaysOnSet(rows));
		} else {
			drawColumn(graphics, rows, alignLeft, screenWidth, slideOffset, 2);
		}
	}

	// ---------------------------------------------------------------- cards

	private static int drawColumn(GuiGraphicsExtractor graphics, List<Row> rows, boolean alignLeft,
			int screenWidth, float xOffset, int yStart) {
		return drawColumn(graphics, rows, alignLeft, screenWidth, xOffset, yStart, java.util.Set.of());
	}

	/**
	 * Draws a column, translating every row that is <em>not</em> in {@code pinned} by {@code
	 * slideOffset}.
	 *
	 * <p>Always-on rows are pinned: they keep their slot in the full layout so the F3-only rows can
	 * glide into the space around them instead of pushing them aside.
	 */
	private static int drawColumn(GuiGraphicsExtractor graphics, List<Row> rows, boolean alignLeft,
			int screenWidth, float xOffset, int yStart, java.util.Set<Row> pinned) {
		List<Card> cards = new ArrayList<>();
		List<Row> current = new ArrayList<>();

		// Positions are stored per row occurrence, so two lines of the same category (every Sodium
		// line classifies as "sodium") stay independent.
		List<String> rowKeys = rowKeys(rows);

		// Rows the player dragged out of the column are drawn as standalone cards first, and are
		// excluded from the flow below so the column closes up behind them.
		for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
			Row row = rows.get(rowIndex);
			int[] position = pinnedPosition(rowKeys.get(rowIndex));

			if (position == null) {
				continue;
			}

			int pinnedX = Math.round(position[0] * screenWidth / 1000.0F);
			int pinnedY = Math.round(position[1] * graphics.guiHeight() / 1000.0F);
			Card single = new Card(List.of(row), !row.vanilla());
			int width = cardWidth(single);
			int height = CARD_PADDING * 2 + lineHeight;
			drawCard(graphics, pinnedX, pinnedY, width, height, !row.vanilla());
			int maxKey = maxKeyWidth(single);
			int valueColumn = pinnedX + CARD_PADDING + gutter() + (maxKey > 0 ? maxKey + KEY_GAP : 0);
			drawRow(graphics, row, pinnedX, pinnedY + CARD_PADDING, valueColumn);
		}

		// Vanilla rows keep their blank-line grouping; mod rows are collected into one card.
		for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
			Row row = rows.get(rowIndex);

			if (!row.vanilla() || pinnedPosition(rowKeys.get(rowIndex)) != null) {
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

		for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
			Row row = rows.get(rowIndex);

			if (!row.vanilla() && hasContent(row) && pinnedPosition(rowKeys.get(rowIndex)) == null) {
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
			return 0;
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

		// Absolute positions where the player dragged the columns, stored in permille of the screen
		// so a resolution change keeps the arrangement proportional.
		int[] columnPos = OhMyF3Config.get().position(alignLeft ? "column_left" : "column_right");
		int guiHeight = graphics.guiHeight();
		int x = columnPos != null ? Math.round(columnPos[0] * screenWidth / 1000.0F)
				: (alignLeft ? 2 : screenWidth - 2 - cardWidth) + Math.round(xOffset);
		int y = columnPos != null ? Math.round(columnPos[1] * guiHeight / 1000.0F) : yStart;

		float slide = xOffset;

		for (Card card : cards) {
			int height = CARD_PADDING * 2 + card.rows().size() * lineHeight;
			// A card that holds a pinned row stays put: the rows around it glide into the empty
			// space, so the always-on row never jumps. Other cards travel with their rows.
			boolean holdsPinned = card.rows().stream().anyMatch(pinned::contains);
			int cardX = holdsPinned ? x : x + Math.round(slide);
			drawCard(graphics, cardX, y, cardWidth, height, card.mods());

			int maxKey = maxKeyWidth(card);

			for (int index = 0; index < card.rows().size(); index++) {
				Row row = card.rows().get(index);
				int rowX = pinned.contains(row) ? x : x + Math.round(slide);

				// Pinned rows sit exactly on their slot; the rest arrive from the side.
				drawRow(graphics, row, rowX, y + CARD_PADDING + index * lineHeight,
						rowX + CARD_PADDING + gutter() + (maxKey > 0 ? maxKey + KEY_GAP : 0));
			}

			y += height + CARD_GAP;
		}

		return y - yStart;
	}

	/**
	 * Config key for a row the player may have pinned: {@code row:<field>}.
	 *
	 * <p>The field name (not the localised label or the value) is used so the position survives
	 * language switches and changing numbers.
	 */
	private static String pinnedKey(Row row) {
		if (row == null || row.raw() == null || row.raw().isEmpty() || !hasContent(row)) {
			return null;
		}

		String key = EntryClassifier.classify(row.raw()).key();

		return key == null || key.isEmpty() ? null : "row:" + key;
	}

	/**
	 * Stored position of a pinned row, or {@code null} when it still flows in its column.
	 *
	 * <p>Only a row that the player actually moved is pinned — a row merely having a classifier key
	 * is not enough, otherwise every un-moved row would be filtered out of the column and vanish.
	 */
	/** Identity set of the rows that must not move with the slide. */
	private static java.util.Set<Row> alwaysOnSet(List<Row> rows) {
		java.util.Set<Row> pinned = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

		for (Row row : rows) {
			if (row != null && row.raw() != null && pinnedToOverlay(row.raw())) {
				pinned.add(row);
			}
		}

		return pinned;
	}

	/**
	 * True when the row comes from an entry that is always on, i.e. it must stay put and must not be
	 * animated in with the rest of the overlay.
	 */
	private static boolean pinnedToOverlay(String line) {
		net.minecraft.resources.Identifier owner = DebugEntryOrigin.ownerOf(line);

		return owner != null && Minecraft.getInstance().debugEntries.getStatus(owner)
				== net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.ALWAYS_ON;
	}

	private static int[] pinnedPosition(String key) {
		return key == null ? null : OhMyF3Config.get().position(key);
	}

	/** Config keys for a column's rows, numbered by occurrence within their category. */
	private static List<String> rowKeys(List<Row> rows) {
		List<String> keys = new ArrayList<>(rows.size());
		java.util.Map<String, Integer> seen = new java.util.HashMap<>();

		for (Row row : rows) {
			String base = pinnedKey(row);

			if (base == null) {
				keys.add(null);
				continue;
			}

			int occurrence = seen.merge(base, 1, Integer::sum) - 1;
			keys.add(base + "#" + occurrence);
		}

		return keys;
	}

	/**
	 * One row drawn by {@link #drawPreviewColumn}, for hit-testing in the layout editor.
	 *
	 * @param raw the original debug line, used to derive the row's key
	 */
	public record PreviewRow(String raw, int y, int height) {
	}

	/**
	 * Draws a column exactly like the overlay does — split into cards at blank lines, with the same
	 * paddings and gaps — and returns the row rects so the editor can hit-test them.
	 *
	 * <p>Using the real grouping matters: a single-card preview does not look like F3, and the row
	 * boxes would not line up with the cards actually drawn.
	 */
	public static List<PreviewRow> drawPreviewColumn(GuiGraphicsExtractor graphics, List<String> lines, int x,
			int y) {
		if (!ready || lines == null || lines.isEmpty()) {
			return List.of();
		}

		List<Row> rows = new ArrayList<>(lines.size());

		for (String line : lines) {
			rows.add(line == null || line.isEmpty() ? Row.EMPTY : layout(line));
		}

		List<Card> cards = new ArrayList<>();
		List<Row> current = new ArrayList<>();

		for (Row row : rows) {
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

		if (cards.isEmpty()) {
			return List.of();
		}

		int width = 0;

		for (Card card : cards) {
			width = Math.max(width, cardWidth(card));
		}

		List<PreviewRow> drawn = new ArrayList<>(rows.size());
		int cursorY = y;

		for (Card card : cards) {
			int height = CARD_PADDING * 2 + card.rows().size() * lineHeight;
			drawCard(graphics, x, cursorY, width, height, card.mods());

			int maxKey = maxKeyWidth(card);
			int valueColumn = x + CARD_PADDING + gutter() + (maxKey > 0 ? maxKey + KEY_GAP : 0);

			for (int index = 0; index < card.rows().size(); index++) {
				Row row = card.rows().get(index);
				int rowY = cursorY + CARD_PADDING + index * lineHeight;
				drawRow(graphics, row, x, rowY, valueColumn);
				drawn.add(new PreviewRow(row.raw(), rowY, lineHeight));
			}

			cursorY += height + CARD_GAP;
		}

		return drawn;
	}

	/**
	 * Draws a preview of the column for the settings screen's layout editor: one card with the given
	 * sample lines at an absolute position. Uses the same layout, font and card texture as the real
	 * overlay, so what is dragged is what the player will see.
	 */
	public static void drawPreview(GuiGraphicsExtractor graphics, List<String> lines, int x, int y) {
		if (!ready || lines == null || lines.isEmpty()) {
			return;
		}

		List<Row> rows = new ArrayList<>(lines.size());

		for (String line : lines) {
			rows.add(line == null || line.isEmpty() ? Row.EMPTY : layout(line));
		}

		Card card = new Card(rows, false);
		int width = cardWidth(card);
		int height = CARD_PADDING * 2 + rows.size() * lineHeight;
		drawCard(graphics, x, y, width, height, false);

		int maxKey = maxKeyWidth(card);
		int valueColumn = x + CARD_PADDING + gutter() + (maxKey > 0 ? maxKey + KEY_GAP : 0);

		for (int index = 0; index < rows.size(); index++) {
			drawRow(graphics, rows.get(index), x, y + CARD_PADDING + index * lineHeight, valueColumn);
		}
	}

	/** Width the preview card would occupy, without drawing it. */
	public static int previewWidth(List<String> lines) {
		if (!ready || lines == null || lines.isEmpty()) {
			return 0;
		}

		List<Row> rows = new ArrayList<>(lines.size());

		for (String line : lines) {
			rows.add(line == null || line.isEmpty() ? Row.EMPTY : layout(line));
		}

		return cardWidth(new Card(rows, false));
	}

	/** Height the preview card would occupy, without drawing it. */
	public static int previewHeight(List<String> lines) {
		return CARD_PADDING * 2 + (lines == null ? 0 : lines.size()) * lineHeight;
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
		// The settings screen can be the first thing drawn (e.g. opened from ModMenu), so this has
		// to initialise on demand instead of assuming the F3 overlay already did.
		ensureInitialised();

		if (!ready || text == null || text.isEmpty()) {
			return 0.0F;
		}

		List<Cell> cells = cellsFor(text, color);
		flushAtlas();
		return drawCells(graphics, cells, x, baseline);
	}

	/**
	 * Pushes newly rasterised glyphs to the atlas texture.
	 *
	 * <p>The F3 path flushes once per column after laying out its rows; this entry point can be the
	 * first thing to draw (the settings screen), so it has to flush itself or its glyphs stay in the
	 * CPU bitmap and the texture renders empty.
	 */
	private static void flushAtlas() {
		if (atlas != null && texture != null && atlas.isDirty()) {
			texture.upload();
			atlas.clearDirty();
		}
	}

	/**
	 * Ensures glyphs are rasterised for the given device scale.
	 *
	 * <p>The F3 overlay rebuilds the atlas from the pose scale it observes; screens draw at the
	 * plain GUI scale, so without this the first screen to open would bake 1x glyphs and every later
	 * draw would upscale them — visibly blurry text.
	 */
	public static void ensureDeviceScale(int scale) {
		ensureInitialised();

		if (!ready || scale <= 0) {
			return;
		}

		useScale(scale);
	}

	/** Draws one icon glyph at {@code (x, top)}; used by the custom-item editor. */
	public static void drawIcon(GuiGraphicsExtractor graphics, IconPainter.Icon icon, float x, float top) {
		ensureInitialised();

		if (!ready || icon == null) {
			return;
		}

		Sprite sprite = icon(icon, DEFAULT_COLOR);
		flushAtlas();

		if (sprite.rect() == null) {
			return;
		}

		int x0 = Math.round(x + sprite.left());
		int y0 = Math.round(top + sprite.top());
		blitRegion(graphics, sprite.rect(), 0, 0, sprite.rect().width(), sprite.rect().height(), x0, y0,
				x0 + sprite.width(), y0 + sprite.height());
	}

	/**
	 * The lines the overlay last drew for a column, or an empty list when F3 has not been open yet.
	 *
	 * <p>Used by the layout editor to render the real content rather than a sample.
	 */
	public static List<String> lastLines(boolean alignLeft) {
		return alignLeft ? lastLeftLines : lastRightLines;
	}

	/** Width of {@code text} in GUI pixels, with the same metrics {@link #drawString} uses. */
	public static int stringWidth(String text) {
		ensureInitialised();

		if (!ready || text == null || text.isEmpty()) {
			return 0;
		}

		float width = 0.0F;
		List<Cell> cells = cellsFor(text, DEFAULT_COLOR);
		flushAtlas();

		for (Cell cell : cells) {
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
		// Custom rows carry their icon in a marker prefix; they bypass classification entirely.
		IconPainter.Icon forcedIcon = null;

		if (line != null && !line.isEmpty() && line.charAt(0) == '\u0001') {
			int end = line.indexOf('\u0001', 1);

			if (end > 0) {
				try {
					forcedIcon = IconPainter.Icon.valueOf(line.substring(1, end));
				} catch (IllegalArgumentException ignored) {
					// Unknown icon name: fall back to no icon rather than dropping the row.
				}

				line = line.substring(end + 1);
			}
		}

		if (forcedIcon != null) {
			int split = line.indexOf(": ");
			String key = split < 0 ? line : line.substring(0, split);
			String value = split < 0 ? "" : line.substring(split + 2);
			boolean showIcon = OhMyF3Config.get().icons;
			return new Row(showIcon ? forcedIcon : null, cellsFor(key, KEY_COLOR), cellsFor(value, DEFAULT_COLOR),
					false, false, line);
		}

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

	/** Switches to the atlas baked for {@code scale}, building it on first use. */
	private static void useScale(int scale) {
		if (scale <= 0 || (scale == deviceScale && atlas != null)) {
			return;
		}

		if (deviceScale > 0 && atlas != null) {
			ATLASES.put(deviceScale, new Snapshot(deviceScale, atlas, texture, textureView, sampler, fontScale,
					GLYPHS, ICONS, cardFill, cardBorder, cardBorderMods));
		}

		Snapshot snapshot = ATLASES.get(scale);

		if (snapshot != null) {
			deviceScale = snapshot.scale();
			atlas = snapshot.glyphs();
			texture = snapshot.texture();
			textureView = snapshot.view();
			sampler = snapshot.sampler();
			fontScale = snapshot.fontScale();
			GLYPHS = snapshot.glyphCache();
			ICONS = snapshot.iconCache();
			cardFill = snapshot.cardFill();
			cardBorder = snapshot.cardBorder();
			cardBorderMods = snapshot.cardBorderMods();
			return;
		}

		// The previous scale's texture and caches are still assigned; rebuilding must not adopt them,
		// or the two snapshots would share one texture/map and overwrite each other (clearing the old
		// map in place and reusing it is what garbled both scales after cycling the zoom).
		texture = null;
		textureView = null;
		sampler = null;
		GLYPHS = new HashMap<>();
		ICONS = new HashMap<>();

		rebuildAtlas(scale);
		ATLASES.put(scale, new Snapshot(scale, atlas, texture, textureView, sampler, fontScale, GLYPHS, ICONS,
				cardFill, cardBorder, cardBorderMods));
		LOGGER.info("atlas built for device scale {}", scale);
	}

	private static void rebuildAtlas(int newDeviceScale) {
		deviceScale = newDeviceScale;
		fontScale = PIXEL_SIZE * deviceScale / (float) font.unitsPerEm();

		// `true` zeroes the buffer: uninitialised memory leaks garbage into the texture.
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, GlyphAtlas.SIZE, GlyphAtlas.SIZE, true);
		atlas = new GlyphAtlas(image);
		GLYPHS.clear();
		ICONS.clear();

		if (texture == null) {
			texture = new DynamicTexture(() -> "ohmyf3 system font atlas " + newDeviceScale, image);
			Minecraft.getInstance().getTextureManager()
					.register(ATLAS_ID.withSuffix("_" + newDeviceScale), texture);
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
