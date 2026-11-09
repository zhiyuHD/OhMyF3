package com.ohmyf3.client;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.resources.Identifier;

/**
 * Tracks which mod (if any) produced each debug-screen line.
 *
 * <p>The debug screen is assembled from registered entries: for every visible
 * {@link Identifier} the overlay looks the entry up and asks it to emit lines through a
 * {@code DebugScreenDisplayer}. Vanilla registers its entries under the {@code minecraft}
 * namespace and mods register under their own, so the namespace attached to the entry that
 * emitted a line is an exact answer to "who wrote this row" — no guessing from the text.
 *
 * <p>Lines are matched by object identity: the displayer stores the same {@code String}
 * instances that the overlay later hands to {@code extractLines}, so an identity map is
 * enough and avoids confusing two mods that print identical text.
 */
public final class DebugEntryOrigin {
	private static final String VANILLA_NAMESPACE = "minecraft";

	/** Namespace of the entry currently being displayed; set just before it emits lines. */
	private static final ThreadLocal<String> CURRENT_NAMESPACE = new ThreadLocal<>();

	private static final Map<String, Boolean> MODDED = new IdentityHashMap<>();

	private DebugEntryOrigin() {
	}

	/** Clears per-frame state; called when the overlay starts assembling its columns. */
	public static void beginFrame() {
		MODDED.clear();
		CURRENT_NAMESPACE.remove();
	}

	/** Remembers which entry is about to emit lines. */
	public static void markEntry(Identifier id) {
		CURRENT_NAMESPACE.set(id == null ? null : id.getNamespace());
	}

	/** Tags a line with the namespace of the entry currently being displayed. */
	public static void tag(String line) {
		String namespace = CURRENT_NAMESPACE.get();

		if (line != null && namespace != null) {
			MODDED.put(line, !VANILLA_NAMESPACE.equals(namespace));
		}
	}

	/** Tags lines emitted into a named group, whose id is authoritative. */
	public static void tag(Collection<String> lines, Identifier groupId) {
		if (groupId == null) {
			return;
		}

		boolean modded = !VANILLA_NAMESPACE.equals(groupId.getNamespace());

		for (String line : lines) {
			if (line != null) {
				MODDED.put(line, modded);
			}
		}
	}

	/** Tags a single line emitted into a named group. */
	public static void tag(String line, Identifier groupId) {
		if (line != null && groupId != null) {
			MODDED.put(line, !VANILLA_NAMESPACE.equals(groupId.getNamespace()));
		}
	}

	/**
	 * Whether the line came from a mod.
	 *
	 * @return {@code null} when the line was never tagged (separators, or lines the overlay
	 *         synthesises itself)
	 */
	public static Boolean modded(String line) {
		return line == null ? null : MODDED.get(line);
	}
}
