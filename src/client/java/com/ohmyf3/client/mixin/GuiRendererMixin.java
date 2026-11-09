package com.ohmyf3.client.mixin;

import com.ohmyf3.client.font.BackdropBlur;

import net.minecraft.client.gui.render.GuiRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refreshes the blurred backdrop just before the GUI is drawn, so the copy holds this frame's
 * world and none of our cards.
 */
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void ohmyf3$captureBackdrop(CallbackInfo ci) {
		BackdropBlur.capture();
	}
}
