package com.ohmyf3.client.ui;

import com.ohmyf3.client.font.TtfTextRenderer;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Material 3 building blocks for the settings screen.
 *
 * <p>Only what this screen needs: the dark baseline colour roles, the shape scale, and three
 * composite components (list item with a switch, filled button, text button). Everything is drawn
 * with the mod's TrueType font and {@link Shapes}, so it scales with the GUI and matches the F3
 * cards instead of looking like vanilla's flat widgets.
 */
public final class M3 {
	// Material 3 dark baseline roles.
	public static final int PRIMARY = 0xFFD0BCFF;
	public static final int ON_PRIMARY = 0xFF381E72;
	public static final int PRIMARY_CONTAINER = 0xFF4F378B;
	public static final int ON_PRIMARY_CONTAINER = 0xFFEADDFF;
	public static final int SECONDARY_CONTAINER = 0xFF4A4458;
	public static final int ON_SECONDARY_CONTAINER = 0xFFE8DEF8;
	public static final int SURFACE = 0xF2141218;
	public static final int SURFACE_CONTAINER = 0xFF211F26;
	public static final int SURFACE_CONTAINER_HIGH = 0xFF2B2930;
	public static final int SURFACE_CONTAINER_HIGHEST = 0xFF36343B;
	public static final int ON_SURFACE = 0xFFE6E0E9;
	public static final int ON_SURFACE_VARIANT = 0xFFCAC4D0;
	/** Supporting text that has to stay legible on the darker containers. */
	public static final int ON_SURFACE_VARIANT_BRIGHT = 0xFFDBD5E0;
	public static final int OUTLINE = 0xFF938F99;
	public static final int OUTLINE_VARIANT = 0xFF49454F;
	public static final int ERROR = 0xFFF2B8B5;

	// Shape scale.
	public static final int RADIUS_SMALL = 8;
	public static final int RADIUS_MEDIUM = 12;
	public static final int RADIUS_LARGE = 16;
	public static final int RADIUS_EXTRA_LARGE = 28;

	/** Height of one list item (label + supporting line + control). */
	public static final int ROW_HEIGHT = 44;
	public static final int ROW_GAP = 8;

	private M3() {
	}

