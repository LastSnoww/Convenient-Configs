package io.github.lastsnow.settingspresets.preset;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The capture/merge rules for presets. Pure Java, no Minecraft classes. */
public final class OptionsMerge {
	private OptionsMerge() {
	}

	/** Keeps the keys that belong to one of {@code categories}, minus excluded keys. Order is preserved. */
	public static LinkedHashMap<String, String> select(Map<String, String> options, Collection<OptionCategory> categories) {
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		options.forEach((key, value) -> {
			if (!ExcludedKeys.isExcluded(key) && categories.contains(OptionCategory.categorize(key))) {
				result.put(key, value);
			}
		});
		return result;
	}

	/**
	 * Overlays the preset onto the current options. Only keys present in the preset change and
	 * excluded keys are never touched. Keys new to {@code current} are appended at the end.
	 */
	public static LinkedHashMap<String, String> merge(Map<String, String> current, Map<String, String> preset) {
		LinkedHashMap<String, String> result = new LinkedHashMap<>(current);
		preset.forEach((key, value) -> {
			if (!ExcludedKeys.isExcluded(key)) {
				result.put(key, value);
			}
		});
		return result;
	}

	/** Keys whose value differs between the two maps (including keys missing from one side). */
	public static Set<String> changedKeys(Map<String, String> before, Map<String, String> after) {
		Set<String> changed = new LinkedHashSet<>();
		for (Map.Entry<String, String> e : after.entrySet()) {
			if (!Objects.equals(before.get(e.getKey()), e.getValue())) {
				changed.add(e.getKey());
			}
		}
		for (String key : before.keySet()) {
			if (!after.containsKey(key)) {
				changed.add(key);
			}
		}
		return changed;
	}
}
