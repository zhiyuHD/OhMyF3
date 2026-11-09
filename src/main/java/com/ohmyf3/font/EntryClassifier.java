package com.ohmyf3.font;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a raw debug-screen line into an icon-led {@code key: value} entry.
 *
 * <p>Vanilla 26.3 emits one flat string per datum, so the category has to be recovered from
 * the text. Keys are matched against a table of the fields vanilla currently prints; anything
 * else is treated as contributed by another mod, which lets the renderer give those rows
 * their own card.
 *
 * <p>Classification is heuristic by construction: a mod that prints "Mem: ..." would be
 * classified as vanilla. Detecting the owning entry instead would need a hook inside
 * {@code DebugScreenOverlay}, which is a much larger change.
 */
public final class EntryClassifier {
	/**
	 * One classified line. {@code key} may be empty when the line has no {@code key:} form.
	 *
	 * <p>This class only recovers structure (icon, key, value); whether a row came from a mod
	 * is decided by {@code DebugEntryOrigin} from the owning entry's namespace, not guessed
	 * from the text.
	 */
	public record Entry(IconPainter.Icon icon, String key, String value) {
	}

	private static final char FORMATTING_CODE = '\u00a7';

	/** Longest key accepted before we stop looking for a colon. */
	private static final int MAX_KEY_LENGTH = 26;

	private static final Map<String, IconPainter.Icon> ICONS_BY_KEY = new HashMap<>();

	static {
		ICONS_BY_KEY.put("xyz", IconPainter.Icon.CROSSHAIR);
		ICONS_BY_KEY.put("block", IconPainter.Icon.GRID);
		ICONS_BY_KEY.put("chunk", IconPainter.Icon.GRID);
		ICONS_BY_KEY.put("section-relative", IconPainter.Icon.GRID);
		ICONS_BY_KEY.put("client chunk", IconPainter.Icon.GRID);
		ICONS_BY_KEY.put("server chunk", IconPainter.Icon.GRID);
		ICONS_BY_KEY.put("facing", IconPainter.Icon.COMPASS);
		ICONS_BY_KEY.put("biome", IconPainter.Icon.LEAF);
		ICONS_BY_KEY.put("light", IconPainter.Icon.SUN);
		ICONS_BY_KEY.put("local difficulty", IconPainter.Icon.CLOCK);
		ICONS_BY_KEY.put("day", IconPainter.Icon.CLOCK);
		ICONS_BY_KEY.put("time", IconPainter.Icon.CLOCK);
		ICONS_BY_KEY.put("seed", IconPainter.Icon.DICE);
		ICONS_BY_KEY.put("difficulty", IconPainter.Icon.DICE);
		ICONS_BY_KEY.put("entities", IconPainter.Icon.PACKAGE);
		ICONS_BY_KEY.put("entity", IconPainter.Icon.PACKAGE);
		ICONS_BY_KEY.put("size", IconPainter.Icon.PACKAGE);
		ICONS_BY_KEY.put("e", IconPainter.Icon.PACKAGE);
		ICONS_BY_KEY.put("fps", IconPainter.Icon.GAUGE);
		ICONS_BY_KEY.put("t", IconPainter.Icon.GAUGE);
		ICONS_BY_KEY.put("mem", IconPainter.Icon.MEMORY);
		ICONS_BY_KEY.put("pools", IconPainter.Icon.MEMORY);
		ICONS_BY_KEY.put("allocation rate", IconPainter.Icon.MEMORY);
		ICONS_BY_KEY.put("allocated", IconPainter.Icon.MEMORY);
		ICONS_BY_KEY.put("off-heap", IconPainter.Icon.MEMORY);
		ICONS_BY_KEY.put("chunk builder", IconPainter.Icon.ACTIVITY);
		ICONS_BY_KEY.put("tasks", IconPainter.Icon.ACTIVITY);
		ICONS_BY_KEY.put("duration", IconPainter.Icon.ACTIVITY);
		ICONS_BY_KEY.put("ts", IconPainter.Icon.ACTIVITY);
		ICONS_BY_KEY.put("n", IconPainter.Icon.ACTIVITY);
		ICONS_BY_KEY.put("qd", IconPainter.Icon.WIFI);
		ICONS_BY_KEY.put("java", IconPainter.Icon.CUP);
		ICONS_BY_KEY.put("cpu", IconPainter.Icon.CHIP);
		ICONS_BY_KEY.put("gpu", IconPainter.Icon.CHIP);
		ICONS_BY_KEY.put("opengl", IconPainter.Icon.CHIP);
		ICONS_BY_KEY.put("display", IconPainter.Icon.SETTINGS);
		ICONS_BY_KEY.put("window", IconPainter.Icon.SETTINGS);
		ICONS_BY_KEY.put("filtering", IconPainter.Icon.SETTINGS);
		ICONS_BY_KEY.put("fancy-clouds b", IconPainter.Icon.SETTINGS);
		ICONS_BY_KEY.put("debug charts", IconPainter.Icon.SETTINGS);
		ICONS_BY_KEY.put("sodium renderer", IconPainter.Icon.SODIUM);
		ICONS_BY_KEY.put("terrain rendering", IconPainter.Icon.SODIUM);
		ICONS_BY_KEY.put("integrated server", IconPainter.Icon.SERVER);
		ICONS_BY_KEY.put("dimension", IconPainter.Icon.SERVER);
		ICONS_BY_KEY.put("dim", IconPainter.Icon.SERVER);
	}

