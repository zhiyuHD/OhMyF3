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
	public static void scrim(GuiGraphicsExtractor graphics, int width, int height) {
		graphics.fill(0, 0, width, height, 0x99000000);
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
		Shapes.filled(graphics, x, y, width, height, RADIUS_LARGE,
				hovered ? SURFACE_CONTAINER_HIGHEST : SURFACE_CONTAINER_HIGH);

		TtfTextRenderer.drawString(graphics, label, x + 16, y + 17, ON_SURFACE);
		TtfTextRenderer.drawString(graphics, detail, x + 16, y + 30,
				hovered ? ON_SURFACE_VARIANT : OUTLINE_VARIANT + 0x20202020);

		drawSwitch(graphics, x + width - 16 - 34, y + (height - 20) / 2, value);
	}

	/** M3 switch: pill track, circular thumb, tonal colours. */
	public static void drawSwitch(GuiGraphicsExtractor graphics, int x, int y, boolean on) {
		int trackWidth = 34;
		int trackHeight = 20;
		int track = on ? PRIMARY : SURFACE_CONTAINER_HIGHEST;
		int trackOutline = on ? PRIMARY : OUTLINE;

		Shapes.outlined(graphics, x, y, trackWidth, trackHeight, trackHeight / 2, track, trackOutline);

		float thumbRadius = 7.0F;
		float thumbCenterX = on ? x + trackWidth - 1 - thumbRadius : x + 1 + thumbRadius;
		Shapes.circle(graphics, thumbCenterX, y + trackHeight / 2.0F, thumbRadius, on ? ON_PRIMARY : OUTLINE);
	}

	/** Filled tonal button; returns nothing, the caller owns hit-testing. */
	public static void filledButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String label,
			boolean hovered) {
		Shapes.filled(graphics, x, y, width, height, height / 2, hovered ? PRIMARY_CONTAINER : PRIMARY);
		int textWidth = TtfTextRenderer.stringWidth(label);
		TtfTextRenderer.drawString(graphics, label, x + (width - textWidth) / 2.0F,
				Math.round(y + height / 2.0F + TtfTextRenderer.lineHeight() / 2.0F - 2),
				hovered ? ON_PRIMARY_CONTAINER : ON_PRIMARY);
	}

	/** Text button (no fill), used for the secondary actions. */
	public static void textButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String label,
			boolean hovered) {
		if (hovered) {
			Shapes.filled(graphics, x, y, width, height, height / 2, SURFACE_CONTAINER_HIGHEST);
		}

		int textWidth = TtfTextRenderer.stringWidth(label);
		TtfTextRenderer.drawString(graphics, label, x + (width - textWidth) / 2.0F,
				Math.round(y + height / 2.0F + TtfTextRenderer.lineHeight() / 2.0F - 2),
				hovered ? ON_SURFACE : ON_SURFACE_VARIANT);
	}

	/** Filled tonal "chip" for the layout-edit mode banner. */
	public static void chip(GuiGraphicsExtractor graphics, int x, int y, String label, int background, int foreground) {
		int width = TtfTextRenderer.stringWidth(label) + 20;
		Shapes.filled(graphics, x, y, width, 20, 10, background);
		TtfTextRenderer.drawString(graphics, label, x + 10, y + 14, foreground);
	}

	public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
