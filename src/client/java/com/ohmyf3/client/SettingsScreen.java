package com.ohmyf3.client;

import java.util.ArrayList;
import java.util.List;

import com.ohmyf3.client.font.TtfTextRenderer;
import com.ohmyf3.client.ui.Anim;
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
 * <p>Two modes. Normally it is a list of M3 list items with switches. In <em>layout mode</em> the
 * list is replaced by a miniature of the F3 screen where the three things the player can move — the
 * left column, the right column and the profiler pie — are dragged into place. The offsets are
 * stored in {@link OhMyF3Config#layout} and applied by the F3 renderer itself, so the editor changes
 * the real overlay rather than this screen's own arrangement.
 */
public final class SettingsScreen extends Screen {
	/** Left mouse button in the {@code InputConstants} numbering used by {@code MouseButtonEvent}. */
	private static final int LEFT_BUTTON = 1;

	private static final int PANEL_WIDTH = 372;
	private static final int PADDING = 24;
	private static final int GAP = 12;
	private static final int HEADER_HEIGHT = 34;
	private static final int SECTION_HEIGHT = 12 + M3.ROW_HEIGHT * 2 + M3.ROW_GAP;
	private static final int FOOTER_HEIGHT = 32;

	private static final String OPEN_ANIM = "screen:open";

	/** Config keys of the movable F3 parts. */
	private static final String PART_LEFT = "f3_left";
	private static final String PART_RIGHT = "f3_right";
	private static final String PART_PIE = "f3_pie";

	private record Toggle(String label, String detail, java.util.function.BooleanSupplier getter,
			java.util.function.Consumer<Boolean> setter) {
	}

	private static final List<Toggle> TOGGLES = List.of(
			new Toggle("中文标签", "把字段名换成中文",
					() -> OhMyF3Config.get().chineseLabels, v -> OhMyF3Config.get().chineseLabels = v),
			new Toggle("关键词图标", "在字段前显示图标",
					() -> OhMyF3Config.get().icons, v -> OhMyF3Config.get().icons = v),
			new Toggle("背景模糊", "卡片背后模糊世界",
					() -> OhMyF3Config.get().blur, v -> OhMyF3Config.get().blur = v),
			new Toggle("滑入动画", "两列滑入滑出",
					() -> OhMyF3Config.get().slideAnimation, v -> OhMyF3Config.get().slideAnimation = v));

	private record Button(String key, String label, int x, int y, int width, int height, boolean filled) {
	}

	/** A draggable stand-in for one F3 element inside the miniature. */
	private record Handle(String key, String label, int x, int y, int width, int height) {
	}

	private final List<Button> buttons = new ArrayList<>();

	private boolean layoutMode;

	private String dragging;
	private int dragStartX;
	private int dragStartY;
	private int dragOriginX;
	private int dragOriginY;

	public SettingsScreen() {
		super(Component.literal("OhMyF3 设置"));
		Anim.seed(OPEN_ANIM, 0.0F);
	}

	@Override
	protected void init() {
		buttons.clear();
	}

	// ---------------------------------------------------------------- geometry

	private int panelX() {
		return this.width / 2 - PANEL_WIDTH / 2;
	}

	private int panelHeight() {
		return PADDING * 2 + HEADER_HEIGHT + GAP + SECTION_HEIGHT * 2 + GAP + FOOTER_HEIGHT;
	}

	private int panelY() {
		return Math.max(8, this.height / 2 - panelHeight() / 2);
	}

	private int contentX() {
		return panelX() + PADDING;
	}

	private int contentWidth() {
		return PANEL_WIDTH - PADDING * 2;
	}

	private int appearanceY() {
		return panelY() + PADDING + HEADER_HEIGHT + GAP;
	}

	private int effectsY() {
		return appearanceY() + SECTION_HEIGHT + GAP;
	}

	private int footerY() {
		return effectsY() + SECTION_HEIGHT + GAP;
	}

	private int[] rowRect(int sectionY, int index) {
		return new int[] {contentX(), sectionY + 12 + index * (M3.ROW_HEIGHT + M3.ROW_GAP), contentWidth(),
				M3.ROW_HEIGHT};
	}

	/** The miniature canvas replaces the two sections while the layout is being edited. */
	private int canvasX() {
		return contentX();
	}

	private int canvasY() {
		return appearanceY();
	}

	private int canvasWidth() {
		return contentWidth();
	}

	private int canvasHeight() {
		return SECTION_HEIGHT * 2 + GAP;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		TtfTextRenderer.ensureDeviceScale(Minecraft.getInstance().getWindow().getGuiScale());

		float open = Anim.value(OPEN_ANIM, 1.0F);
		M3.scrim(graphics, this.width, this.height, open);

		int lift = Math.round((1.0F - open) * 10.0F);
		graphics.pose().pushMatrix();
		graphics.pose().translate(0.0F, lift);

		M3.panel(graphics, panelX(), panelY(), PANEL_WIDTH, panelHeight());
		TtfTextRenderer.drawString(graphics, "OhMyF3 设置", contentX(), panelY() + PADDING + 12, M3.ON_SURFACE);
		TtfTextRenderer.drawString(graphics, layoutMode ? "拖动色块调整 F3 各元素的位置" : "F3 调试界面的外观与行为",
				contentX(), panelY() + PADDING + 26, M3.ON_SURFACE_VARIANT_BRIGHT);

		if (layoutMode) {
			renderLayoutEditor(graphics, mouseX, mouseY);
		} else {
			renderSections(graphics, mouseX, mouseY);
		}

		renderFooter(graphics, mouseX, mouseY);
		graphics.pose().popMatrix();
	}

	private void renderSections(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		M3.section(graphics, "外观", contentX(), appearanceY() + 9);
		M3.section(graphics, "效果", contentX(), effectsY() + 9);

		for (int i = 0; i < TOGGLES.size(); i++) {
			Toggle toggle = TOGGLES.get(i);
			int[] rect = rowRect(i < 2 ? appearanceY() : effectsY(), i < 2 ? i : i - 2);
			boolean hovered = M3.inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]);
			M3.switchRow(graphics, rect[0], rect[1], rect[2], toggle.label(), toggle.detail(),
					toggle.getter().getAsBoolean(), hovered);
			M3.drawRipple(graphics, "row:" + toggle.label(), rect[0], rect[1], rect[2], rect[3]);
		}
	}

	private void renderLayoutEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Shapes.outlined(graphics, canvasX(), canvasY(), canvasWidth(), canvasHeight(), M3.RADIUS_LARGE,
				M3.SURFACE_CONTAINER, M3.OUTLINE_VARIANT);

		for (Handle handle : handles()) {
			boolean hovered = M3.inside(mouseX, mouseY, handle.x(), handle.y(), handle.width(), handle.height());
			int fill = hovered || handle.key().equals(dragging) ? M3.PRIMARY_CONTAINER : M3.SURFACE_CONTAINER_HIGHEST;
			Shapes.filled(graphics, handle.x(), handle.y(), handle.width(), handle.height(), M3.RADIUS_SMALL, fill);

			int labelWidth = TtfTextRenderer.stringWidth(handle.label());
			TtfTextRenderer.drawString(graphics, handle.label(),
					handle.x() + (handle.width() - labelWidth) / 2.0F,
					handle.y() + handle.height() / 2 + TtfTextRenderer.lineHeight() / 2 - 2,
					hovered ? M3.ON_PRIMARY_CONTAINER : M3.ON_SURFACE_VARIANT_BRIGHT);
		}
	}

	private void renderFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = footerY();
		int height = FOOTER_HEIGHT;
		int x = contentX();

		int doneWidth = 84;
		int modeWidth = 96;
		int resetWidth = 84;

		buttons.clear();
		buttons.add(new Button("done", "完成", x, y, doneWidth, height, true));
		buttons.add(new Button("mode", layoutMode ? "结束调整" : "调整 F3 布局", x + doneWidth + 8, y, modeWidth, height,
				false));
		buttons.add(new Button("reset", "恢复默认", x + doneWidth + modeWidth + 16, y, resetWidth, height, false));

		for (Button button : buttons) {
			boolean hovered = M3.inside(mouseX, mouseY, button.x(), button.y(), button.width(), button.height());

			if (button.filled()) {
				M3.filledButton(graphics, button.x(), button.y(), button.width(), button.height(), button.label(),
						hovered);
			} else {
				M3.textButton(graphics, button.x(), button.y(), button.width(), button.height(), button.label(),
						hovered);
			}

			M3.drawRipple(graphics, "btn:" + button.key(), button.x(), button.y(), button.width(), button.height());
		}
	}

	/**
	 * The three movable F3 elements, laid out inside the miniature.
	 *
	 * <p>Preview coordinates are the canonical positions plus the stored offset scaled down, so
	 * dragging a block moves the real overlay by the same relative amount.
	 */
	private List<Handle> handles() {
		OhMyF3Config config = OhMyF3Config.get();
		int x = canvasX();
		int y = canvasY();
		int width = canvasWidth();
		int height = canvasHeight();

		float scaleX = width / (float) Math.max(1, this.width);
		float scaleY = height / (float) Math.max(1, this.height);

		int columnWidth = Math.round(width * 0.28F);
		int columnHeight = Math.round(height * 0.66F);
		int pieSize = Math.round(height * 0.34F);

		int[] left = config.offset(PART_LEFT);
		int[] right = config.offset(PART_RIGHT);
		int[] pie = config.offset(PART_PIE);

		List<Handle> handles = new ArrayList<>(3);
		handles.add(new Handle(PART_LEFT, "左列",
				x + 4 + Math.round(left[0] * scaleX), y + 4 + Math.round(left[1] * scaleY), columnWidth, columnHeight));
		handles.add(new Handle(PART_RIGHT, "右列",
				x + width - 4 - columnWidth + Math.round(right[0] * scaleX), y + 4 + Math.round(right[1] * scaleY),
				columnWidth, columnHeight));
		handles.add(new Handle(PART_PIE, "饼图",
				x + Math.round(width * 0.55F) + Math.round(pie[0] * scaleX),
				y + Math.round(height * 0.45F) + Math.round(pie[1] * scaleY), pieSize, pieSize));
		return handles;
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean quickMove) {
		double mouseX = event.x();
		double mouseY = event.y();

		// MouseButtonEvent reports InputConstants codes, where the left button is 1.
		if (event.button() != LEFT_BUTTON) {
			return super.mouseClicked(event, quickMove);
		}

		for (Button button : buttons) {
			if (M3.inside(mouseX, mouseY, button.x(), button.y(), button.width(), button.height())) {
				M3.ripple("btn:" + button.key(), mouseX, mouseY);
				onButton(button.key());
				return true;
			}
		}

		if (layoutMode) {
			for (Handle handle : handles()) {
				if (M3.inside(mouseX, mouseY, handle.x(), handle.y(), handle.width(), handle.height())) {
					dragging = handle.key();
					dragStartX = (int) mouseX;
					dragStartY = (int) mouseY;
					int[] offset = OhMyF3Config.get().offset(handle.key());
					dragOriginX = offset[0];
					dragOriginY = offset[1];
					return true;
				}
			}

			return true;
		}

		for (int i = 0; i < TOGGLES.size(); i++) {
			Toggle toggle = TOGGLES.get(i);
			int[] rect = rowRect(i < 2 ? appearanceY() : effectsY(), i < 2 ? i : i - 2);

			if (M3.inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3])) {
				M3.ripple("row:" + toggle.label(), mouseX, mouseY);
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

		float scaleX = canvasWidth() / (float) Math.max(1, this.width);
		float scaleY = canvasHeight() / (float) Math.max(1, this.height);

		int offsetX = dragOriginX + Math.round(((float) event.x() - dragStartX) / scaleX);
		int offsetY = dragOriginY + Math.round(((float) event.y() - dragStartY) / scaleY);

		// Keep the offsets sane: a part can be nudged, not exiled off the screen.
		int limitX = this.width / 2;
		int limitY = this.height / 2;
		offsetX = Math.max(-limitX, Math.min(limitX, offsetX));
		offsetY = Math.max(-limitY, Math.min(limitY, offsetY));

		OhMyF3Config config = OhMyF3Config.get();

		if (config.layout == null) {
			config.layout = new java.util.LinkedHashMap<>();
		}

		config.layout.put(dragging, new int[] {offsetX, offsetY});
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
			case "mode" -> layoutMode = !layoutMode;
			case "reset" -> OhMyF3Config.get().resetLayout();
			default -> {
			}
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.input() == 256 && layoutMode) {
			layoutMode = false;
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
