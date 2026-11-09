package com.ohmyf3.font;

import java.util.Locale;

/**
 * Maps debug-screen keywords to icons.
 *
 * <p>Matching is case-insensitive and only at word boundaries, so "fps" matches the frame-rate
 * field but not a letter inside another word. Longer keywords are listed first so that
 * "OpenGL" wins over a hypothetical "Open" rule.
 */
public final class KeywordIcons {
	private record Rule(String keyword, IconPainter.Icon icon) {
	}

	private static final Rule[] RULES = {
			new Rule("Sodium", IconPainter.Icon.SODIUM),
			new Rule("OpenGL", IconPainter.Icon.CHIP),
			new Rule("Chunk", IconPainter.Icon.GRID),
			new Rule("MiB", IconPainter.Icon.MEMORY),
			new Rule("XYZ", IconPainter.Icon.CROSSHAIR),
			new Rule("fps", IconPainter.Icon.GAUGE),
			new Rule("Java", IconPainter.Icon.CUP),
			new Rule("Seed", IconPainter.Icon.DICE)
	};

	private KeywordIcons() {
	}

	/**
	 * Returns the icon for a keyword starting exactly at {@code index}, or {@code null}.
	 */
	public static IconPainter.Icon iconStartingAt(String line, int index) {
		for (Rule rule : RULES) {
			String keyword = rule.keyword();

			if (index + keyword.length() > line.length()) {
				continue;
			}

			if (!line.regionMatches(true, index, keyword, 0, keyword.length())) {
				continue;
			}

			if (!isBoundary(line, index - 1) || !isBoundary(line, index + keyword.length())) {
				continue;
			}

			return rule.icon();
		}

		return null;
	}

	/**
	 * A boundary is anything that is not a letter, so units stay attached to their number
	 * ("3800MiB" must still match "MiB") while "gfpsx" does not match "fps".
	 */
	private static boolean isBoundary(String line, int index) {
		if (index < 0 || index >= line.length()) {
			return true;
		}

		char c = line.charAt(index);
		return !Character.isLetter(c) && c != '_';
	}
}
