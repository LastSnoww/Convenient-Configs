package io.github.lastsnow.settingspresets.preset;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A named preset. {@code id} is the file slug (file name without {@code .json}) and is not stored in
 * the file; the display name lives inside the file so renaming never touches the file name.
 */
public record Preset(
	String id,
	String name,
	Instant createdAt,
	Instant modifiedAt,
	int gameDataVersion,
	Set<OptionCategory> categories,
	Map<String, String> options
) {
	public static final int FORMAT_VERSION = 1;

	public Preset {
		categories = categories.isEmpty()
			? Collections.unmodifiableSet(EnumSet.noneOf(OptionCategory.class))
			: Collections.unmodifiableSet(EnumSet.copyOf(categories));
		options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
	}

	public Preset withName(String newName, Instant now) {
		return new Preset(id, newName, createdAt, now, gameDataVersion, categories, options);
	}

	public Preset withOptions(Map<String, String> newOptions, int dataVersion, Instant now) {
		return new Preset(id, name, createdAt, now, dataVersion, categories, newOptions);
	}

	public Preset withId(String newId) {
		return new Preset(newId, name, createdAt, modifiedAt, gameDataVersion, categories, options);
	}

	public boolean hasAllCategories() {
		return categories.size() == OptionCategory.values().length;
	}
}
