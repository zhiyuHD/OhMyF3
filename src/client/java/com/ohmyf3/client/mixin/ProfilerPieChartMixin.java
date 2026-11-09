package com.ohmyf3.client.mixin;

import com.ohmyf3.client.chart.PieChartRenderer;
import com.ohmyf3.client.font.TtfTextRenderer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debugchart.ProfilerPieChart;
import net.minecraft.util.profiling.ProfileResults;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the profiler's hotspot pie chart.
 *
 * <p>Vanilla draws an extruded pie per scanline through {@code extractor.profilerChart} — blocky
 * edges, no text on the slices. {@link PieChartRenderer} draws a flat, supersampled pie with the
 * slice names in the system font instead. When the font is unavailable the injection does not
 * cancel, so vanilla still renders its own chart.
 */
@Mixin(ProfilerPieChart.class)
public abstract class ProfilerPieChartMixin {
	@Shadow
	private ProfileResults profilerPieChartResults;

	@Shadow
	private String profilerTreePath;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void ohmyf3$drawFlatPie(GuiGraphicsExtractor graphics, int x, int y, CallbackInfo ci) {
		if (!TtfTextRenderer.isReady() || this.profilerPieChartResults == null) {
			return;
		}

		if (PieChartRenderer.render(graphics, this.profilerPieChartResults, this.profilerTreePath, x, y) > 0) {
			ci.cancel();
		}
	}
}
