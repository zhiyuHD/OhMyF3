package com.ohmyf3.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class KeywordIconsTest {
	@Test
	void matchesRealDebugLines() {
		String line = "61 fps T: 120 (fifo) @119.96Hz";

		assertEquals(IconPainter.Icon.GAUGE, KeywordIcons.iconStartingAt(line, line.indexOf("fps")));
	}

	@Test
	void matchingIsCaseInsensitive() {
		String memory = "Mem: 17% 666/3800MiB";
		assertEquals(IconPainter.Icon.MEMORY, KeywordIcons.iconStartingAt(memory, memory.indexOf("MiB")));
		assertEquals(IconPainter.Icon.CROSSHAIR, KeywordIcons.iconStartingAt("XYZ: 1 / 2 / 3", 0));
	}

	@Test
	void ignoresMatchesInsideWords() {
		// "fps" inside "gfpsx" must not light up.
		assertNull(KeywordIcons.iconStartingAt("gfpsx", 1));
		// "Chunk" glued to another word on the left.
		assertNull(KeywordIcons.iconStartingAt("SubChunk:", 3));
	}

	@Test
	void doesNotMatchPartialKeywords() {
		assertNull(KeywordIcons.iconStartingAt("fp", 0));
		assertNull(KeywordIcons.iconStartingAt("OpenG", 0));
	}

	@Test
	void matchesEachDocumentedKeyword() {
		assertEquals(IconPainter.Icon.SODIUM, KeywordIcons.iconStartingAt("Sodium Renderer", 0));
		assertEquals(IconPainter.Icon.CHIP, KeywordIcons.iconStartingAt("OpenGL 4.6", 0));
		assertEquals(IconPainter.Icon.GRID, KeywordIcons.iconStartingAt("Chunk: 1 2 3", 0));
		String allocated = "Allocated: 52% 1234MiB";
		assertEquals(IconPainter.Icon.MEMORY, KeywordIcons.iconStartingAt(allocated, allocated.indexOf("MiB")));
		assertEquals(IconPainter.Icon.CUP, KeywordIcons.iconStartingAt("Java: 25.0.4", 0));
		assertEquals(IconPainter.Icon.DICE, KeywordIcons.iconStartingAt("Seed: 42", 0));
	}
}
