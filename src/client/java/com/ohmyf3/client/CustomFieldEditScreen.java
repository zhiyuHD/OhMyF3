package com.ohmyf3.client;

import java.util.List;

import com.ohmyf3.client.font.TtfTextRenderer;
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
 * Dedicated editor for one custom F3 row.
 *
 * <p>Opening an item goes straight here instead of editing it inline in the list: the label, the
 * expression, the icon and a live preview all need room, and the preview keeps the field honest
 * while the expression is being typed.
 */
public final class CustomFieldEditScreen extends Screen {
	private static final int LEFT_BUTTON = 1;
	private static final int PANEL_WIDTH = 440;
	private static final int PADDING = 20;
	private static final int FIELD_HEIGHT = 30;
	private static final int ICON_CELL = 34;
	private static final int ICON_GAP = 8;
	private static final int FOOTER_HEIGHT = 32;

	private enum Editing {
		NONE, LABEL, EXPRESSION
	}

	private record Button(String key, String label, int x, int y, int width, int height, boolean filled) {
	}

	private final Screen parent;
	private final OhMyF3Config.CustomField field;
	private final List<Button> buttons = new java.util.ArrayList<>();

	private Editing editing = Editing.NONE;
	private String buffer = "";

	public CustomFieldEditScreen(Screen parent, OhMyF3Config.CustomField field) {
		super(Component.literal("编辑自定义项"));
		this.parent = parent;
		this.field = field;
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

	private int fieldX() {
		return panelX() + PADDING;
	}

	private int labelY() {
		return panelY() + PADDING + 46;
	}

	private int expressionY() {
		return labelY() + FIELD_HEIGHT + 14;
	}

	private int previewY() {
		return expressionY() + FIELD_HEIGHT + 22;
	}

	private int iconGridY() {
		return previewY() + 22;
	}

	private int fieldWidth() {
		return PANEL_WIDTH - PADDING * 2;
	}

	private int iconsPerRow() {
		return Math.max(1, fieldWidth() / (ICON_CELL + ICON_GAP));
	}

	private int iconCellX(int index) {
		return fieldX() + (index % iconsPerRow()) * (ICON_CELL + ICON_GAP);
	}

	private int iconCellY(int index) {
		return iconGridY() + (index / iconsPerRow()) * (ICON_CELL + ICON_GAP);
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		TtfTextRenderer.ensureDeviceScale(Minecraft.getInstance().getWindow().getGuiScale());
		M3.scrim(graphics, this.width, this.height, 1.0F);
		M3.panel(graphics, panelX(), panelY(), PANEL_WIDTH, panelHeight());

		TtfTextRenderer.drawString(graphics, "编辑自定义项", panelX() + PADDING, panelY() + PADDING + 10, M3.ON_SURFACE);
		TtfTextRenderer.drawString(graphics, "点输入框编辑，回车确认", panelX() + PADDING, panelY() + PADDING + 24,
				M3.ON_SURFACE_VARIANT_BRIGHT);

		drawField(graphics, "标签", field.label, labelY(), Editing.LABEL, mouseX, mouseY);
		drawField(graphics, "表达式", field.expression, expressionY(), Editing.EXPRESSION, mouseX, mouseY);

		String preview = Expression.evaluate(field.expression);
		TtfTextRenderer.drawString(graphics, "预览", fieldX(), previewY(), M3.OUTLINE);
		M3.chip(graphics, fieldX() + 44, previewY() - 11, preview, M3.PRIMARY_CONTAINER, M3.ON_PRIMARY_CONTAINER);

		TtfTextRenderer.drawString(graphics, "图标", fieldX(), iconGridY() - 8, M3.OUTLINE);
		renderIconGrid(graphics, mouseX, mouseY);
		renderFooter(graphics, mouseX, mouseY);
	}

	private void drawField(GuiGraphicsExtractor graphics, String label, String value, int y, Editing part,
			int mouseX, int mouseY) {
		boolean active = editing == part;
		boolean hovered = M3.inside(mouseX, mouseY, fieldX(), y, fieldWidth(), FIELD_HEIGHT);

		Shapes.outlined(graphics, fieldX(), y, fieldWidth(), FIELD_HEIGHT, 12,
				active || hovered ? M3.SURFACE_CONTAINER_HIGHEST : M3.SURFACE_CONTAINER,
				active ? M3.PRIMARY : M3.OUTLINE_VARIANT);

		TtfTextRenderer.drawString(graphics, label, fieldX() + 12, y + 19, M3.OUTLINE);
		String text = active ? buffer : value;
		TtfTextRenderer.drawString(graphics, text, fieldX() + 56, y + 19,
				active ? M3.ON_SURFACE : M3.ON_SURFACE_VARIANT_BRIGHT);
	}

	private void renderIconGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		IconPainter.Icon[] icons = IconPainter.Icon.values();
		IconPainter.Icon selected = iconOf(field);

		for (int i = 0; i < icons.length; i++) {
			int x = iconCellX(i);
			int y = iconCellY(i);

			if (y + ICON_CELL > panelY() + panelHeight() - PADDING - FOOTER_HEIGHT - 8) {
				break;
			}

			boolean isSelected = icons[i] == selected;
			boolean hovered = M3.inside(mouseX, mouseY, x, y, ICON_CELL, ICON_CELL);
			Shapes.filled(graphics, x, y, ICON_CELL, ICON_CELL, 10,
					isSelected ? M3.PRIMARY_CONTAINER : hovered ? M3.SURFACE_CONTAINER_HIGHEST : M3.SURFACE_CONTAINER_HIGH);
			TtfTextRenderer.drawIcon(graphics, icons[i], x + (ICON_CELL - 10) / 2.0F, y + (ICON_CELL - 10) / 2.0F);
		}
	}

