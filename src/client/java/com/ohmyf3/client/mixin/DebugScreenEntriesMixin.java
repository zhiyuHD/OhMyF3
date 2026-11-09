package com.ohmyf3.client.mixin;

import com.ohmyf3.client.DebugEntryOrigin;

import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records which entry the debug overlay is about to ask for.
 *
 * <p>{@code DebugScreenOverlay} calls {@code DebugScreenEntries.getEntry(id)} once per visible
 * entry, immediately before that entry emits its lines, so the id seen here is exactly the
 * owner of the lines that follow. The id's namespace is what tells vanilla and mod content
 * apart.
 */
@Mixin(DebugScreenEntries.class)
public class DebugScreenEntriesMixin {
	@Inject(method = "getEntry", at = @At("HEAD"))
	private static void ohmyf3$markOwningEntry(Identifier id, CallbackInfoReturnable<DebugScreenEntry> cir) {
		DebugEntryOrigin.markEntry(id);
	}
}
