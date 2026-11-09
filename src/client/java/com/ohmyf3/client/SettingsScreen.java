package com.ohmyf3.client;

import java.util.ArrayList;
import java.util.List;

import com.ohmyf3.client.chart.PieChartRenderer;
import com.ohmyf3.client.chart.PieLayout;
import com.ohmyf3.client.font.TtfTextRenderer;
import com.ohmyf3.client.ui.Anim;
import com.ohmyf3.client.ui.M3;
import com.ohmyf3.client.ui.Shapes;
import com.ohmyf3.config.OhMyF3Config;
import com.ohmyf3.font.EntryClassifier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.profiling.ResultField;

/**
 * Material 3 settings screen with a full-screen layout editor for the F3 overlay.
 *
 * <p>Normal mode is a list of M3 list items. Layout mode covers the screen with a <em>dummy F3</em>
 * built from sample data at the real size: the two columns, every individual row, and the profiler
 * chart can each be dragged. Positions are stored in permille of the screen, so the arrangement
 * survives resolution changes, and a single button restores the defaults.
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

	private static final String KEY_LEFT = "column_left";
	private static final String KEY_RIGHT = "column_right";

	/** Sample rows for the dummy overlay; the same classifier that handles real rows reads them. */
	private static final List<String> MOCK_LEFT = List.of(
			"XYZ: 128.305 / 70.000 / -256.912",
			"Block: 128 70 -257",
			"Chunk: 8 8 -17 in 0.0.0",
			"Facing: north (Towards negative Z)",
			"Biome: minecraft:plains",
			"Light: 15 (15 sky, 0 block)");

	private static final List<String> MOCK_RIGHT = List.of(
			"Minecraft 26.3 (Fabric)",
			"Mem: 42% 1024/2432 MiB",
			"CPU: AMD Ryzen 7 7735H",
			"Display: 2880x1800");

	private static final List<ResultField> MOCK_PIE = List.of(
			new ResultField("render", 44.0, 44.0, 1),
			new ResultField("tick", 27.0, 27.0, 1),
			new ResultField("chunk", 18.0, 18.0, 1),
			new ResultField("other", 11.0, 11.0, 1));

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

	/** Something the layout editor can drag: the column handle, one row, or the pie. */
	private record Target(String configKey, String label, int x, int y, int width, int height) {
	}

	private final List<Button> buttons = new ArrayList<>();

	/** Targets from the most recent layout-editor frame, reused for hit-testing. */
	private List<Target> lastTargets = List.of();

	private boolean layoutMode;

	private String dragging;
	private int dragOffsetPermilleX;
	private int dragOffsetPermilleY;
	private int dragStartX;
	private int dragStartY;

	public SettingsScreen() {
		super(Component.literal("OhMyF3 设置"));
		Anim.seed(OPEN_ANIM, 0.0F);
	}

	@Override
	protected void init() {
		buttons.clear();
	}

	// ---------------------------------------------------------------- panel geometry

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

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		TtfTextRenderer.ensureDeviceScale(Minecraft.getInstance().getWindow().getGuiScale());

		if (layoutMode) {
			renderLayoutEditor(graphics, mouseX, mouseY);
			return;
		}

		float open = Anim.value(OPEN_ANIM, 1.0F);
		M3.scrim(graphics, this.width, this.height, open);

		int lift = Math.round((1.0F - open) * 10.0F);
		graphics.pose().pushMatrix();
		graphics.pose().translate(0.0F, lift);

		M3.panel(graphics, panelX(), panelY(), PANEL_WIDTH, panelHeight());
		TtfTextRenderer.drawString(graphics, "OhMyF3 设置", contentX(), panelY() + PADDING + 12, M3.ON_SURFACE);
		TtfTextRenderer.drawString(graphics, "F3 调试界面的外观与行为", contentX(), panelY() + PADDING + 26,
				M3.ON_SURFACE_VARIANT_BRIGHT);

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

		renderFooter(graphics, mouseX, mouseY, footerY());
		graphics.pose().popMatrix();
	}

	private void renderFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
		int height = FOOTER_HEIGHT;
		int x = contentX();
		int doneWidth = 84;
		int modeWidth = 96;
		int resetWidth = 84;

		buttons.clear();
		buttons.add(new Button("done", "完成", x, y, doneWidth, height, true));
		buttons.add(new Button("mode", "调整 F3 布局", x + doneWidth + 8, y, modeWidth, height, false));
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

	// ---------------------------------------------------------------- layout editor

	private void renderLayoutEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.fill(0, 0, this.width, this.height, 0x8C000000);

		TtfTextRenderer.drawString(graphics, "拖动组件调整 F3 布局（位置按屏幕比例保存）", 12, 24, M3.ON_SURFACE);
		TtfTextRenderer.drawString(graphics, "拖标题条移动整列，拖某一行可单独摆放，拖饼图连背景一起移动", 12, 40,
				M3.ON_SURFACE_VARIANT_BRIGHT);

		List<Target> targets = targets(graphics);
		lastTargets = targets;

		for (Target target : targets) {
			boolean active = target.configKey().equals(dragging);

			if (active) {
				Shapes.dragOutline(graphics, target.x() - 3, target.y() - 3, target.width() + 6, target.height() + 6,
						0xA0D0BCFF);
			}
		}

		// Bottom action bar, floating over the dummy overlay.
		int barWidth = 80 * 3 + 16;
		int barX = this.width / 2 - barWidth / 2;
		int barY = this.height - 44;
		Shapes.filled(graphics, barX - 10, barY - 8, barWidth + 20, 48, M3.RADIUS_EXTRA_LARGE, M3.SURFACE);

		buttons.clear();
		buttons.add(new Button("done", "完成", barX, barY, 80, 32, true));
		buttons.add(new Button("mode", "结束调整", barX + 88, barY, 80, 32, false));
		buttons.add(new Button("reset", "恢复默认", barX + 176, barY, 80, 32, false));

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
	 * Draws the dummy overlay and returns everything that can be dragged.
	 *
	 * <p>This runs during extraction, so the preview and the hit boxes are always built from the same
	 * positions — a drag can never "miss" because the pointer was tested against last frame's layout.
	 */
	private List<Target> targets(GuiGraphicsExtractor graphics) {
		List<Target> targets = new ArrayList<>(16);
		OhMyF3Config config = OhMyF3Config.get();

		targets.addAll(column(graphics, KEY_LEFT, "左列", MOCK_LEFT, config, true));
		targets.addAll(column(graphics, KEY_RIGHT, "右列", MOCK_RIGHT, config, false));
		targets.add(pie(graphics, config));
		return targets;
	}

	private List<Target> column(GuiGraphicsExtractor graphics, String key, String label, List<String> lines,
			OhMyF3Config config, boolean left) {
		List<Target> targets = new ArrayList<>();
		List<String> visible = new ArrayList<>(lines);

		// Pinned rows are drawn on their own, exactly like the real overlay does.
		for (String line : lines) {
			int[] position = config.position(rowKey(line));

			if (position != null) {
				visible.remove(line);
			}
		}

		int width = Math.max(60, TtfTextRenderer.previewWidth(visible));
		int height = TtfTextRenderer.previewHeight(visible);
		int defaultX = left ? 2 : this.width - 2 - width;
		int[] stored = config.position(key);
		int x = stored != null ? Math.round(stored[0] * this.width / 1000.0F) : defaultX;
		int y = stored != null ? Math.round(stored[1] * this.height / 1000.0F) : 2;

		// Grab bar above the card: dragging it moves the whole column.
		Shapes.filled(graphics, x, y - 14, Math.min(width, 60), 12, 6, M3.SURFACE_CONTAINER_HIGHEST);
		TtfTextRenderer.drawString(graphics, label, x + 6, y - 4, M3.ON_SURFACE_VARIANT_BRIGHT);
		TtfTextRenderer.drawPreview(graphics, visible, x, y);

		targets.add(new Target(key, label, x, y - 14, Math.min(width, 60), 12));

		for (int index = 0; index < visible.size(); index++) {
			int rowY = y + 3 + index * TtfTextRenderer.lineHeight();
			targets.add(new Target(rowKey(visible.get(index)), "行", x, rowY, width, TtfTextRenderer.lineHeight()));
		}

		for (String line : lines) {
			int[] position = config.position(rowKey(line));

			if (position == null) {
				continue;
			}

			int pinnedX = Math.round(position[0] * this.width / 1000.0F);
			int pinnedY = Math.round(position[1] * this.height / 1000.0F);
			int pinnedWidth = Math.max(60, TtfTextRenderer.previewWidth(List.of(line)));
			TtfTextRenderer.drawPreview(graphics, List.of(line), pinnedX, pinnedY);
			targets.add(new Target(rowKey(line), "行", pinnedX, pinnedY, pinnedWidth,
					TtfTextRenderer.previewHeight(List.of(line))));
		}

		return targets;
	}

	private Target pie(GuiGraphicsExtractor graphics, OhMyF3Config config) {
		int[] centre = PieLayout.targetCentre(this.width, this.height);
		int size = 124;
		int left = centre[0] - size / 2;
		int top = centre[1] - size / 2;

		Shapes.filled(graphics, left - 8, top - 8, size + 16, size + 16, M3.RADIUS_LARGE,
				M3.SURFACE_CONTAINER);
		PieChartRenderer.render(graphics, MOCK_PIE, left, top, left + size, top + size);

		return new Target(PieLayout.KEY, "饼图", left - 8, top - 8, size + 16, size + 16);
	}

	private List<Target> hitTargets() {
		return lastTargets;
	}

	private static String rowKey(String line) {
		String key = EntryClassifier.classify(line).key();
		return "row:" + (key == null || key.isEmpty() ? line : key);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean quickMove) {
		double mouseX = event.x();
		double mouseY = event.y();

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
			List<Target> targets = this.hitTargets();

			// Later targets are drawn on top (pinned rows, then the pie), so search backwards.
			for (int i = targets.size() - 1; i >= 0; i--) {
				Target target = targets.get(i);

				if (!M3.inside(mouseX, mouseY, target.x(), target.y(), target.width(), target.height())) {
					continue;
				}

				dragging = target.configKey();
				dragStartX = (int) mouseX;
				dragStartY = (int) mouseY;

				int[] stored = OhMyF3Config.get().position(target.configKey());
				int currentPermilleX = stored != null ? stored[0] : Math.round(target.x() * 1000.0F / this.width);
				int currentPermilleY = stored != null ? stored[1] : Math.round(target.y() * 1000.0F / this.height);
				dragOffsetPermilleX = currentPermilleX - (int) Math.round(mouseX * 1000.0F / this.width);
				dragOffsetPermilleY = currentPermilleY - (int) Math.round(mouseY * 1000.0F / this.height);
				return true;
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

		int permilleX = Math.round((float) event.x() * 1000.0F / this.width) + dragOffsetPermilleX;
		int permilleY = Math.round((float) event.y() * 1000.0F / this.height) + dragOffsetPermilleY;

		OhMyF3Config config = OhMyF3Config.get();

		if (config.layout == null) {
			config.layout = new java.util.LinkedHashMap<>();
		}

		config.layout.put(dragging, new int[] {clamp(permilleX), clamp(permilleY)});
		return true;
	}

	private static int clamp(int permille) {
		return Math.max(-200, Math.min(1200, permille));
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
