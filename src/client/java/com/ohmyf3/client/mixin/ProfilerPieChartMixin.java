package com.ohmyf3.client.mixin;

import java.util.List;

import com.ohmyf3.client.chart.PieChartRenderer;
import com.ohmyf3.client.chart.PieLayout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debugchart.ProfilerPieChart;
import net.minecraft.util.profiling.ResultField;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the profiler pie and lets the whole chart be repositioned.
 *
 * <p>The pie itself is redirected to a flat, anti-aliased, labelled one. The surrounding panel and
 * legend are vanilla's, so instead of moving the pie on its own (which would strand its background)
 * the whole extraction is translated by the player's chosen offset.
 */
@Mixin(ProfilerPieChart.class)
public abstract class ProfilerPieChartMixin {
	/** Mixin state lives in the target class, so keep it minimal and reset it every frame. */
	private static boolean ohmyf3$translated;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void ohmyf3$beginChart(GuiGraphicsExtractor graphics, int guiWidth, int guiHeight, CallbackInfo ci) {
		ohmyf3$translated = false;
		int[] translation = PieLayout.translation(guiWidth, guiHeight);

		if (translation == null || (translation[0] == 0 && translation[1] == 0)) {
			return;
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate(translation[0], translation[1]);
		ohmyf3$translated = true;
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void ohmyf3$endChart(GuiGraphicsExtractor graphics, int guiWidth, int guiHeight, CallbackInfo ci) {
		if (ohmyf3$translated) {
			ohmyf3$translated = false;
			graphics.pose().popMatrix();
		}
	}

	@Redirect(method = "extractRenderState",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;profilerChart(Ljava/util/List;IIII)V"))
	private void ohmyf3$drawFlatPie(GuiGraphicsExtractor graphics, List<ResultField> fields,
			int x1, int y1, int x2, int y2) {
		PieLayout.observeDefaultCentre(x1, y1, x2, y2);

		if (!PieChartRenderer.render(graphics, fields, x1, y1, x2, y2)) {
			graphics.profilerChart(fields, x1, y1, x2, y2);
		}
	}
}
