package com.ohmyf3;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OhMyF3 implements ModInitializer {
	public static final String MOD_ID = "ohmyf3";

	// Use your mod id as the logger name so log lines are attributable.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Runs once Minecraft reached a mod-load-ready state.
		// Some things (like resources) may still be uninitialized here.
		LOGGER.info("OhMyF3 initialised on Minecraft 26.3");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
