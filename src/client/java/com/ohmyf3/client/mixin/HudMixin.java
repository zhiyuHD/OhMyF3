package com.ohmyf3.client.mixin;

import com.ohmyf3.client.DebugOverlaySlide;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.DebugScreenOverlay;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Samples the slide clock once per frame.
 *
 * <p>Minecraft 26.3 extracts the HUD every frame through {@link Hud#extractDebugOverlay}
 * (everything except the debug-options screen reaches this call), and the overlay's own
 * extraction early-returns while hidden. That makes this the one place where both the
 * "F3 is now visible" transition and the per-frame clock can be observed exactly once,
 * before either debug column is extracted.
 *
 * <p>The actual translation lives in {@link DebugScreenOverlayMixin}, because the two
 * debug columns are drawn from separate calls there.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Shadow
	@Final
	private DebugScreenOverlay debugOverlay;

	@Inject(method = "extractDebugOverlay", at = @At("HEAD"))
	private void ohmyf3$sampleSlideClock(GuiGraphicsExtractor extractor, CallbackInfo ci) {
		// showDebugScreen() is the same visibility check the overlay itself uses.
		DebugOverlaySlide.update(this.debugOverlay.showDebugScreen(), System.nanoTime());
	}
}
