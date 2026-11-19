package io.github.lastsnow.settingspresets.preset;

import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import io.github.lastsnow.settingspresets.SettingsPresetsClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;

/**
 * Captures the live settings into a preset and applies a preset to the running game.
 *
 * <p>Notes from the 26.3 sources: while the game is running, {@code OptionInstance#set} fires each
 * option's value-change callback, so {@code Options#load()} already resizes the GUI, updates sound
 * volumes, vsync, framerate limit, fullscreen, narrator, fonts and level-renderer state, and it
 * rebuilds the key lookup with {@code KeyMapping.resetMapping()}. {@code Options#save()} re-sends
 * skin parts/main hand/chat settings to the server. What is left for us is language, resource
 * packs, texture mipmaps/filtering and the exclusive fullscreen video mode.
 */
public final class PresetApplier {
	private static final Set<String> TEXTURE_KEYS = Set.of("mipmapLevels", "maxAnisotropyBit", "textureFiltering");
	private static final Set<String> PACK_KEYS = Set.of("resourcePacks", "incompatibleResourcePacks");

	public record Result(boolean success, List<String> missingPacks, List<String> notApplied) {
	}

	private PresetApplier() {
	}

	public static int currentDataVersion() {
		return SharedConstants.getCurrentVersion().dataVersion().version();
	}

	private static Path optionsPath(Minecraft minecraft) {
		return minecraft.gameDirectory.toPath().resolve("options.txt");
	}

	/** Writes the live settings to options.txt and returns the keys of the given categories. */
	public static LinkedHashMap<String, String> captureCurrent(Collection<OptionCategory> categories) throws IOException {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.options.save();
		return OptionsMerge.select(OptionsFile.read(optionsPath(minecraft)), categories);
	}

	public static Result apply(Preset preset, PresetStore store) {
		Minecraft minecraft = Minecraft.getInstance();
		Options options = minecraft.options;
		Path optionsPath = optionsPath(minecraft);
		Path backup = null;
		try {
			// 1-2. Make options.txt current and read it.
			options.save();
			LinkedHashMap<String, String> current = OptionsFile.read(optionsPath);

			// 3. Overlay the (upgraded) preset.
			Map<String, String> presetOptions = upgrade(preset, minecraft);
			LinkedHashMap<String, String> merged = OptionsMerge.merge(current, presetOptions);
			Set<String> changed = OptionsMerge.changedKeys(current, merged);
			if (changed.isEmpty()) {
				return new Result(true, List.of(), List.of());
			}

			// Safety: keep a copy of the current file before touching it.
			backup = store.backup(optionsPath);

			String oldLanguage = options.languageCode;
			List<String> oldPacks = List.copyOf(options.resourcePacks);
			List<String> oldIncompatible = List.copyOf(options.incompatibleResourcePacks);

			// 4. Write and load. OptionInstance callbacks run the per-option side effects here.
			OptionsFile.write(optionsPath, merged);
			options.load();

			// 5. Side effects that load() does not cover. Resources reload at most once.
			boolean reloadResources = false;
			List<String> missingPacks = new ArrayList<>();

			if (!Objects.equals(oldLanguage, options.languageCode)) {
				minecraft.getLanguageManager().setSelected(options.languageCode);
				reloadResources = true;
			}

			if (!oldPacks.equals(options.resourcePacks) || !oldIncompatible.equals(options.incompatibleResourcePacks)) {
				List<String> requested = List.copyOf(options.resourcePacks);
				// Drops packs that no longer exist (or became incompatible) and selects the rest.
				options.loadSelectedResourcePacks(minecraft.getResourcePackRepository());
				for (String id : requested) {
					if (!options.resourcePacks.contains(id)) {
						missingPacks.add(id);
					}
				}
				reloadResources = true;
			}

			if (changed.stream().anyMatch(TEXTURE_KEYS::contains)) {
				minecraft.updateMaxMipLevel(options.mipmapLevels().get());
				if (!reloadResources) {
					minecraft.delayTextureReload();
				}
			}

			if (changed.contains("fullscreenResolution")) {
				Window window = minecraft.getWindow();
				window.setPreferredFullscreenVideoMode(VideoMode.read(merged.get("fullscreenResolution")));
				window.changeFullscreenVideoMode();
			}

			if (reloadResources) {
				minecraft.reloadResourcePacks();
			}

			// 6. Persist the result (this also broadcasts client information to the server).
			options.save();

			// Report preset keys whose value did not survive the round trip (invalid values,
			// keybinds of mods that aren't installed, ...).
			LinkedHashMap<String, String> after = OptionsFile.read(optionsPath);
			List<String> notApplied = new ArrayList<>();
			for (Map.Entry<String, String> e : presetOptions.entrySet()) {
				if (!ExcludedKeys.isExcluded(e.getKey()) && !PACK_KEYS.contains(e.getKey())
					&& !Objects.equals(after.get(e.getKey()), e.getValue())) {
					notApplied.add(e.getKey());
				}
			}
			if (!missingPacks.isEmpty()) {
				SettingsPresetsClient.LOGGER.warn("Preset \"{}\": resource packs not available: {}", preset.name(), missingPacks);
			}
			if (!notApplied.isEmpty()) {
				SettingsPresetsClient.LOGGER.info("Preset \"{}\": options not applied as stored: {}", preset.name(), notApplied);
			}
			return new Result(true, missingPacks, notApplied);
		} catch (Exception e) {
			SettingsPresetsClient.LOGGER.error("Failed to apply preset \"{}\"", preset.name(), e);
			if (backup != null) {
				try {
					Files.copy(backup, optionsPath, StandardCopyOption.REPLACE_EXISTING);
					options.load();
				} catch (Exception restoreError) {
					SettingsPresetsClient.LOGGER.error("Failed to restore options.txt from {}", backup, restoreError);
				}
			}
			return new Result(false, List.of(), List.of());
		}
	}

	/**
	 * Presets saved on an older game version are run through the vanilla options data fixer, so
	 * renamed keys and changed value formats are upgraded before they are merged.
	 */
	private static Map<String, String> upgrade(Preset preset, Minecraft minecraft) {
		int current = currentDataVersion();
		int from = preset.gameDataVersion();
		if (from <= 0 || from >= current) {
			if (from > current) {
				SettingsPresetsClient.LOGGER.info("Preset \"{}\" was saved by a newer game version ({} > {})", preset.name(), from, current);
			}
			return preset.options();
		}
		SettingsPresetsClient.LOGGER.info("Upgrading preset \"{}\" from data version {} to {}", preset.name(), from, current);
		CompoundTag tag = new CompoundTag();
		preset.options().forEach(tag::putString);
		CompoundTag fixed = DataFixTypes.OPTIONS.updateToCurrentVersion(minecraft.getFixerUpper(), tag, from);
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		for (String key : fixed.keySet()) {
			fixed.getString(key).ifPresent(value -> result.put(key, value));
		}
		// Fixers may add unrelated keys; keep only the preset's categories.
		return OptionsMerge.select(result, preset.categories());
	}
}
