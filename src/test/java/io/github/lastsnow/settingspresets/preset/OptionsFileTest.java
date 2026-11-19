package io.github.lastsnow.settingspresets.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptionsFileTest {
	static String fixture() throws IOException {
		try (InputStream in = OptionsFileTest.class.getResourceAsStream("/options.txt")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void roundTripsRealOptionsFile() throws IOException {
		String original = fixture();
		LinkedHashMap<String, String> parsed = OptionsFile.parse(original.lines().toList());
		// Byte-for-byte, apart from line-ending normalisation.
		assertEquals(original.replace("\r\n", "\n"), OptionsFile.serialize(parsed));
	}

	@Test
	void splitsOnFirstColonOnly() {
		LinkedHashMap<String, String> parsed = OptionsFile.parse(List.of(
			"resourcePacks:[\"vanilla\",\"file/My: \\\"Pack\\\".zip\"]",
			"lastServer:example.org:25565",
			"empty:",
			"no colon here",
			":novalue"
		));
		assertEquals("[\"vanilla\",\"file/My: \\\"Pack\\\".zip\"]", parsed.get("resourcePacks"));
		assertEquals("example.org:25565", parsed.get("lastServer"));
		assertEquals("", parsed.get("empty"));
		assertEquals(3, parsed.size());
	}

	@Test
	void writesAtomicallyAndLeavesNoTempFile(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("options.txt");
		LinkedHashMap<String, String> map = new LinkedHashMap<>();
		map.put("fov", "0.25");
		map.put("key_key.jump", "key.keyboard.space");
		OptionsFile.write(file, map);
		assertEquals(map, OptionsFile.read(file));
		assertFalse(Files.exists(dir.resolve("options.txt.tmp")));
		assertTrue(OptionsFile.read(dir.resolve("missing.txt")).isEmpty());
	}
}
