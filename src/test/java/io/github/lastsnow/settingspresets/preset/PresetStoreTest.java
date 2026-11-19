package io.github.lastsnow.settingspresets.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresetStoreTest {
	@TempDir
	Path dir;

	private final AtomicLong time = new AtomicLong(1_700_000_000_000L);

	private PresetStore store() {
		// Each call to the clock advances one second so modification order is deterministic.
		return new PresetStore(dir, () -> Instant.ofEpochMilli(time.addAndGet(1000)));
	}

	private static Map<String, String> options() {
		LinkedHashMap<String, String> m = new LinkedHashMap<>();
		m.put("fov", "0.25");
		m.put("resourcePacks", "[\"vanilla\",\"file/a:b \\\"c\\\".zip\"]");
		return m;
	}

	@Test
	void slugify() {
		assertEquals("pvp", PresetStore.slugify("PvP"));
		assertEquals("low-end-laptop", PresetStore.slugify("  Low-end   laptop!! "));
		assertEquals("cafe-creme", PresetStore.slugify("Café Crème"));
		assertEquals("preset", PresetStore.slugify("日本語"));
		assertEquals("preset", PresetStore.slugify(""));
		assertEquals("preset", PresetStore.slugify(".."));
		assertEquals("a-b-c", PresetStore.slugify("a/b\\c"));
		assertEquals("con_", PresetStore.slugify("CON"));
		assertEquals("nul_", PresetStore.slugify("nul"));
		assertEquals("lpt1_", PresetStore.slugify("LPT1"));
		assertEquals(64, PresetStore.slugify("x".repeat(200)).length());
		assertTrue(PresetStore.slugify("..\\..\\etc/passwd").matches("[a-z0-9_-]+"));
	}

	@Test
	void slugCollisionsGetSuffixes() throws IOException {
		PresetStore store = store();
		Preset a = store.create("PvP", OptionCategory.all(), options(), 4554);
		Preset b = store.create("pvp!", OptionCategory.all(), options(), 4554);
		Preset c = store.create("PVP?", OptionCategory.all(), options(), 4554);
		assertEquals(List.of("pvp", "pvp-2", "pvp-3"), List.of(a.id(), b.id(), c.id()));

		String longName = "y".repeat(80);
		Preset l1 = store.create(longName, OptionCategory.all(), options(), 4554);
		Preset l2 = store.create(longName, OptionCategory.all(), options(), 4554);
		assertEquals(64, l1.id().length());
		assertEquals(64, l2.id().length());
		assertTrue(l2.id().endsWith("-2"));
	}

	@Test
	void createRenameDuplicateUpdateDeleteSurviveReload() throws IOException {
		PresetStore store = store();
		Preset pvp = store.create("PvP", EnumSet.of(OptionCategory.VIDEO), options(), 4554);
		Preset renamed = store.rename(pvp, "Ranked PvP");
		assertEquals(pvp.id(), renamed.id(), "rename keeps the file name");

		Preset copy = store.duplicate(renamed, store.uniqueName("Ranked PvP"));
		assertEquals("Ranked PvP (2)", copy.name());
		assertNotEquals(renamed.id(), copy.id());

		Preset updated = store.updateOptions(copy, Map.of("fov", "1.0"), 4600);
		assertEquals(EnumSet.of(OptionCategory.VIDEO), updated.categories(), "update keeps categories");

		PresetStore reopened = store();
		List<Preset> list = reopened.list();
		assertEquals(2, list.size());
		assertEquals(updated.id(), list.get(0).id(), "newest first");
		assertEquals("Ranked PvP", reopened.get(pvp.id()).orElseThrow().name());
		assertEquals(options(), reopened.get(pvp.id()).orElseThrow().options(), "values are kept as raw strings");
		assertEquals(Map.of("fov", "1.0"), reopened.get(copy.id()).orElseThrow().options());
		assertEquals(4600, reopened.get(copy.id()).orElseThrow().gameDataVersion());

		reopened.delete(list.get(0));
		assertEquals(1, store().list().size());
		assertTrue(reopened.findByName("ranked pvp", null).isPresent(), "name lookup ignores case");
	}

	@Test
	void corruptFilesAreSkipped() throws IOException {
		PresetStore store = store();
		store.create("Good", OptionCategory.all(), options(), 4554);
		Files.writeString(dir.resolve("broken.json"), "{ this is not json");
		Files.writeString(dir.resolve("wrongshape.json"), "[1,2,3]");
		Files.writeString(dir.resolve("noname.json"), "{\"options\":{}}");
		Files.writeString(dir.resolve("future.json"), "{\"formatVersion\":99,\"name\":\"x\",\"options\":{}}");
		List<Preset> list = store().refresh();
		assertEquals(1, list.size());
		assertEquals("Good", list.get(0).name());
	}

	@Test
	void jsonMatchesDocumentedFormat() throws IOException {
		PresetStore store = store();
		Preset p = store.create("PvP", EnumSet.of(OptionCategory.MOUSE, OptionCategory.VIDEO), options(), 4554);
		String json = Files.readString(dir.resolve(p.id() + ".json"));
		assertTrue(json.contains("\"formatVersion\": 1"));
		assertTrue(json.contains("\"name\": \"PvP\""));
		assertTrue(json.contains("\"gameDataVersion\": 4554"));
		assertTrue(json.contains("\"VIDEO\""));
		assertEquals(p.options(), PresetStore.fromJson("x", json).options());
	}

	@Test
	void backupsKeepLastFive(@TempDir Path game) throws IOException {
		PresetStore store = store();
		Path options = game.resolve("options.txt");
		Files.writeString(options, "fov:0.0\n");
		for (int i = 0; i < 8; i++) {
			store.backup(options);
		}
		try (Stream<Path> files = Files.list(store.backupDirectory())) {
			assertEquals(5, files.count());
		}
		assertFalse(Files.exists(dir.resolve("options.txt")));
	}
}