	private void renderFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = fieldX();
		int y = panelY() + panelHeight() - PADDING - FOOTER_HEIGHT;

		buttons.clear();
		buttons.add(new Button("done", "完成", x, y, 84, FOOTER_HEIGHT, true));
		buttons.add(new Button("delete", "删除此项", x + 92, y, 96, FOOTER_HEIGHT, false));

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
				onButton(button.key());
				return true;
			}
		}

		if (M3.inside(mouseX, mouseY, fieldX(), labelY(), fieldWidth(), FIELD_HEIGHT)) {
			begin(Editing.LABEL, field.label);
			return true;
		}

		if (M3.inside(mouseX, mouseY, fieldX(), expressionY(), fieldWidth(), FIELD_HEIGHT)) {
			begin(Editing.EXPRESSION, field.expression);
			return true;
		}

		IconPainter.Icon[] icons = IconPainter.Icon.values();

		for (int i = 0; i < icons.length; i++) {
			if (M3.inside(mouseX, mouseY, iconCellX(i), iconCellY(i), ICON_CELL, ICON_CELL)) {
				field.icon = icons[i].name();
				OhMyF3Config.get().save();
				return true;
			}
		}

		commit();
		return super.mouseClicked(event, quickMove);
	}

	private void begin(Editing part, String current) {
		commit();
		editing = part;
		buffer = current == null ? "" : current;
	}

	private void commit() {
		if (editing == Editing.NONE) {
			return;
		}

		if (editing == Editing.LABEL) {
			field.label = buffer;
		} else if (editing == Editing.EXPRESSION) {
			field.expression = buffer;
		}

		editing = Editing.NONE;
		OhMyF3Config.get().save();
	}

	private void onButton(String key) {
		switch (key) {
			case "done" -> close();
			case "delete" -> {
				OhMyF3Config config = OhMyF3Config.get();
				config.customFields.remove(field);
				config.save();
				Minecraft.getInstance().setScreenAndShow(parent);
			}
			default -> {
			}
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (editing == Editing.NONE) {
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
			if (editing != Editing.NONE) {
				editing = Editing.NONE;
				return true;
			}

			close();
			return true;
		}

		if (editing != Editing.NONE) {
			if (key == 259) {
				if (!buffer.isEmpty()) {
					buffer = buffer.substring(0, buffer.offsetByCodePoints(buffer.length(), -1));
				}

				return true;
			}

			if (key == 257 || key == 335) {
				commit();
				return true;
			}

			return true;
		}

		return super.keyPressed(event);
	}

	private void close() {
		commit();
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
