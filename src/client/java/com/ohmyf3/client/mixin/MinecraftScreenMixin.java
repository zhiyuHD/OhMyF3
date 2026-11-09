package com.ohmyf3.client.mixin;

import com.ohmyf3.client.M3DebugOptionsScreen;
import com.ohmyf3.client.font.TtfTextRenderer;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.debug.DebugOptionsScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps the vanilla F3 display-options screen for the mod's Material 3 one.
 *
 * <p>26.3 opens it with {@code Gui.setScreen(new DebugOptionsScreen())} from the key handler — not
 * through {@code Minecraft.setScreenAndShow} — so the swap has to happen here. Replacing the
 * argument keeps the hand-off in one place; if the font is unavailable the vanilla screen is kept so
 * the options stay reachable.
 */
@Mixin(Gui.class)
public abstract class MinecraftScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen ohmyf3$useMaterialOptions(Screen screen) {
		if (screen instanceof DebugOptionsScreen && TtfTextRenderer.isReady()) {
			return new M3DebugOptionsScreen();
		}

		return screen;
	}
}
