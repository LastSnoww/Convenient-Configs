package io.github.lastsnow.settingspresets.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptionsMergeTest {
	private static LinkedHashMap<String, String> current() {
		LinkedHashMap<String, String> m = new LinkedHashMap<>();
		m.put("version", "4554");
		m.put("fov", "0.0");
		m.put("renderDistance", "12");
		m.put("key_key.jump", "key.keyboard.space");
		m.put("soundCategory_master", "1.0");
		m.put("lastServer", "example.org");
		m.put("tutorialStep", "none");
		return m;
	}

	@Test
	void mergeOnlyTouchesPresetKeys() {
		Map<String, String> preset = Map.of("soundCategory_master", "0.5");
		LinkedHashMap<String, String> merged = OptionsMerge.merge(current(), preset);
		assertEquals("0.5", merged.get("soundCategory_master"));
		assertEquals("key.keyboard.space", merged.get("key_key.jump"));
		assertEquals("12", merged.get("renderDistance"));
		assertEquals(List.copyOf(current().keySet()), List.copyOf(merged.keySet()), "order preserved");
		assertEquals(Set.of("soundCategory_master"), OptionsMerge.changedKeys(current(), merged));
	}

	@Test
	void mergeNeverTouchesExcludedKeys() {
		Map<String, String> preset = Map.of("version", "1", "lastServer", "evil.example", "tutorialStep", "movement", "fov", "1.0");
		LinkedHashMap<String, String> merged = OptionsMerge.merge(current(), preset);
		assertEquals("4554", merged.get("version"));
		assertEquals("example.org", merged.get("lastServer"));
		assertEquals("none", merged.get("tutorialStep"));
		assertEquals("1.0", merged.get("fov"));
	}

	@Test
	void selectFiltersByCategoryAndDropsExcluded() {
		LinkedHashMap<String, String> audio = OptionsMerge.select(current(), EnumSet.of(OptionCategory.AUDIO));
		assertEquals(Map.of("soundCategory_master", "1.0"), audio);

		LinkedHashMap<String, String> all = OptionsMerge.select(current(), OptionCategory.all());
		assertFalse(all.containsKey("version"));
		assertFalse(all.containsKey("lastServer"));
		assertFalse(all.containsKey("tutorialStep"));
		assertTrue(all.containsKey("key_key.jump"));
	}
}
