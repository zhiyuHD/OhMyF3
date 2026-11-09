package com.ohmyf3.font;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LabelsTest {
	@Test
	void translatesKnownFieldNames() {
		assertEquals("坐标", Labels.key("XYZ"));
		assertEquals("内存", Labels.key("Mem"));
		assertEquals("区块", Labels.key("Chunk"));
		assertEquals("帧率", Labels.key("fps"));
	}

	@Test
	void keepsUnknownNamesUntouched() {
		assertEquals("MyMod Widget", Labels.key("MyMod Widget"));
		assertEquals("", Labels.key(""));
	}

	@Test
	void rewritesKnownPhrasesOnly() {
		assertEquals("性能分析已隐藏", Labels.value("Profiler hidden"));
		assertEquals("编辑请按 [F3+F6]", Labels.value("To edit: press [F3+F6]"));
		assertEquals("something else", Labels.value("something else"));
	}

	@Test
	void coversMostVanillaFields() {
		// Guards against a translation table that silently loses entries.
		if (Labels.knownKeyCount() < 40) {
			throw new AssertionError("only " + Labels.knownKeyCount() + " translated keys");
		}
	}
}
