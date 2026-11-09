package com.ohmyf3.client.mixin;

import java.util.List;

import com.ohmyf3.client.chart.PieChartRenderer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debugchart.ProfilerPieChart;
import net.minecraft.util.profiling.ResultField;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps only the pie drawing, not the chart around it.
 *
 * <p>{@code ProfilerPieChart.extractRenderState} works out the layout (centre, legend height,
 * breadcrumb) and then hands the finishing rectangle to
 * {@code GuiGraphicsExtractor.profilerChart}. Redirecting that one call lets the mod draw a flat,
 * anti-aliased, labelled pie while vanilla keeps everything else — including the legend and the
 * tree-path navigation. If our renderer declines, the original call runs unchanged.
 */
@Mixin(ProfilerPieChart.class)
public abstract class ProfilerPieChartMixin {
	@Redirect(method = "extractRenderState",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;profilerChart(Ljava/util/List;IIII)V"))
	private void ohmyf3$drawFlatPie(GuiGraphicsExtractor graphics, List<ResultField> fields,
			int x1, int y1, int x2, int y2) {
		if (!PieChartRenderer.render(graphics, fields, x1, y1, x2, y2)) {
			graphics.profilerChart(fields, x1, y1, x2, y2);
		}
	}
}
