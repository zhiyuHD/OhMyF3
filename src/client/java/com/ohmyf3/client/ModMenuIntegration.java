package com.ohmyf3.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu hook: adds an "OhMyF3" config button to the mods list.
 *
 * <p>Fabric only instantiates this when ModMenu is installed — the {@code modmenu} entry point is
 * read by ModMenu itself — so the compile-only dependency never has to be present at runtime.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new SettingsScreen();
	}
}
