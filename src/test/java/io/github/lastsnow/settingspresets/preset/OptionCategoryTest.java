package io.github.lastsnow.settingspresets.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OptionCategoryTest {
	@Test
	void everyFixtureKeyIsCategorizedOrExcluded() throws IOException {
		Map<String, String> options = OptionsFile.parse(OptionsFileTest.fixture().lines().toList());
		assertTrue(options.size() > 100, "fixture looks too small: " + options.size());
		// Keys falling through to OTHER must be ones we deliberately put there; a new key in a
		// game update shows up here so it can be given a proper category.
		List<String> knownOther = List.of(
			"realmsNotifications", "hideServerAddress", "advancedItemTooltips", "pauseOnLostFocus",
			"overrideWidth", "overrideHeight", "useNativeTransport", "glDebugVerbosity", "syncChunkWrites",
			"allowServerListing", "inGameNotification", "sharePresence", "telemetryOptInExtra"
		);
		List<String> unexpected = new ArrayList<>();
		for (String key : options.keySet()) {
			if (!ExcludedKeys.isExcluded(key) && OptionCategory.categorize(key) == OptionCategory.OTHER && !knownOther.contains(key)) {
				unexpected.add(key);
			}
		}
		assertEquals(List.of(), unexpected, "keys without a deliberate category");
	}

	@Test
	void prefixesAndExactMatches() {
		assertEquals(OptionCategory.CONTROLS, OptionCategory.categorize("key_key.jump"));
		assertEquals(OptionCategory.CONTROLS, OptionCategory.categorize("key_key.somemod.dash"));
		assertEquals(OptionCategory.AUDIO, OptionCategory.categorize("soundCategory_master"));
		assertEquals(OptionCategory.SKIN, OptionCategory.categorize("modelPart_hat"));
		assertEquals(OptionCategory.CHAT, OptionCategory.categorize("chatScale"));
		assertEquals(OptionCategory.VIDEO, OptionCategory.categorize("renderDistance"));
		assertEquals(OptionCategory.PACKS_AND_LANGUAGE, OptionCategory.categorize("lang"));
		assertEquals(OptionCategory.OTHER, OptionCategory.categorize("somethingNewIn27"));
	}
}
