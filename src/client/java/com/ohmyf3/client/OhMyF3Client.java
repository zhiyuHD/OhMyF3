package com.ohmyf3.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import com.ohmyf3.client.mixin.ScreenAccessor;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;


public class OhMyF3Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		KeyMapping settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.ohmyf3.settings", InputConstants.Type.KEYBOARD, InputConstants.KEY_F9,
				KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (settingsKey.consumeClick()) {
				client.setScreenAndShow(new SettingsScreen());
			}
		});

		// Key bindings do not fire while a screen is open, so the menus get a real button.
		// That also makes the settings reachable from the title screen.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof TitleScreen || screen instanceof PauseScreen) {
				((ScreenAccessor) screen).ohmyf3$addRenderableWidget(Button.builder(Component.literal("OhMyF3"),
						button -> client.setScreenAndShow(new SettingsScreen()))
						.bounds(scaledWidth - 84, 4, 80, 20).build());
			}
		});
	}
}
