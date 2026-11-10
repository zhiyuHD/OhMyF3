package com.ohmyf3.client;

import java.util.List;

import com.ohmyf3.client.font.TtfTextRenderer;
import com.ohmyf3.client.ui.Anim;
import com.ohmyf3.client.ui.M3;
import com.ohmyf3.client.ui.Shapes;
import com.ohmyf3.config.OhMyF3Config;
import com.ohmyf3.custom.Expression;
import com.ohmyf3.font.IconPainter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Editor for the user-defined F3 rows.
 *
 * <p>Each row shows its icon, a label and the expression that produces the value. Tapping the icon
 * cycles the available ones, tapping the label or the expression starts inline editing, and the
 * footer adds or removes entries. Values are evaluated live by {@link Expression}, so the preview
 * under each expression is what the overlay will print.
 */
public final class CustomFieldsScreen extends Screen {
	private static final int LEFT_BUTTON = 1;
	private static final int PANEL_WIDTH = 440;
	private static final int PADDING = 20;
	private static final int ROW_HEIGHT = 56;
	private static final int ROW_GAP = 6;
	private static final int FOOTER_HEIGHT = 32;
	private static final int ICON_SIZE = 30;

	private enum Part {
		LABEL, EXPRESSION
	}

	private record Button(String key, String label, int x, int y, int width, int height, boolean filled) {
	}

	private final Screen parent;
	private final java.util.List<Button> buttons = new java.util.ArrayList<>();

	private int scroll;
	private int editingIndex = -1;
	private Part editingPart;
	private String buffer = "";

	public CustomFieldsScreen(Screen parent) {
		super(Component.literal("自定义项"));
		this.parent = parent;
		Anim.seed("custom:open", 0.0F);
	}

	// ---------------------------------------------------------------- geometry

	private int panelHeight() {
		return Math.min(this.height - 32, 420);
	}

	private int panelX() {
		return this.width / 2 - PANEL_WIDTH / 2;
	}

	private int panelY() {
		return this.height / 2 - panelHeight() / 2;
	}

	private int listTop() {
		return panelY() + PADDING + 40;
	}

	private int listBottom() {
		return panelY() + panelHeight() - PADDING - FOOTER_HEIGHT - 8;
	}

	private int visibleRows() {
		return Math.max(1, (listBottom() - listTop()) / (ROW_HEIGHT + ROW_GAP));
	}

	private List<OhMyF3Config.CustomField> fields() {
		OhMyF3Config config = OhMyF3Config.get();

		if (config.customFields == null) {
			config.customFields = new java.util.ArrayList<>();
		}

		return config.customFields;
	}

	private int maxScroll() {
		return Math.max(0, fields().size() - visibleRows());
	}

	private int rowY(int index) {
		return listTop() + (index - scroll) * (ROW_HEIGHT + ROW_GAP);
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		TtfTextRenderer.ensureDeviceScale(Minecraft.getInstance().getWindow().getGuiScale());
		M3.scrim(graphics, this.width, this.height, 1.0F);
		M3.panel(graphics, panelX(), panelY(), PANEL_WIDTH, panelHeight());

		TtfTextRenderer.drawString(graphics, "自定义项", panelX() + PADDING, panelY() + PADDING + 10, M3.ON_SURFACE);
		TtfTextRenderer.drawString(graphics, "点标签或表达式编辑 · 点图标切换", panelX() + PADDING, panelY() + PADDING + 24,
				M3.ON_SURFACE_VARIANT_BRIGHT);

		renderList(graphics, mouseX, mouseY);
		renderFooter(graphics, mouseX, mouseY);
	}

	private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<OhMyF3Config.CustomField> fields = fields();

		graphics.enableScissor(panelX() + PADDING, listTop(), panelX() + PANEL_WIDTH - PADDING, listBottom());

		for (int i = 0; i < fields.size(); i++) {
			int y = rowY(i);

			if (y + ROW_HEIGHT < listTop() || y > listBottom()) {
				continue;
			}

			renderRow(graphics, fields.get(i), i, y, mouseX, mouseY);
		}

		graphics.disableScissor();

