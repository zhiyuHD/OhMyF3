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

	/** Entry currently being displayed; set just before it emits lines. */
	private static final ThreadLocal<Identifier> CURRENT_ENTRY = new ThreadLocal<>();

	/** Registered owner of each line (identity keyed, like the displayer's own strings). */
	private static final Map<String, Identifier> OWNERS = new IdentityHashMap<>();

	private DebugEntryOrigin() {
	}

	/** Clears per-frame state; called when the overlay starts assembling its columns. */
	public static void beginFrame() {
		OWNERS.clear();
		CURRENT_ENTRY.remove();
	}

	/** Remembers which entry is about to emit lines. */
	public static void markEntry(Identifier id) {
		CURRENT_ENTRY.set(id);
	}

	/** The entry that produced {@code line}, or {@code null} when it was synthesised. */
	public static Identifier ownerOf(String line) {
		return line == null ? null : OWNERS.get(line);
	}

	/** Tags a line with the entry currently being displayed. */
	public static void tag(String line) {
		Identifier entry = CURRENT_ENTRY.get();

		if (line != null && entry != null) {
			OWNERS.put(line, entry);
		}
	}

	/** Tags lines emitted into a named group, whose id is authoritative. */
	public static void tag(Collection<String> lines, Identifier groupId) {
		if (groupId == null) {
			return;
		}

		for (String line : lines) {
			if (line != null) {
				OWNERS.put(line, groupId);
			}
		}
	}

	/** Tags a single line emitted into a named group. */
	public static void tag(String line, Identifier groupId) {
		if (line != null && groupId != null) {
			OWNERS.put(line, groupId);
		}
	}

	/**
	 * Whether the line came from a mod.
	 *
	 * @return {@code null} when the line was never tagged (separators, or lines the overlay
	 *         synthesises itself)
	 */
	public static Boolean modded(String line) {
		Identifier owner = ownerOf(line);
		return owner == null ? null : !VANILLA_NAMESPACE.equals(owner.getNamespace());
	}
}
