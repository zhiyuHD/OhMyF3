package com.ohmyf3.client;

import java.util.ArrayList;
import java.util.List;

import com.ohmyf3.OhMyF3;
import com.ohmyf3.client.font.TtfTextRenderer;
import com.ohmyf3.client.ui.M3;
import com.ohmyf3.client.ui.Shapes;
import com.ohmyf3.config.OhMyF3Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Material 3 settings screen.
 *
 * <p>Four layout <em>parts</em> (header, appearance, effects, footer) are stacked by default and
 * can each be dragged elsewhere in "adjust layout" mode; every part's offset is stored in
 * {@link OhMyF3Config#layout} and a single button restores the defaults.
 *
 * <p>All painting is custom (no vanilla widgets) so the screen matches the F3 cards: TrueType
 * text, rounded tonal surfaces, M3 switches.
 */
public final class SettingsScreen extends Screen {
	private static final int PANEL_WIDTH = 372;
	private static final int PADDING = 24;
	private static final int PART_GAP = 12;
	private static final int HEADER_HEIGHT = 34;
	private static final int SECTION_HEIGHT = 12 + M3.ROW_HEIGHT * 2 + M3.ROW_GAP;
	private static final int FOOTER_HEIGHT = 32;

	/** Left mouse button in the {@code InputConstants} numbering used by {@code MouseButtonEvent}. */
	private static final int LEFT_BUTTON = 1;

	private static final String PART_HEADER = "header";
	private static final String PART_APPEARANCE = "appearance";
	private static final String PART_EFFECTS = "effects";
	private static final String PART_FOOTER = "footer";

	/** Button hit boxes, recomputed every frame. */
	private final List<Button> buttons = new ArrayList<>();

	private boolean editingLayout;

	private String dragging;

	private int dragGrabX;
	private int dragGrabY;

	/** Rows in draw order: [part, label, detail, getter, setter]. */
	private record Toggle(String part, int index, String label, String detail,
			java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
	}

	private static final List<Toggle> TOGGLES = List.of(
			new Toggle(PART_APPEARANCE, 0, "中文标签", "把字段名换成中文",
					() -> OhMyF3Config.get().chineseLabels, v -> OhMyF3Config.get().chineseLabels = v),
			new Toggle(PART_APPEARANCE, 1, "关键词图标", "在字段前显示图标",
					() -> OhMyF3Config.get().icons, v -> OhMyF3Config.get().icons = v),
			new Toggle(PART_EFFECTS, 0, "背景模糊", "卡片背后模糊世界",
					() -> OhMyF3Config.get().blur, v -> OhMyF3Config.get().blur = v),
			new Toggle(PART_EFFECTS, 1, "滑入动画", "两列滑入滑出",
					() -> OhMyF3Config.get().slideAnimation, v -> OhMyF3Config.get().slideAnimation = v));

	/** One draggable block on the panel. */
	private record Part(String key, int x, int y, int width, int height) {
	}

	private record Button(String key, String label, int x, int y, int width, int height, boolean filled) {
	}

	public SettingsScreen() {
		super(Component.literal("OhMyF3 设置"));
		OhMyF3.LOGGER.info("settings screen opened");
	}

	@Override
	protected void init() {
		buttons.clear();
	}

	// ---------------------------------------------------------------- layout

	private int panelX() {
		return this.width / 2 - PANEL_WIDTH / 2;
	}

	private int panelY() {
		int panelHeight = PADDING * 2 + HEADER_HEIGHT + PART_GAP + SECTION_HEIGHT * 2 + PART_GAP * 2 + FOOTER_HEIGHT;
		return Math.max(8, this.height / 2 - panelHeight / 2);
	}

	private int panelHeight() {
		return PADDING * 2 + HEADER_HEIGHT + PART_GAP + SECTION_HEIGHT * 2 + PART_GAP * 2 + FOOTER_HEIGHT;
	}

	/** Default stack positions, each shifted by the stored offset. */
	private List<Part> parts() {
		OhMyF3Config config = OhMyF3Config.get();
		int x = panelX() + PADDING;
		int width = PANEL_WIDTH - PADDING * 2;
		int y = panelY() + PADDING;

		List<Part> parts = new ArrayList<>(4);
		parts.add(shifted(config, new Part(PART_HEADER, x, y, width, HEADER_HEIGHT)));
		y += HEADER_HEIGHT + PART_GAP;
		parts.add(shifted(config, new Part(PART_APPEARANCE, x, y, width, SECTION_HEIGHT)));
		y += SECTION_HEIGHT + PART_GAP;
		parts.add(shifted(config, new Part(PART_EFFECTS, x, y, width, SECTION_HEIGHT)));
		y += SECTION_HEIGHT + PART_GAP;
		parts.add(shifted(config, new Part(PART_FOOTER, x, y, width, FOOTER_HEIGHT)));
		return parts;
	}

	private Part shifted(OhMyF3Config config, Part part) {
		int[] offset = config.offset(part.key());
		return new Part(part.key(), part.x() + offset[0], part.y() + offset[1], part.width(), part.height());
	}

	private Part part(List<Part> parts, String key) {
		for (Part part : parts) {
			if (part.key().equals(key)) {
				return part;
			}
		}

		return new Part(key, 0, 0, 0, 0);
	}

	/** Rect of one settings row inside its part (the section label occupies the first 12px). */
	private int[] rowRect(Part part, int index) {
		return new int[] {part.x(), part.y() + 12 + index * (M3.ROW_HEIGHT + M3.ROW_GAP), part.width(), M3.ROW_HEIGHT};
	}

	// ---------------------------------------------------------------- drawing

	private static long lastDiag;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		int guiScale = Minecraft.getInstance().getWindow().getGuiScale();
		TtfTextRenderer.ensureDeviceScale(guiScale);
		M3.scrim(graphics, this.width, this.height);
		M3.panel(graphics, panelX(), panelY(), PANEL_WIDTH, panelHeight());

		List<Part> parts = parts();

		renderHeader(graphics, part(parts, PART_HEADER));
		renderSection(graphics, parts, PART_APPEARANCE, "外观", mouseX, mouseY);
		renderSection(graphics, parts, PART_EFFECTS, "效果", mouseX, mouseY);
		renderFooter(graphics, part(parts, PART_FOOTER), mouseX, mouseY);

		if (editingLayout) {
			for (Part part : parts) {
				Shapes.dragOutline(graphics, part.x() - 3, part.y() - 3, part.width() + 6, part.height() + 6,
						0x80D0BCFF);
			}

			M3.chip(graphics, panelX() + PANEL_WIDTH - 108, panelY() - 26, "拖动分区调整位置",
					M3.PRIMARY_CONTAINER, M3.ON_PRIMARY_CONTAINER);
		}
	}

	private void renderHeader(GuiGraphicsExtractor graphics, Part part) {
		TtfTextRenderer.drawString(graphics, "OhMyF3 设置", part.x(), part.y() + 12, M3.ON_SURFACE);
		TtfTextRenderer.drawString(graphics, "F3 调试界面的外观与行为", part.x(), part.y() + 26,
				M3.ON_SURFACE_VARIANT);
	}

	private void renderSection(GuiGraphicsExtractor graphics, List<Part> parts, String key, String label,
			int mouseX, int mouseY) {
		Part part = part(parts, key);
		M3.section(graphics, label, part.x(), part.y() + 9);

		for (Toggle toggle : TOGGLES) {
			if (!toggle.part().equals(key)) {
				continue;
			}

			int[] rect = rowRect(part, toggle.index());
			boolean hovered = M3.inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]) && !editingLayout;
			M3.switchRow(graphics, rect[0], rect[1], rect[2], toggle.label(), toggle.detail(),
					toggle.getter().getAsBoolean(), hovered);
		}
	}

	private void renderFooter(GuiGraphicsExtractor graphics, Part part, int mouseX, int mouseY) {
		int x = part.x();
		int y = part.y();
		int height = part.height();

		int doneWidth = 84;
		int layoutWidth = 96;
		int resetWidth = 84;

		buttons.clear();
		buttons.add(new Button("done", "完成", x, y, doneWidth, height, true));
		buttons.add(new Button("layout", editingLayout ? "结束调整" : "调整布局", x + doneWidth + 8, y, layoutWidth,
				height, false));
		buttons.add(new Button("reset", "恢复默认", x + doneWidth + layoutWidth + 16, y, resetWidth, height, false));

		for (Button button : buttons) {
			boolean hovered = M3.inside(mouseX, mouseY, button.x(), button.y(), button.width(), button.height());

			if (button.filled()) {
				M3.filledButton(graphics, button.x(), button.y(), button.width(), button.height(), button.label(),
						hovered);
			} else {
				M3.textButton(graphics, button.x(), button.y(), button.width(), button.height(), button.label(),
						hovered);
			}
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean quickMove) {
		double mouseX = event.x();
		double mouseY = event.y();

		// MouseButtonEvent reports InputConstants codes, where the left button is 1 (GLFW's 0 is
		// remapped by the input layer), so comparing against 0 would treat every left click as a
		// right click.
		if (event.button() != LEFT_BUTTON) {
			return super.mouseClicked(event, quickMove);
		}

		List<Part> parts = parts();

		if (editingLayout) {
			for (Part part : parts) {
				if (M3.inside(mouseX, mouseY, part.x(), part.y(), part.width(), part.height())) {
					dragging = part.key();
					int[] offset = OhMyF3Config.get().offset(part.key());
					dragGrabX = (int) mouseX - offset[0];
					dragGrabY = (int) mouseY - offset[1];
					return true;
				}
			}

			return true;
		}

		for (Button button : buttons) {
			if (M3.inside(mouseX, mouseY, button.x(), button.y(), button.width(), button.height())) {
				onButton(button.key());
				return true;
			}
		}

		for (Toggle toggle : TOGGLES) {
			Part part = part(parts, toggle.part());
			int[] rect = rowRect(part, toggle.index());

			if (M3.inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3])) {
				toggle.setter().accept(!toggle.getter().getAsBoolean());
				OhMyF3Config.get().save();
				return true;
			}
		}

		return super.mouseClicked(event, quickMove);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging == null) {
			return super.mouseDragged(event, dragX, dragY);
		}

		int[] offset = new int[] {(int) event.x() - dragGrabX, (int) event.y() - dragGrabY};

		// Keep the part inside the panel so it cannot be dragged out of reach.
		int minX = -PANEL_WIDTH / 2;
		int minY = -panelHeight() / 2;
		int maxX = PANEL_WIDTH / 2;
		int maxY = panelHeight() / 2;
		offset[0] = Math.max(minX, Math.min(maxX, offset[0]));
		offset[1] = Math.max(minY, Math.min(maxY, offset[1]));

		OhMyF3Config config = OhMyF3Config.get();

		if (config.layout == null) {
			config.layout = new java.util.LinkedHashMap<>();
		}

		config.layout.put(dragging, offset);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			OhMyF3Config.get().save();
			return true;
		}

		return super.mouseReleased(event);
	}

	private void onButton(String key) {
		switch (key) {
			case "done" -> close();
			case "layout" -> editingLayout = !editingLayout;
			case "reset" -> {
				OhMyF3Config.get().resetLayout();
				editingLayout = false;
			}
			default -> {
			}
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Escape leaves layout editing first, so a stray drag mode never traps the player.
		if (event.input() == 256 && editingLayout) {
			editingLayout = false;
			return true;
		}

		return super.keyPressed(event);
	}

	private void close() {
		Minecraft.getInstance().setScreenAndShow(null);
	}

	@Override
	public void onClose() {
		close();
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