		if (fields.isEmpty()) {
			Shapes.filled(graphics, panelX() + PADDING, listTop(), PANEL_WIDTH - PADDING * 2, ROW_HEIGHT,
					M3.RADIUS_LARGE, M3.SURFACE_CONTAINER);
			TtfTextRenderer.drawString(graphics, "还没有自定义项，点下面的「添加一项」",
					panelX() + PADDING + 16, listTop() + 32, M3.ON_SURFACE_VARIANT_BRIGHT);
		}
	}

	private void renderRow(GuiGraphicsExtractor graphics, OhMyF3Config.CustomField field, int index, int y,
			int mouseX, int mouseY) {
		int x = panelX() + PADDING;
		int width = PANEL_WIDTH - PADDING * 2;
		boolean hovered = M3.inside(mouseX, mouseY, x, y, width, ROW_HEIGHT);
		Shapes.filled(graphics, x, y, width, ROW_HEIGHT, M3.RADIUS_LARGE,
				hovered ? M3.SURFACE_CONTAINER_HIGHEST : M3.SURFACE_CONTAINER_HIGH);

		// Icon chip; tapping cycles through the available icons.
		int iconX = x + 12;
		int iconY = y + (ROW_HEIGHT - ICON_SIZE) / 2;
		boolean iconHovered = M3.inside(mouseX, mouseY, iconX, iconY, ICON_SIZE, ICON_SIZE);
		Shapes.filled(graphics, iconX, iconY, ICON_SIZE, ICON_SIZE, 10,
				iconHovered ? M3.PRIMARY_CONTAINER : M3.SURFACE_CONTAINER_HIGH);
		TtfTextRenderer.drawIcon(graphics, iconOf(field), iconX + (ICON_SIZE - 10) / 2.0F,
				iconY + (ICON_SIZE - 10) / 2.0F);

		String label = field.label == null ? "" : field.label;
		String expression = field.expression == null ? "" : field.expression;

		boolean editingLabel = editingIndex == index && editingPart == Part.LABEL;
		boolean editingExpression = editingIndex == index && editingPart == Part.EXPRESSION;

		TtfTextRenderer.drawString(graphics, editingLabel ? buffer : label, x + 54, y + 22,
				editingLabel ? M3.PRIMARY : M3.ON_SURFACE);

		String preview = Expression.evaluate(expression);
		TtfTextRenderer.drawString(graphics, editingExpression ? buffer : expression, x + 54, y + 38,
				editingExpression ? M3.PRIMARY : M3.ON_SURFACE_VARIANT_BRIGHT);

		TtfTextRenderer.drawString(graphics, "= " + preview, x + 54, y + 50, M3.OUTLINE);

		// Delete button.
		int deleteX = x + width - 34;
		int deleteY = y + (ROW_HEIGHT - 20) / 2;
		boolean deleteHovered = M3.inside(mouseX, mouseY, deleteX, deleteY, 20, 20);
		Shapes.filled(graphics, deleteX, deleteY, 20, 20, 10,
				deleteHovered ? M3.SURFACE_CONTAINER_HIGHEST : M3.SURFACE_CONTAINER_HIGH);
		TtfTextRenderer.drawString(graphics, "×", deleteX + 8, deleteY + 15,
				deleteHovered ? M3.OUTLINE : M3.ON_SURFACE_VARIANT_BRIGHT);
	}

	private void renderFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = panelX() + PADDING;
		int y = panelY() + panelHeight() - PADDING - FOOTER_HEIGHT;

		buttons.clear();
		buttons.add(new Button("done", "完成", x, y, 84, FOOTER_HEIGHT, true));
		buttons.add(new Button("add", "添加一项", x + 92, y, 100, FOOTER_HEIGHT, false));

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

	private static IconPainter.Icon iconOf(OhMyF3Config.CustomField field) {
		try {
			return IconPainter.Icon.valueOf(field.icon);
		} catch (RuntimeException ignored) {
			return IconPainter.Icon.CLOCK;
		}
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

		List<OhMyF3Config.CustomField> fields = fields();
		int x = panelX() + PADDING;
		int width = PANEL_WIDTH - PADDING * 2;

		for (int i = 0; i < fields.size(); i++) {
			int y = rowY(i);

			if (y + ROW_HEIGHT < listTop() || y > listBottom()) {
				continue;
			}

			OhMyF3Config.CustomField field = fields.get(i);

			if (M3.inside(mouseX, mouseY, x + 12, y + (ROW_HEIGHT - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE)) {
				cycleIcon(field);
				return true;
			}

			if (M3.inside(mouseX, mouseY, x + width - 34, y + (ROW_HEIGHT - 20) / 2, 20, 20)) {
				fields.remove(i);
				OhMyF3Config.get().save();
				return true;
			}

			if (M3.inside(mouseX, mouseY, x + 54, y + 8, width - 100, 18)) {
				startEditing(i, Part.LABEL, field.label);
				return true;
			}

			if (M3.inside(mouseX, mouseY, x + 54, y + 24, width - 100, 18)) {
				startEditing(i, Part.EXPRESSION, field.expression);
				return true;
			}
		}

		commitEditing();
		return super.mouseClicked(event, quickMove);
	}

	private void startEditing(int index, Part part, String current) {
		commitEditing();
		editingIndex = index;
		editingPart = part;
		buffer = current == null ? "" : current;
	}

	private void commitEditing() {
		if (editingIndex < 0) {
			return;
		}

		List<OhMyF3Config.CustomField> fields = fields();

		if (editingIndex < fields.size()) {
			OhMyF3Config.CustomField field = fields.get(editingIndex);

			if (editingPart == Part.LABEL) {
				field.label = buffer;
			} else {
				field.expression = buffer;
			}

			OhMyF3Config.get().save();
		}

		editingIndex = -1;
		editingPart = null;
	}

	private void cycleIcon(OhMyF3Config.CustomField field) {
		IconPainter.Icon[] icons = IconPainter.Icon.values();
		IconPainter.Icon current = iconOf(field);
		field.icon = icons[(current.ordinal() + 1) % icons.length].name();
		OhMyF3Config.get().save();
	}

	private void onButton(String key) {
		switch (key) {
			case "done" -> close();
			case "add" -> {
				commitEditing();
				fields().add(new OhMyF3Config.CustomField("时间", "CLOCK", "time()"));
				OhMyF3Config.get().save();
			}
			default -> {
			}
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (editingIndex < 0) {
			return super.charTyped(event);
		}

		String typed = event.codepointAsString();

		if (typed.isEmpty()) {
			return false;
		}

		buffer += typed;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.input();

		if (key == 256) {
			if (editingIndex >= 0) {
				editingIndex = -1;
				editingPart = null;
				return true;
			}

			close();
			return true;
		}

		if (editingIndex >= 0) {
			if (key == 259) {
				if (!buffer.isEmpty()) {
					buffer = buffer.substring(0, buffer.offsetByCodePoints(buffer.length(), -1));
				}

				return true;
			}

			if (key == 257 || key == 335) {
				commitEditing();
				return true;
			}

			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseY >= listTop() && mouseY <= listBottom()) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(vertical)));
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	private void close() {
		commitEditing();
		Minecraft.getInstance().setScreenAndShow(parent);
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
