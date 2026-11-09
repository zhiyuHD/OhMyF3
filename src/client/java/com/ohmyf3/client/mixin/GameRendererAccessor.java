package com.ohmyf3.client.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;

import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the per-frame resource pool so post chains can be run from our own code. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Accessor("resourcePool")
	CrossFrameResourcePool ohmyf3$resourcePool();
}
