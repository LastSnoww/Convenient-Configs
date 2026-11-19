package io.github.lastsnow.settingspresets.preset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes vanilla {@code options.txt}: one {@code key:value} pair per line, split on the
 * first colon only (values such as resource pack lists are JSON and can contain colons).
 * Pure Java, no Minecraft classes.
 */
public final class OptionsFile {
	private OptionsFile() {
	}

	public static LinkedHashMap<String, String> parse(List<String> lines) {
		LinkedHashMap<String, String> map = new LinkedHashMap<>();
		for (String line : lines) {
			int colon = line.indexOf(':');
			// Same rule as vanilla Options.load(): lines without a colon are skipped.
			if (colon <= 0) {
				continue;
			}
			map.put(line.substring(0, colon), line.substring(colon + 1));
		}
		return map;
	}

	public static LinkedHashMap<String, String> read(Path file) throws IOException {
		if (!Files.exists(file)) {
			return new LinkedHashMap<>();
		}
		return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
	}

	public static String serialize(Map<String, String> options) {
		StringBuilder sb = new StringBuilder();
		options.forEach((key, value) -> sb.append(key).append(':').append(value).append('\n'));
		return sb.toString();
	}

	public static void write(Path file, Map<String, String> options) throws IOException {
		writeAtomically(file, serialize(options));
	}

	/** Writes to {@code <file>.tmp} and moves it over the target so readers never see a half-written file. */
	public static void writeAtomically(Path file, String content) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, content, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
