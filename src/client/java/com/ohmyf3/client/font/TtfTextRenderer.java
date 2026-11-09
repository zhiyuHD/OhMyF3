package com.ohmyf3.client.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ohmyf3.font.GlyphOutline;
import com.ohmyf3.font.GlyphRasterizer;
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
 * units, so the atlas texels land on screen pixels one-to-one. Rasterising at the nominal
 * 9px and letting the GUI scale blow it up is what made the first version look blurry.
 */
public final class TtfTextRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger("ohmyf3/font");

	/** Font pixel size in GUI units. */
	private static final int PIXEL_SIZE = 9;

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

	/** Current device pixels per GUI pixel; rebuilt when it changes. */
	private static int deviceScale = -1;

	private static final Map<GlyphKey, Sprite> SPRITES = new HashMap<>();

	/** A glyph instance is colour-specific because the colour is baked into the atlas. */
	private record GlyphKey(int codePoint, int color) {
	}

	private TtfTextRenderer() {
	}

	/**
	 * A glyph already converted to GUI units. {@code rect} is {@code null} for blank glyphs
	 * such as space.
	 */
	private record Sprite(GlyphAtlas.Rect rect, int left, int top, int width, int height, int advance) {
	}

	public static boolean isReady() {
		ensureInitialised();
		return ready;
	}

	public static int lineHeight() {
		return lineHeight;
	}

	/** Width of a string in GUI pixels, ignoring formatting codes. */
	public static int width(String text) {
		if (!ready || text == null || text.isEmpty()) {
			return 0;
		}

		int width = 0;
		int color = DEFAULT_COLOR;

		for (int i = 0; i < text.length(); ) {
			char c = text.charAt(i);

			if (c == FORMATTING_CODE && i + 1 < text.length()) {
				color = colorFor(text.charAt(i + 1), color);
				i += 2;
				continue;
			}

			int codePoint = text.codePointAt(i);
			i += Character.charCount(codePoint);
			width += glyph(codePoint, color).advance;
		}

		return width;
	}

	private static int colorFor(char code, int current) {
		if (code == 'r' || code == 'R') {
			return DEFAULT_COLOR;
		}

		int index = "0123456789abcdef".indexOf(Character.toLowerCase(code));
		return index >= 0 ? LEGACY_COLORS[index] : current;
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
		}

		// Pass 1: make sure every glyph is in the atlas before anything is drawn, so a
		// single upload covers this frame and the quads reference uploaded pixels.
		for (String line : lines) {
			if (line == null || line.isEmpty()) {
				continue;
			}

			int color = DEFAULT_COLOR;

			for (int i = 0; i < line.length(); ) {
				char c = line.charAt(i);

				if (c == FORMATTING_CODE && i + 1 < line.length()) {
					color = colorFor(line.charAt(i + 1), color);
					i += 2;
					continue;
				}

				int codePoint = line.codePointAt(i);
				i += Character.charCount(codePoint);
				glyph(codePoint, color);
			}
		}

		if (atlas.isDirty()) {
			texture.upload();
			atlas.clearDirty();
		}

		// Pass 2: background plates.
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);

			if (line == null || line.isEmpty()) {
				continue;
			}

			int width = width(line);
			int left = alignLeft ? 2 : screenWidth - 2 - width;
			int top = 2 + lineHeight * index;
			graphics.fill(left - 1, top - 1, left + width + 1, top + lineHeight - 1, BACKGROUND_COLOR);
		}

		// Pass 3: glyph quads.
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);

			if (line == null || line.isEmpty()) {
				continue;
			}

			int left = alignLeft ? 2 : screenWidth - 2 - width(line);
			int top = 2 + lineHeight * index;
			int baseline = top + ascent;
			int penX = left;

			int color = DEFAULT_COLOR;

			for (int i = 0; i < line.length(); ) {
				char c = line.charAt(i);

				if (c == FORMATTING_CODE && i + 1 < line.length()) {
					color = colorFor(line.charAt(i + 1), color);
					i += 2;
					continue;
				}

				int codePoint = line.codePointAt(i);
				i += Character.charCount(codePoint);

				Sprite sprite = glyph(codePoint, color);

				if (sprite.rect != null) {
					int x0 = penX + sprite.left;
					int y0 = baseline + sprite.top;
					graphics.blit(textureView, sampler, x0, y0, x0 + sprite.width, y0 + sprite.height,
							sprite.rect.u0(TEXEL_INSET), sprite.rect.u1(TEXEL_INSET),
							sprite.rect.v0(TEXEL_INSET), sprite.rect.v1(TEXEL_INSET));
				}

				penX += sprite.advance;
			}
		}
	}

	// ---------------------------------------------------------------- glyph cache

	private static Sprite glyph(int codePoint, int color) {
		GlyphKey key = new GlyphKey(codePoint, color);
		Sprite cached = SPRITES.get(key);

		if (cached != null) {
			return cached;
		}

		int glyphIndex = font.glyphIndex(codePoint);
		int advance = Math.max(1, Math.round(font.advanceWidth(glyphIndex) * fontScale / deviceScale));
		GlyphOutline outline = font.outline(glyphIndex);
		GlyphRasterizer.Bitmap bitmap = outline == null ? null : GlyphRasterizer.rasterize(outline, fontScale);

		Sprite sprite;

		if (bitmap == null) {
			sprite = new Sprite(null, 0, 0, 0, 0, advance);
		} else {
			GlyphAtlas.Rect rect = atlas.add(bitmap, color);

			if (rect == null) {
				// Atlas full: draw nothing rather than crashing, but keep the advance so the
				// rest of the line still lines up.
				sprite = new Sprite(null, 0, 0, 0, 0, advance);
			} else {
				// Rasterised at device resolution: divide back down to GUI units.
				sprite = new Sprite(rect,
						Math.round(bitmap.left / (float) deviceScale),
						Math.round(bitmap.top / (float) deviceScale),
						Math.max(1, Math.round(bitmap.width / (float) deviceScale)),
						Math.max(1, Math.round(bitmap.height / (float) deviceScale)),
						advance);
			}
		}

		SPRITES.put(key, sprite);
		return sprite;
	}

	/** Re-creates the atlas for a new device-pixel ratio; cached glyphs are invalidated. */
	private static void rebuildAtlas(int newDeviceScale) {
		deviceScale = newDeviceScale;
		// fontScale is "device pixels per font unit".
		fontScale = PIXEL_SIZE * deviceScale / (float) font.unitsPerEm();

		// `true` zeroes the native buffer: an uninitialised atlas leaks garbage into the
		// texture and shows up as stray white blocks between glyphs.
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, GlyphAtlas.SIZE, GlyphAtlas.SIZE, true);
		atlas = new GlyphAtlas(image);
		SPRITES.clear();

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
