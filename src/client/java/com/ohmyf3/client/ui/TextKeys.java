package com.ohmyf3.client.ui;

import net.minecraft.client.input.KeyEvent;

/**
 * Falls back to key codes when a screen receives no character events.
 *
 * <p>Screens normally type through {@code charTyped}, but that event is skipped whenever the game
 * has an overlay active, which left the mod's text fields unable to accept anything. Mapping the GLFW
 * key codes covers the characters these fields need (letters, digits, quotes, parentheses,
 * operators); {@link #shifted(int)} supplies the shifted symbol for the same key.
 */
public final class TextKeys {
	private TextKeys() {
	}

	/** Character for a key code, or {@code null} when this key is not printable. */
	public static String character(int key, boolean shift) {
		if (key >= 65 && key <= 90) {
			char letter = (char) (shift ? key : key + 32);
			return String.valueOf(letter);
		}

		if (key >= 48 && key <= 57) {
			String digits = "0123456789";
			String shiftedDigits = ")!@#$%^&*(";
			int index = key - 48;
			return String.valueOf(shift ? shiftedDigits.charAt(index) : digits.charAt(index));
		}

		return switch (key) {
			case 32 -> " ";
			case 45 -> shift ? "_" : "-";
			case 61 -> shift ? "+" : "=";
			case 91 -> shift ? "{" : "[";
			case 93 -> shift ? "}" : "]";
			case 59 -> shift ? ":" : ";";
			case 39 -> shift ? "\"" : "'";
			case 44 -> shift ? "<" : ",";
			case 46 -> shift ? ">" : ".";
			case 47 -> shift ? "?" : "/";
			case 92 -> "\\";
			case 96 -> shift ? "~" : "`";
			default -> null;
		};
	}

	/** Whether {@code key} is the backspace key. */
	public static boolean isBackspace(int key) {
		return key == 259;
	}

	/** Whether {@code key} is enter or keypad enter. */
	public static boolean isEnter(int key) {
		return key == 257 || key == 335;
	}

	/** Shift state from the modifiers carried by a key event. */
	public static boolean shift(KeyEvent event) {
		return event.hasShiftDown();
	}
}