	private EntryClassifier() {
	}

	public static Entry classify(String line) {
		String plain = stripFormatting(line).trim();

		if (plain.isEmpty()) {
			return new Entry(IconPainter.Icon.DOT, "", line);
		}

		// "61 fps T: 120 (fifo) @119.96Hz" has no separator before the metric.
		int fpsIndex = plain.indexOf(" fps");

		if (fpsIndex > 0 && isAllDigits(plain.substring(0, fpsIndex))) {
			return new Entry(IconPainter.Icon.GAUGE, "fps", line);
		}

		String[] split = splitOnColon(line);

		if (split == null) {
			IconPainter.Icon icon = KeywordIcons.iconStartingAt(plain, 0);

			if (icon == null) {
				icon = IconPainter.Icon.DOT;
			}

			return new Entry(icon, "", line);
		}

		String key = stripFormatting(split[0]).trim();
		String lower = key.toLowerCase(Locale.ROOT);
		IconPainter.Icon icon = ICONS_BY_KEY.getOrDefault(lower, IconPainter.Icon.DOT);

		if (icon == IconPainter.Icon.DOT) {
			IconPainter.Icon keyword = KeywordIcons.iconStartingAt(plain, 0);

			if (keyword != null) {
				icon = keyword;
			}
		}

		return new Entry(icon, key, split[1]);
	}

	/** Splits on the first colon that appears before the key gets implausibly long. */
	private static String[] splitOnColon(String line) {
		int plainIndex = 0;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (c == FORMATTING_CODE && i + 1 < line.length()) {
				i++;
				continue;
			}

			if (c == ':' && plainIndex > 0) {
				return new String[]{line.substring(0, i), trimLeadingSpace(line.substring(i + 1))};
			}

			if (platIndexExceeded(plainIndex++)) {
				return null;
			}
		}

		return null;
	}

	private static boolean platIndexExceeded(int index) {
		return index > MAX_KEY_LENGTH;
	}

	private static String trimLeadingSpace(String text) {
		int start = 0;

		while (start < text.length() && text.charAt(start) == ' ') {
			start++;
		}

		return text.substring(start);
	}

	private static boolean isAllDigits(String text) {
		if (text.isEmpty()) {
			return false;
		}

		for (int i = 0; i < text.length(); i++) {
			if (!Character.isDigit(text.charAt(i))) {
				return false;
			}
		}

		return true;
	}

	private static String stripFormatting(String line) {
		StringBuilder builder = new StringBuilder(line.length());

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (c == FORMATTING_CODE && i + 1 < line.length()) {
				i++;
				continue;
			}

			builder.append(c);
		}

		return builder.toString();
	}
}
