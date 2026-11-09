package com.ohmyf3.config;

import java.util.LinkedHashMap;
import java.util.Map;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Persisted settings for the mod, stored as {@code config/ohmyf3.json}.
 *
 * <p>Plain fields on purpose: Gson reads and writes them by name, and a missing or corrupt file
 * simply falls back to the defaults instead of preventing the game from starting.
 */
public final class OhMyF3Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ohmyf3.json");

	private static OhMyF3Config instance;

	/** Translate the debug screen's field names into Chinese. */
	public boolean chineseLabels = true;

	/** Draw the keyword icons and the icon gutter. */
	public boolean icons = true;

	/** Blur the frame behind the cards. */
	public boolean blur = true;

	/** Slide the columns in when F3 opens. */
	public boolean slideAnimation = true;

	/**
	 * Absolute positions for movable F3 elements, keyed by element id.
	 *
	 * <p>Keys are {@code column_left}, {@code column_right}, {@code pie}, or {@code row:<field>} for a
	 * single line dragged out of its column. Values are {@code {x, y}} in GUI pixels relative to the
	 * top-left of the debug screen; a missing key means "use the default anchor".
	 */
	public Map<String, int[]> layout = new LinkedHashMap<>();

	public static OhMyF3Config get() {
		if (instance == null) {
			instance = load();
		}

		return instance;
	}

	private static OhMyF3Config load() {
		try {
			if (Files.isRegularFile(FILE)) {
				try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
					OhMyF3Config loaded = GSON.fromJson(reader, OhMyF3Config.class);

					if (loaded != null) {
						return loaded;
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			// Corrupt config: keep the defaults and overwrite on the next save.
		}

		return new OhMyF3Config();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());

			try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			// Not worth crashing over; the setting just will not persist.
		}
	}

	/** Absolute position of an element, or {@code null} when it still uses its default anchor. */
	public int[] position(String key) {
		int[] value = layout == null ? null : layout.get(key);
		return value != null && value.length == 2 ? value : null;
	}

	/** Offset from the default anchor, for callers that only need a relative nudge. */
	public int[] offset(String key) {
		int[] value = position(key);
		return value == null ? new int[] {0, 0} : value;
	}

	/** Places an element at an absolute position and persists immediately. */
	public void move(String key, int x, int y) {
		if (layout == null) {
			layout = new LinkedHashMap<>();
		}

		layout.put(key, new int[] {x, y});
		save();
	}

	/** Puts every part back where it started. */
	public void resetLayout() {
		layout = new LinkedHashMap<>();
		save();
	}

	/** Replaces the singleton, used by tests and by the settings screen after edits. */
	public static void set(OhMyF3Config config) {
		instance = config;
	}
}
