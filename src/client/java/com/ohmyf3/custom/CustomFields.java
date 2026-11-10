package com.ohmyf3.custom;

import java.util.ArrayList;
import java.util.List;

import com.ohmyf3.config.OhMyF3Config;

/**
 * Turns the configured custom rows into debug lines the overlay can draw.
 *
 * <p>A line carries its icon in a marker prefix, {@code \u0001ICON\u0001label: value}: the picture
 * font picks icons by matching the row's field name, which a user-chosen icon cannot do, so the
 * choice travels with the line instead.
 */
public final class CustomFields {
	private static final char MARKER = '\u0001';

	private CustomFields() {
	}

	/** Complete lines (marker included) for every enabled custom row. */
	public static List<String> lines() {
		OhMyF3Config config = OhMyF3Config.get();

		if (config.customFields == null || config.customFields.isEmpty()) {
			return List.of();
		}

		List<String> lines = new ArrayList<>(config.customFields.size());

		for (OhMyF3Config.CustomField field : config.customFields) {
			if (field == null || !field.enabled) {
				continue;
			}

			lines.add(MARKER + (field.icon == null ? "CLOCK" : field.icon) + MARKER
					+ (field.label == null ? "自定义" : field.label) + ": " + Expression.evaluate(field.expression));
		}

		return lines;
	}

	/** Appends the custom rows to a column's lines. */
	public static List<String> augment(List<String> lines) {
		List<String> custom = lines();

		if (custom.isEmpty()) {
			return lines;
		}

		List<String> result = new ArrayList<>(lines.size() + custom.size() + 1);
		result.addAll(lines);
		result.add("");
		result.addAll(custom);
		return result;
	}
}
