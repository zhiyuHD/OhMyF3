package com.ohmyf3.client.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes {@code Screen#addRenderableWidget}, which is protected. */
@Mixin(Screen.class)
public interface ScreenAccessor {
	@Invoker("addRenderableWidget")
	<T extends GuiEventListener & Renderable & NarratableEntry> T ohmyf3$addRenderableWidget(T widget);
}
