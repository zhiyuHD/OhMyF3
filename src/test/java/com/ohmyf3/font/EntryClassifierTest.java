package com.ohmyf3.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntryClassifierTest {
	@Test
	void splitsKeyAndValue() {
		var entry = EntryClassifier.classify("XYZ: -137.147 / 64.00000 / -162.874");

		assertEquals("XYZ", entry.key());
		assertEquals("-137.147 / 64.00000 / -162.874", entry.value());
		assertEquals(IconPainter.Icon.CROSSHAIR, entry.icon());
		assertTrue(entry.vanilla());
	}

	@Test
	void keepsValueAfterTheFirstColon() {
		var entry = EntryClassifier.classify("Block: -128 64 -163 Chunk: -8 4 -11");

		assertEquals("Block", entry.key());
		assertEquals("-128 64 -163 Chunk: -8 4 -11", entry.value());
		assertEquals(IconPainter.Icon.GRID, entry.icon());
	}

	@Test
	void handlesTheFpsLineWithoutKey() {
		var entry = EntryClassifier.classify("61 fps T: 120 (fifo) @119.96Hz");

		assertEquals("fps", entry.key());
		assertEquals(IconPainter.Icon.GAUGE, entry.icon());
		assertTrue(entry.vanilla());
	}

	@Test
	void stripsFormattingCodesWhenReadingTheKey() {
		var entry = EntryClassifier.classify("\u00a7aMem: 15% 607/3806 MiB");

		assertEquals("Mem", entry.key());
		assertEquals(IconPainter.Icon.MEMORY, entry.icon());
		assertTrue(entry.vanilla());
	}

	@Test
	void unknownKeysAreTreatedAsModContent() {
		var entry = EntryClassifier.classify("MyMod Widget: 42");

		assertEquals("MyMod Widget", entry.key());
		assertFalse(entry.vanilla());
		assertEquals(IconPainter.Icon.DOT, entry.icon());
	}

	@Test
	void modRendererLinesWithoutColonAlsoLandInTheModZone() {
		// Sodium prints its renderer name as a bare line, which is not a vanilla field.
		var entry = EntryClassifier.classify("\u00a7aSodium Renderer (0.9.2+mc26.3)");

		assertEquals("", entry.key());
		assertFalse(entry.vanilla());
		assertEquals(IconPainter.Icon.SODIUM, entry.icon());
	}

	@Test
	void vanillaLinesWithoutColonStayInTheVanillaZone() {
		var entry = EntryClassifier.classify("N=00501 SNR=00184 STA=00003 DYN=00228 (DIR=00)");

		assertTrue(entry.vanilla());
	}

	@Test
	void everyRowGetsAnIcon() {
		String[] lines = {
				"fancy-clouds B: 2",
				"Filtering: RGSS",
				"Pools: Geometry 297/420 MiB",
				"Chunk Builder: Schd=00 | Busy=00",
				"Java: 25.0.4",
				"OpenGL 4.6 (Core Profile) Mesa 26.2.3",
				"something completely unknown"
		};

		for (String line : lines) {
			var entry = EntryClassifier.classify(line);
			assertTrue(entry.icon() != null, "no icon for: " + line);
		}
	}
}
