package com.ohmyf3.client;

import com.ohmyf3.OhMyF3;
import com.ohmyf3.config.OhMyF3Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings screen: a short list of toggles that write straight to {@link OhMyF3Config}.
 *
 * <p>Buttons are rebuilt in place when clicked so the label always reflects the stored value;
 * every change is saved immediately, which keeps the screen free of an explicit "apply" step.
 */
public final class SettingsScreen extends Screen {
	private static final int WIDTH = 220;
	private static final int ROW = 24;

	public SettingsScreen() {
		super(Component.literal("OhMyF3 设置"));
		OhMyF3.LOGGER.info("settings screen opened");
	}

	@Override
	protected void init() {
		OhMyF3Config config = OhMyF3Config.get();
		int x = this.width / 2 - WIDTH / 2;
		int y = this.height / 4;

		addRenderableWidget(toggle(x, y, "中文标签", config.chineseLabels, value -> config.chineseLabels = value));
		addRenderableWidget(toggle(x, y + ROW, "关键词图标", config.icons, value -> config.icons = value));
		addRenderableWidget(toggle(x, y + ROW * 2, "背景模糊", config.blur, value -> config.blur = value));
		addRenderableWidget(
				toggle(x, y + ROW * 3, "滑入动画", config.slideAnimation, value -> config.slideAnimation = value));

		addRenderableWidget(Button.builder(Component.literal("完成"), button -> close())
				.bounds(x, y + ROW * 5, WIDTH, 20)
				.build());
	}

	/** A button that flips a boolean, persists it and updates its own label. */
	private Button toggle(int x, int y, String label, boolean initial, java.util.function.Consumer<Boolean> setter) {
		return Button.builder(Component.literal(label + "：" + onOff(initial)), button -> {
			boolean next = !state(label);
			setter.accept(next);
			OhMyF3Config.get().save();
			button.setMessage(Component.literal(label + "：" + onOff(next)));
		}).bounds(x, y, WIDTH, 20).build();
	}

	/** Reads the live value back from the config by label, so the button never drifts. */
	private static boolean state(String label) {
		OhMyF3Config config = OhMyF3Config.get();

		return switch (label) {
			case "中文标签" -> config.chineseLabels;
			case "关键词图标" -> config.icons;
			case "背景模糊" -> config.blur;
			case "滑入动画" -> config.slideAnimation;
			default -> false;
		};
	}

	private static String onOff(boolean value) {
		return value ? "开" : "关";
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