	/** Screen scrim: Material's modal background, so the world behind stays visible but muted. */
	public static void scrim(GuiGraphicsExtractor graphics, int width, int height, float fade) {
		graphics.fill(0, 0, width, height, Anim.alpha(0x99000000, fade));
	}

	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		Shapes.filled(graphics, x, y, width, height, RADIUS_EXTRA_LARGE, SURFACE);
	}

	public static void title(GuiGraphicsExtractor graphics, String text, int x, int y) {
		TtfTextRenderer.drawString(graphics, text, x, y, ON_SURFACE);
	}

	public static void supporting(GuiGraphicsExtractor graphics, String text, int x, int y) {
		TtfTextRenderer.drawString(graphics, text, x, y, ON_SURFACE_VARIANT);
	}

	/** Section header: label in the primary colour, like M3's "list subheader". */
	public static void section(GuiGraphicsExtractor graphics, String text, int x, int y) {
		TtfTextRenderer.drawString(graphics, text, x, y, PRIMARY);
	}

	/**
	 * One settings row: rounded container, label, supporting line and an M3 switch on the right.
	 *
	 * @return {@code true} when the switch was toggled by this draw (never — the caller handles
	 *         input and only uses this to paint)
	 */
	public static void switchRow(GuiGraphicsExtractor graphics, int x, int y, int width, String label, String detail,
			boolean value, boolean hovered) {
		int height = ROW_HEIGHT;
		float hover = Anim.value("row:" + label, hovered ? 1.0F : 0.0F);
		float on = Anim.value("switch:" + label, value ? 1.0F : 0.0F);

		Shapes.filled(graphics, x, y, width, height, RADIUS_LARGE,
				Anim.mix(SURFACE_CONTAINER_HIGH, SURFACE_CONTAINER_HIGHEST, hover));

		// Supporting text has to stay above the "barely visible" line: the container is already
		// dark, so this uses the bright variant of onSurfaceVariant rather than a dimmed outline.
		TtfTextRenderer.drawString(graphics, label, x + 16, y + 17, ON_SURFACE);
		TtfTextRenderer.drawString(graphics, detail, x + 16, y + 30,
				Anim.mix(ON_SURFACE_VARIANT, ON_SURFACE_VARIANT_BRIGHT, hover));

		drawSwitch(graphics, x + width - 16 - 34, y + (height - 20) / 2.0F, on);
	}

	/**
	 * M3 switch: pill track, circular thumb, tonal colours.
	 *
	 * @param on 0 = off, 1 = on; the thumb and the track colours interpolate between them, so the
	 *           caller can drive a real transition instead of a hard flip
	 */
	public static void drawSwitch(GuiGraphicsExtractor graphics, int x, float y, float on) {
		int trackWidth = 34;
		int trackHeight = 20;
		float clamped = Math.max(0.0F, Math.min(on, 1.0F));

		int track = Anim.mix(SURFACE_CONTAINER_HIGHEST, PRIMARY, clamped);
		int trackOutline = Anim.mix(OUTLINE, PRIMARY, clamped);
		Shapes.outlined(graphics, x, Math.round(y), trackWidth, trackHeight, trackHeight / 2, track, trackOutline);

		// 3px inset keeps the thumb off the track edge in both states (it used to sit flush).
		float thumbRadius = 7.0F;
		float inset = 3.0F;
		float travel = trackWidth - inset * 2 - thumbRadius * 2;
		float thumbCenterX = x + inset + thumbRadius + travel * clamped;
		Shapes.circle(graphics, thumbCenterX, y + trackHeight / 2.0F, thumbRadius,
				Anim.mix(OUTLINE, ON_PRIMARY, clamped));
	}

	/** Filled tonal button; returns nothing, the caller owns hit-testing. */
	public static void filledButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String label,
			boolean hovered) {
		float hover = Anim.value("btn:" + label, hovered ? 1.0F : 0.0F);
		Shapes.filled(graphics, x, y, width, height, height / 2, Anim.mix(PRIMARY, PRIMARY_CONTAINER, hover));
		int textWidth = TtfTextRenderer.stringWidth(label);
		TtfTextRenderer.drawString(graphics, label, x + (width - textWidth) / 2.0F,
				Math.round(y + height / 2.0F + TtfTextRenderer.lineHeight() / 2.0F - 2),
				Anim.mix(ON_PRIMARY, ON_PRIMARY_CONTAINER, hover));
	}

	/** Text button (no fill), used for the secondary actions. */
	public static void textButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String label,
			boolean hovered) {
		float hover = Anim.value("btn:" + label, hovered ? 1.0F : 0.0F);

		if (hover > 0.01F) {
			Shapes.filled(graphics, x, y, width, height, height / 2, Anim.alpha(SURFACE_CONTAINER_HIGHEST, hover));
		}

		int textWidth = TtfTextRenderer.stringWidth(label);
		TtfTextRenderer.drawString(graphics, label, x + (width - textWidth) / 2.0F,
				Math.round(y + height / 2.0F + TtfTextRenderer.lineHeight() / 2.0F - 2),
				Anim.mix(ON_SURFACE_VARIANT_BRIGHT, ON_SURFACE, hover));
	}

	/** Filled tonal "chip" for the layout-edit mode banner. */
	public static void chip(GuiGraphicsExtractor graphics, int x, int y, String label, int background, int foreground) {
		int width = TtfTextRenderer.stringWidth(label) + 20;
		Shapes.filled(graphics, x, y, width, 20, 10, background);
		TtfTextRenderer.drawString(graphics, label, x + 10, y + 14, foreground);
	}

	// ---------------------------------------------------------------- ripple

	private record Ripple(float x, float y, long startNanos) {
	}

	private static final java.util.Map<String, Ripple> RIPPLES = new java.util.HashMap<>();

	/** Starts a Material ripple at the pointer, clipped to the component it belongs to. */
	public static void ripple(String key, double x, double y) {
		RIPPLES.put(key, new Ripple((float) x, (float) y, System.nanoTime()));
	}

	/** Draws (and expires) the ripple for {@code key}; call right after painting the component. */
	public static void drawRipple(GuiGraphicsExtractor graphics, String key, int x, int y, int width, int height) {
		Ripple ripple = RIPPLES.get(key);

		if (ripple == null) {
			return;
		}

		float progress = (System.nanoTime() - ripple.startNanos()) / 320_000_000.0F;

		if (progress >= 1.0F) {
			RIPPLES.remove(key);
			return;
		}

		float radius = 4.0F + progress * Math.max(width, height) * 0.75F;
		float fade = (1.0F - progress) * (1.0F - progress);

		graphics.enableScissor(x, y, x + width, y + height);
		Shapes.circle(graphics, ripple.x(), ripple.y(), radius, Anim.alpha(0x66FFFFFF, fade));
		graphics.disableScissor();
	}

	public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
