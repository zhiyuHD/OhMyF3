package com.ohmyf3.client.mixin;

import java.util.Collection;

import com.ohmyf3.client.DebugEntryOrigin;

import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tags every line the debug overlay's displayer receives with the namespace of the entry that
 * emitted it, so the renderer can separate vanilla rows from mod rows without inspecting text.
 *
 * <p>{@code DebugScreenOverlay$1} is the anonymous {@code DebugScreenDisplayer} the overlay
 * builds in {@code extractRenderState}; entries push their lines through these four methods.
 */
@Mixin(targets = "net.minecraft.client.gui.components.DebugScreenOverlay$1")
public class DebugScreenDisplayerMixin {
	@Inject(method = "addLine", at = @At("HEAD"))
	private void ohmyf3$tagLine(String line, CallbackInfo ci) {
		DebugEntryOrigin.tag(line);
	}

	@Inject(method = "addPriorityLine", at = @At("HEAD"))
	private void ohmyf3$tagPriorityLine(String line, CallbackInfo ci) {
		DebugEntryOrigin.tag(line);
	}

	@Inject(method = "addToGroup(Lnet/minecraft/resources/Identifier;Ljava/util/Collection;)V",
			at = @At("HEAD"))
	private void ohmyf3$tagGroup(Identifier groupId, Collection<String> lines, CallbackInfo ci) {
		DebugEntryOrigin.tag(lines, groupId);
	}

	@Inject(method = "addToGroup(Lnet/minecraft/resources/Identifier;Ljava/lang/String;)V",
			at = @At("HEAD"))
	private void ohmyf3$tagGroupLine(Identifier groupId, String line, CallbackInfo ci) {
		DebugEntryOrigin.tag(line, groupId);
	}
}
