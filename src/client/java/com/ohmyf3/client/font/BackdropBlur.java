package com.ohmyf3.client.font;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import com.ohmyf3.client.mixin.GameRendererAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Produces a blurred copy of the frame for the cards to sample, so the blur is limited to the
 * card areas instead of blurring the whole screen.
 *
 * <p>Vanilla's own blur ({@code GameRenderer.processBlurEffect}) runs the {@code minecraft:blur}
 * post chain directly on the main render target, which destroys the sharp image — that is why
 * it can only be used for a full-screen blur. This class does the same thing on a
 * <em>copy</em> instead:
 *
 * <ol>
 *   <li>{@link RenderTarget#copyColorFrom} copies the main target to a target we own (an engine
 *       copy, no raw GL);</li>
 *   <li>the same {@code minecraft:blur} post chain is run over that copy;</li>
 *   <li>the cards sample the blurred copy through their own UVs.</li>
 * </ol>
 *
 * <p>Capture happens at the start of the GUI render pass, before any card is drawn, so the copy
 * contains this frame's world and never our own output.
 */
public final class BackdropBlur {
	private static final Logger LOGGER = LoggerFactory.getLogger("ohmyf3/blur");

	private static final Identifier BLUR_CHAIN = Identifier.fromNamespaceAndPath("minecraft", "blur");

	private static boolean disabled;
	private static boolean initialised;
	private static boolean loggedChain;

	private static RenderTarget target;
	private static GpuTextureView view;

	private BackdropBlur() {
	}

	public static boolean isReady() {
		return initialised && !disabled && view != null && TtfTextRenderer.sampler() != null;
	}

	public static GpuTextureView view() {
		return view;
	}

	/** Sampling state, borrowed from the font atlas once that has initialised. */
	public static GpuSampler sampler() {
		return TtfTextRenderer.sampler();
	}

	/** Called once per frame, before the GUI is drawn. */
	public static void capture() {
		if (disabled) {
			return;
		}

		try {
			Minecraft minecraft = Minecraft.getInstance();
			RenderTarget main = minecraft.gameRenderer.mainRenderTarget();

			if (main == null || main.width <= 0 || main.height <= 0) {
				return;
			}

			ensureTarget(main.width, main.height);

			if (disabled) {
				return;
			}

			// The chain only exists once the shader manager has loaded its resources, which is
			// after the first few frames; keep asking instead of disabling permanently.
			PostChain chain = minecraft.getShaderManager().getPostChain(BLUR_CHAIN, LevelTargetBundle.MAIN_TARGETS);

			if (chain == null) {
				return;
			}

			if (!loggedChain) {
				loggedChain = true;
				LOGGER.info("backdrop blur chain acquired");
			}

			// Copy first: the post chain blurs in place, and the main target must stay sharp
			// for everything drawn after us.
			target.copyColorFrom(main);

			CrossFrameResourcePool pool = ((GameRendererAccessor) minecraft.gameRenderer).ohmyf3$resourcePool();
			chain.process(target, pool);
		} catch (Throwable throwable) {
			disabled = true;
			LOGGER.warn("Backdrop blur disabled ({}); cards keep their plain fill",
					throwable.getClass().getSimpleName());
		}
	}

	private static void ensureTarget(int width, int height) {
		if (target == null) {
			target = new TextureTarget("ohmyf3-backdrop", width, height, GpuFormat.RGBA8_UNORM,
					GpuFormat.D32_FLOAT);
			view = target.getColorTextureView();
			initialised = true;
		} else if (target.width != width || target.height != height) {
			target.resize(width, height);
			view = target.getColorTextureView();
		}
	}
}
