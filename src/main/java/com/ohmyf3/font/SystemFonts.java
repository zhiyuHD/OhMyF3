package com.ohmyf3.font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds a usable system font without bundling one.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>the {@code ohmyf3.font} system property (a path to a .ttf),</li>
 *   <li>the {@code OHMYF3_FONT} environment variable,</li>
 *   <li>fontconfig's {@code fc-match} answer for a plain sans-serif,</li>
 *   <li>a scan of the usual font directories for well-known TrueType families.</li>
 * </ol>
 *
 * <p>Only real TrueType outlines are accepted: {@code .ttc} collections and {@code .otf}
 * (CFF) fonts are rejected, because {@link TtfFont} parses the {@code glyf} table.
 */
public final class SystemFonts {
	private static final String[] PREFERRED_FILES = {
			"NotoSans-Regular.ttf", "NotoSansCJK-Regular.ttf", "DejaVuSans.ttf",
			"MiSans-Medium.ttf", "MiSans-Regular.ttf", "Roboto-Regular.ttf",
			"Inter-Regular.ttf", "SegoeUI.ttf", "segoeui.ttf", "arial.ttf", "Arial.ttf",
			"Helvetica.ttf", "LiberationSans-Regular.ttf", "Ubuntu-R.ttf"
	};

	private static final String[] FONT_DIRECTORIES = {
			"/run/current-system/sw/share/fonts", // NixOS
			"/usr/share/fonts",
			"/usr/local/share/fonts",
			"/system/fonts",                      // Android-ish layouts
			"/System/Library/Fonts",              // macOS
			"/Library/Fonts",
			"C:\\Windows\\Fonts"                  // Windows
	};

	private static final String[] USER_DIRECTORIES = {
			".local/share/fonts", ".fonts"
	};

	private SystemFonts() {
	}

	/** Returns a TrueType file to render with, or {@code null} when none can be found. */
	public static Path find() {
		Path explicit = fromPropertyOrEnvironment();

		if (explicit != null) {
			return explicit;
		}

		Path matched = fromFontconfig();

		if (matched != null) {
			return matched;
		}

		List<Path> directories = new ArrayList<>();

		for (String directory : FONT_DIRECTORIES) {
			directories.add(Paths.get(directory));
		}

		String home = System.getProperty("user.home");

		if (home != null && !home.isBlank()) {
			for (String relative : USER_DIRECTORIES) {
				directories.add(Paths.get(home, relative));
			}
		}

		Path preferred = findPreferred(directories);

		if (preferred != null) {
			return preferred;
		}

		return findAnyTrueType(directories);
	}

	private static Path fromPropertyOrEnvironment() {
		String property = System.getProperty("ohmyf3.font");

		if (property == null || property.isBlank()) {
			property = System.getenv("OHMYF3_FONT");
		}

		if (property == null || property.isBlank()) {
			return null;
		}

		Path path = Paths.get(property);

		if (isUsable(path)) {
			return path;
		}

		return null;
	}

	/** Asks fontconfig for the file behind a generic sans-serif family. */
	private static Path fromFontconfig() {
		try {
			Process process = new ProcessBuilder("fc-match", "-f", "%{file}", "sans")
					.redirectErrorStream(true)
					.start();

			String output = new String(process.getInputStream().readAllBytes()).trim();
			process.waitFor();

			if (output.isEmpty()) {
				return null;
			}

			Path path = Paths.get(output);
			return isUsable(path) ? path : null;
		} catch (IOException e) {
			return null; // no fontconfig on this system
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private static Path findPreferred(List<Path> directories) {
		for (String fileName : PREFERRED_FILES) {
			for (Path directory : directories) {
				if (!Files.isDirectory(directory)) {
					continue;
				}

				try (var stream = Files.walk(directory, 4)) {
					Path found = stream
							.filter(Files::isRegularFile)
							.filter(path -> path.getFileName().toString().equalsIgnoreCase(fileName))
							.findFirst()
							.orElse(null);

					if (found != null && isUsable(found)) {
						return found;
					}
				} catch (IOException | RuntimeException e) {
					// Unreadable directory: keep looking.
				}
			}
		}

		return null;
	}

	private static Path findAnyTrueType(List<Path> directories) {
		for (Path directory : directories) {
			if (!Files.isDirectory(directory)) {
				continue;
			}

			try (var stream = Files.walk(directory, 4)) {
				Path found = stream
						.filter(Files::isRegularFile)
						.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ttf"))
						.filter(SystemFonts::isUsable)
						.findFirst()
						.orElse(null);

				if (found != null) {
					return found;
				}
			} catch (IOException | RuntimeException e) {
				// Ignore and try the next directory.
			}
		}

		return null;
	}

	/** True when the file exists and starts with a plain TrueType {@code glyf} header. */
	public static boolean isUsable(Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			return false;
		}

		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

		if (name.endsWith(".ttc") || name.endsWith(".otf")) {
			return false; // collections and CFF outlines are not supported
		}

		try (var in = Files.newInputStream(path)) {
			byte[] header = in.readNBytes(4);

			if (header.length < 4) {
				return false;
			}

			long version = (long) (header[0] & 0xFF) << 24 | (header[1] & 0xFF) << 16
					| (header[2] & 0xFF) << 8 | header[3] & 0xFF;

			return version == 0x00010000L || version == 0x74727565L;
		} catch (IOException e) {
			return false;
		}
	}
}
