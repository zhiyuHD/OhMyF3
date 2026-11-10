package com.ohmyf3.client.mixin;

import java.util.List;

import com.ohmyf3.client.DebugEntryOrigin;
import com.ohmyf3.client.DebugOverlaySlide;
import com.ohmyf3.client.font.TtfTextRenderer;
import com.ohmyf3.config.OhMyF3Config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;

import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws both F3 columns with a system TrueType font instead of the vanilla bitmap font.
 *
 * <p>26.3 draws the debug screen as two independent columns through
 * {@code DebugScreenOverlay#extractLines(graphics, lines, alignLeft, scaledScreenWidth)}:
 * {@code alignLeft = true} puts the left column at {@code x = 2}, and {@code alignLeft = false}
 * puts the right column at {@code scaledScreenWidth - 2 - width}. Hooking the shared helper
 * means the two halves stay symmetric, lines added by other mods are drawn with the same
 * font, and the slide-in from the previous change still applies.
 *
 * <p>When the font is unavailable the injection simply does not cancel, so vanilla draws the
 * column and the {@code RETURN} hook below still pops the pose.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
	/** Whether the vanilla path pushed a translation that still has to be popped. */
	private static boolean ohmyf3$posePushed;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void ohmyf3$beginFrame(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		DebugEntryOrigin.beginFrame();
		TtfTextRenderer.beginFrame();
	}

	@Inject(method = "extractLines", at = @At("HEAD"), cancellable = true)
	private void ohmyf3$drawColumn(GuiGraphicsExtractor graphics, List<String> lines, boolean alignLeft,
			int scaledScreenWidth, CallbackInfo ci) {
		float offset = DebugOverlaySlide.offset();
		boolean pushed = false;

		if (offset > 0.0F) {
			// The left column comes in from the left, the right column from the right.
			float direction = alignLeft ? -1.0F : 1.0F;

			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(direction * offset * TtfTextRenderer.travel(alignLeft), 0.0F);
			pushed = true;
		}

		if (TtfTextRenderer.isReady()) {
			// User rows are appended to the left column so they read as part of the overlay.
			List<String> content = alignLeft ? com.ohmyf3.custom.CustomFields.augment(lines) : lines;
			float scale = Math.max(0.5F, Math.min(2.0F, OhMyF3Config.get().scale));

			if (scale != 1.0F) {
				// Scaling the pose keeps the font sharp: drawLines derives its rasterisation scale
				// from the pose, so glyphs are baked at the zoomed size rather than stretched.
				graphics.pose().pushMatrix();
				graphics.pose().scale(scale, scale);
				TtfTextRenderer.drawLines(graphics, content, alignLeft, Math.round(scaledScreenWidth / scale));
				graphics.pose().popMatrix();
			} else {
				TtfTextRenderer.drawLines(graphics, content, alignLeft, scaledScreenWidth);
			}

			// Cancelling skips the RETURN hook, so the pose must be restored here.
			if (pushed) {
				graphics.pose().popMatrix();
			}

			ci.cancel();
			return;
		}

		ohmyf3$posePushed = pushed;
	}

	@Inject(method = "extractLines", at = @At("RETURN"))
	private void ohmyf3$drawColumnEnd(GuiGraphicsExtractor graphics, List<String> lines, boolean alignLeft,
			int scaledScreenWidth, CallbackInfo ci) {
		if (ohmyf3$posePushed) {
			ohmyf3$posePushed = false;
			graphics.pose().popMatrix();
		}
	}
}
