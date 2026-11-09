package com.ohmyf3.client.mixin;

import com.ohmyf3.animation.SlideInAnimation;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.DebugScreenOverlay;

import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slides the F3 debug screen in from the left instead of having it appear instantly.
 *
 * <p>Minecraft 26.3 builds the HUD through an extract/draw split: {@link Hud#extractDebugOverlay}
 * records the debug overlay's geometry into a {@link GuiGraphicsExtractor}, and the
 * extractor bakes the current pose into every vertex it emits. Translating that pose
 * around the extraction call therefore moves the whole overlay.
 *
 * <p>{@code extractDebugOverlay} is the right hook because it runs every frame (only the
 * debug-options screen skips it) and has a single return path, so the matrix push and pop
 * below always pair up. {@link DebugScreenOverlay#showDebugScreen()} is the visibility
 * source of truth, and its own extraction early-returns while hidden.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
	/** How long the slide takes. */
	private static final long SLIDE_DURATION_NANOS = 260_000_000L;

	/** How far the overlay starts to the left of its resting position, in GUI pixels. */
	private static final float SLIDE_DISTANCE = 200.0F;

	private static final SlideInAnimation OHMYF3$SLIDE = new SlideInAnimation(SLIDE_DURATION_NANOS);

	/** Whether this frame pushed a matrix translation that still has to be popped. */
	private static boolean ohmyf3$posePushed;

	@Shadow
	@Final
	private DebugScreenOverlay debugOverlay;

	@Inject(method = "extractDebugOverlay", at = @At("HEAD"))
	private void ohmyf3$slideIn(GuiGraphicsExtractor extractor, CallbackInfo ci) {
		long now = System.nanoTime();
		boolean visible = this.debugOverlay.showDebugScreen();

		float offset = OHMYF3$SLIDE.offset(visible, now);

		// Zero means either hidden or already settled: leave the pose untouched.
		if (offset <= 0.0F) {
			return;
		}

		Matrix3x2fStack pose = extractor.pose();
		pose.pushMatrix();
		pose.translate(-offset * SLIDE_DISTANCE, 0.0F);
		ohmyf3$posePushed = true;
	}

	@Inject(method = "extractDebugOverlay", at = @At("RETURN"))
	private void ohmyf3$slideInEnd(GuiGraphicsExtractor extractor, CallbackInfo ci) {
		if (ohmyf3$posePushed) {
			ohmyf3$posePushed = false;
			extractor.pose().popMatrix();
		}
	}
}
