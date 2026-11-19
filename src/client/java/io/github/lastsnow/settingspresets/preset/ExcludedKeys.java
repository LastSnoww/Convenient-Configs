package io.github.lastsnow.settingspresets.preset;

import java.util.Set;

/**
 * options.txt keys that are per-install state rather than preferences. They are never stored in a
 * preset and never overwritten when one is applied. Checked against {@code Options#processOptions}
 * for Minecraft 26.3.
 */
public final class ExcludedKeys {
	public static final Set<String> KEYS = Set.of(
		"version",               // data version, used by the game to upgrade the file
		"lastServer",            // last typed server address
		"tutorialStep",          // tutorial progress
		"joinedFirstServer",     // one-time social interactions hint
		"skipMultiplayerWarning",// one-time multiplayer warning
		"onboardAccessibility",  // first-launch accessibility onboarding
		"startedCleanly"         // crash detection flag
	);

	private ExcludedKeys() {
	}

	public static boolean isExcluded(String key) {
		return KEYS.contains(key);
	}
}
