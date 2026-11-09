package com.ohmyf3.client.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * Draws debug-screen text with a system TrueType font instead of Minecraft's bitmap font.
 *
 * <p>Glyphs are rasterised on demand, packed into one atlas, uploaded once per batch of new
 * glyphs, and drawn as textured quads through {@link GuiGraphicsExtractor}. Everything is
 * best-effort: if no usable font can be found the renderer reports itself unavailable and
 * the caller falls back to vanilla text.
 *
 * <p>Glyphs are rasterised at the current device-pixel ratio (GUI scale times the pose
 * scale the debug overlay applies) and the quads are drawn at 1/N of that size in GUI
 * units, so the atlas texels land on screen pixels one-to-one.
 *
 * <p>Lines are also scanned for keywords; a matching word gets a procedurally drawn icon
 * inserted in front of it. Icons are part of the line layout, so they take part in width
 * measurement and right-column alignment like any other glyph.
 */
public final class TtfTextRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger("ohmyf3/font");

	/** Font pixel size in GUI units. */
	private static final int PIXEL_SIZE = 9;

	/** Icon edge length and the gap after it, in GUI units. */
	private static final int ICON_SIZE = 10;
	private static final int ICON_GAP = 2;

	/** Colour of debug text that carries no formatting code. */
	private static final int DEFAULT_COLOR = 0xFFE0E0E0;

	private static final int BACKGROUND_COLOR = 0x90505050;

	/** Vanilla legacy palette, indexed by the hex digit after the section sign. */
	private static final int[] LEGACY_COLORS = {
			0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA,
			0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
			0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF,
			0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF
	};

	private static final char FORMATTING_CODE = '\u00a7';

	private static final Identifier ATLAS_ID = Identifier.fromNamespaceAndPath("ohmyf3", "font_atlas");

	/** Half-texel inset so linear filtering never samples a neighbouring glyph. */
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

	/** Current device pixels per GUI pixel; the atlas is rebuilt when it changes. */
	private static int deviceScale = -1;

	private static final Map<GlyphKey, Sprite> GLYPHS = new HashMap<>();
	private static final Map<IconKey, Sprite> ICONS = new HashMap<>();

	private TtfTextRenderer() {
	}

	/**
	 * A drawable item already converted to GUI units. {@code rect} is {@code null} for blank
	 * glyphs such as space.
	 */
	private record Sprite(GlyphAtlas.Rect rect, int left, int top, int width, int height, float advance) {
	}

	/** One slot of a laid-out line: a glyph or an icon, with its advance. */
	private record Cell(Sprite sprite, boolean icon, float advance) {
	}

	/** A glyph instance is colour-specific because the colour is baked into the atlas. */
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

	/**
	 * Draws the two debug columns, mirroring vanilla's layout (2px margins, one background
	 * plate per line, left column left-aligned and right column right-aligned).
	 */
	public static void drawLines(GuiGraphicsExtractor graphics, List<String> lines, boolean alignLeft,
			int screenWidth) {
		ensureInitialised();

		if (!ready) {
			return;
		}

		// Follow the effective device-pixel ratio so glyphs stay pixel-aligned.
		float poseScale = graphics.pose().m00();
		int target = Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale() * poseScale));

		if (target != deviceScale) {
			rebuildAtlas(target);
			LOGGER.info("device scale {} (window guiScale {}, pose scale {})", target,
					Minecraft.getInstance().getWindow().getGuiScale(), poseScale);
		}

		// Build the layout first: this rasterises any glyph or icon that is not cached yet,
		// so one upload covers the whole frame before a single quad is emitted.
		List<List<Cell>> layouts = new ArrayList<>(lines.size());

		for (String line : lines) {
			if (line == null || line.isEmpty()) {
				layouts.add(List.of());
			} else {
				layouts.add(layout(line));
			}
		}

		if (atlas.isDirty()) {
			texture.upload();
			atlas.clearDirty();
		}

		// Background plates.
		for (int index = 0; index < layouts.size(); index++) {
			List<Cell> cells = layouts.get(index);

			if (cells.isEmpty()) {
				continue;
			}

			int width = widthOf(cells);
			int left = alignLeft ? 2 : screenWidth - 2 - width;
			int top = 2 + lineHeight * index;
			graphics.fill(left - 1, top - 1, left + width + 1, top + lineHeight - 1, BACKGROUND_COLOR);
		}

		// Glyph and icon quads.
		for (int index = 0; index < layouts.size(); index++) {
			List<Cell> cells = layouts.get(index);

			if (cells.isEmpty()) {
				continue;
			}

			int left = alignLeft ? 2 : screenWidth - 2 - widthOf(cells);
			int top = 2 + lineHeight * index;
			int baseline = top + ascent;
			int iconTop = top + (lineHeight - ICON_SIZE) / 2;

			// Keep the pen fractional: rounding every advance to whole pixels is what made
			// the letters bunch up at 9px.
			float penX = left;

			for (Cell cell : cells) {
				Sprite sprite = cell.sprite();

				if (sprite.rect() != null) {
					// Subpixel placement: the blit API only takes whole GUI pixels, which are
					// several screen pixels wide, so snapping the pen to them makes some
					// letter pairs collide and others drift apart. A fractional pose
					// translation moves the quad by the remainder, and the render state
					// captures the pose per quad, so the position survives.
					float targetX = cell.icon() ? penX + ICON_GAP : penX + sprite.left();
					int x0 = Math.round(targetX);
					float remainder = targetX - x0;
					int y0 = cell.icon() ? iconTop : baseline + sprite.top();

					boolean shifted = Math.abs(remainder) > 0.001F;

					if (shifted) {
						graphics.pose().pushMatrix();
						graphics.pose().translate(remainder, 0.0F);
					}

					graphics.blit(textureView, sampler, x0, y0, x0 + sprite.width(), y0 + sprite.height(),
							sprite.rect().u0(TEXEL_INSET), sprite.rect().u1(TEXEL_INSET),
							sprite.rect().v0(TEXEL_INSET), sprite.rect().v1(TEXEL_INSET));

					if (shifted) {
						graphics.pose().popMatrix();
					}
				}

				penX += cell.advance();
			}
		}
	}

	private static int widthOf(List<Cell> cells) {
		float width = 0;

		for (Cell cell : cells) {
			width += cell.advance();
		}

		return Math.round(width);
	}

	// ---------------------------------------------------------------- layout

	/**
	 * Splits a line into glyph cells, inserting an icon in front of the first occurrence of
	 * each recognised keyword. Formatting codes are consumed and only affect the colour.
	 */
	private static List<Cell> layout(String line) {
		List<Cell> cells = new ArrayList<>(line.length() + 4);
		Set<IconPainter.Icon> inserted = EnumSet.noneOf(IconPainter.Icon.class);
		int color = DEFAULT_COLOR;

		for (int i = 0; i < line.length(); ) {
			char c = line.charAt(i);

			if (c == FORMATTING_CODE && i + 1 < line.length()) {
				color = colorFor(line.charAt(i + 1), color);
				i += 2;
				continue;
			}

			IconPainter.Icon icon = KeywordIcons.iconStartingAt(line, i);

			if (icon != null && inserted.add(icon)) {
				// Equal padding on both sides so the icon does not touch the digits it
				// usually follows ("3806 MiB" -> "3806 [icon] MiB").
				cells.add(new Cell(icon(icon, color), true, ICON_GAP + ICON_SIZE + ICON_GAP));
			}

			int codePoint = line.codePointAt(i);
			i += Character.charCount(codePoint);
			Sprite sprite = glyph(codePoint, color);
			cells.add(new Cell(sprite, false, sprite.advance()));
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
		// Fractional advance in GUI units; rounding it here loses the font's side bearings.
		float advance = Math.max(1.0F, font.advanceWidth(glyphIndex) * (PIXEL_SIZE / (float) font.unitsPerEm()));
		GlyphOutline outline = font.outline(glyphIndex);
		CoverageBitmap bitmap = outline == null ? null : GlyphRasterizer.rasterize(outline, fontScale);

		Sprite sprite;

		if (bitmap == null) {
			sprite = new Sprite(null, 0, 0, 0, 0, advance);
		} else {
			sprite = place(bitmap, color, advance, true);
		}

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
		Sprite sprite = place(bitmap, color, ICON_SIZE + ICON_GAP, false);
		ICONS.put(key, sprite);
		return sprite;
	}

	/** Uploads a bitmap into the atlas and converts its pixel offsets to GUI units. */
	private static Sprite place(CoverageBitmap bitmap, int color, float advance, boolean useBitmapOffsets) {
		// Quads can only be placed and sized on whole GUI pixels, and one GUI pixel is
		// several screen pixels. Rounding the bitmap's size down to GUI units and letting
		// the GUI scale blow it back up resamples every glyph. Padding the bitmap out to the
		// device grid instead keeps one texel on exactly one screen pixel.
		CoverageBitmap aligned = useBitmapOffsets ? alignToDeviceGrid(bitmap) : bitmap;
		GlyphAtlas.Rect rect = atlas.add(aligned, color);

		if (rect == null) {
			// Atlas full: draw nothing rather than crashing, but keep the advance so the rest
			// of the line still lines up.
			return new Sprite(null, 0, 0, 0, 0, advance);
		}

		int left = aligned.left / deviceScale;
		int top = aligned.top / deviceScale;
		int width = Math.max(1, aligned.width / deviceScale);
		int height = Math.max(1, aligned.height / deviceScale);

		return new Sprite(rect, left, top, width, height, advance);
	}

	/**
	 * Shifts a bitmap inside a larger box so its origin and its size are exact multiples of
	 * the device scale. Rounding is then confined to transparent padding rather than to the
	 * glyph itself.
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

	/** Re-creates the atlas for a new device-pixel ratio; cached sprites are invalidated. */
	private static void rebuildAtlas(int newDeviceScale) {
		deviceScale = newDeviceScale;
		// fontScale is "device pixels per font unit".
		fontScale = PIXEL_SIZE * deviceScale / (float) font.unitsPerEm();

		// `true` zeroes the native buffer: an uninitialised atlas leaks garbage into the
		// texture and shows up as stray white blocks between glyphs.
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

		texture.upload();
		atlas.clearDirty();
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
			// Never let a font problem take the game down: fall back to vanilla rendering.
			LOGGER.warn("System font setup failed; F3 keeps vanilla text", throwable);
		}
	}
}
