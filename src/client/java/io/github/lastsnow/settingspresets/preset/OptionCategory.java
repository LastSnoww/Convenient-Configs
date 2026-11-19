package io.github.lastsnow.settingspresets.preset;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Groups options.txt keys into user-facing categories. Exact key matches win over prefix matches;
 * anything unknown lands in {@link #OTHER} so no key is ever silently dropped.
 * Key list checked against {@code Options#processOptions} for Minecraft 26.3.
 */
public enum OptionCategory {
	VIDEO,
	CONTROLS,
	MOUSE,
	AUDIO,
	CHAT,
	ACCESSIBILITY,
	SKIN,
	PACKS_AND_LANGUAGE,
	OTHER;

	private static final Map<String, OptionCategory> EXACT = Map.<String, OptionCategory>ofEntries(
		// Controls
		Map.entry("autoJump", CONTROLS),
		Map.entry("rotateWithMinecart", CONTROLS),
		Map.entry("operatorItemsTab", CONTROLS),
		Map.entry("toggleCrouch", CONTROLS),
		Map.entry("toggleSprint", CONTROLS),
		Map.entry("toggleAttack", CONTROLS),
		Map.entry("toggleUse", CONTROLS),
		Map.entry("sprintWindow", CONTROLS),
		Map.entry("quitShortcuts", CONTROLS),
		// Mouse
		Map.entry("mouseSensitivity", MOUSE),
		Map.entry("invertXMouse", MOUSE),
		Map.entry("invertYMouse", MOUSE),
		Map.entry("rawMouseInput", MOUSE),
		Map.entry("mouseWheelSensitivity", MOUSE),
		Map.entry("discrete_mouse_scroll", MOUSE),
		Map.entry("touchscreen", MOUSE),
		Map.entry("allowCursorChanges", MOUSE),
		Map.entry("ctrlClickEmulatesRightClick", MOUSE),
		// Video
		Map.entry("renderDistance", VIDEO),
		Map.entry("simulationDistance", VIDEO),
		Map.entry("graphicsPreset", VIDEO),
		Map.entry("graphicsMode", VIDEO),
		Map.entry("preferredGraphicsBackend", VIDEO),
		Map.entry("maxFps", VIDEO),
		Map.entry("inactivityFpsLimit", VIDEO),
		Map.entry("enableVsync", VIDEO),
		Map.entry("fov", VIDEO),
		Map.entry("fovEffectScale", VIDEO),
		Map.entry("gamma", VIDEO),
		Map.entry("guiScale", VIDEO),
		Map.entry("debugGuiScale", VIDEO),
		Map.entry("fullscreen", VIDEO),
		Map.entry("exclusiveFullscreen", VIDEO),
		Map.entry("fullscreenResolution", VIDEO),
		Map.entry("macFullscreenMenuVisibility", VIDEO),
		Map.entry("bobView", VIDEO),
		Map.entry("entityShadows", VIDEO),
		Map.entry("mipmapLevels", VIDEO),
		Map.entry("maxAnisotropyBit", VIDEO),
		Map.entry("textureFiltering", VIDEO),
		Map.entry("biomeBlendRadius", VIDEO),
		Map.entry("renderClouds", VIDEO),
		Map.entry("cloudRange", VIDEO),
		Map.entry("weatherRadius", VIDEO),
		Map.entry("particles", VIDEO),
		Map.entry("entityDistanceScaling", VIDEO),
		Map.entry("prioritizeChunkUpdates", VIDEO),
		Map.entry("chunkSectionFadeInTime", VIDEO),
		Map.entry("cutoutLeaves", VIDEO),
		Map.entry("improvedTransparency", VIDEO),
		Map.entry("vignette", VIDEO),
		Map.entry("ao", VIDEO),
		Map.entry("screenEffectScale", VIDEO),
		Map.entry("attackIndicator", VIDEO),
		Map.entry("menuBackgroundBlurriness", VIDEO),
		Map.entry("showAutosaveIndicator", VIDEO),
		// Audio
		Map.entry("soundDevice", AUDIO),
		Map.entry("showSubtitles", AUDIO),
		Map.entry("directionalAudio", AUDIO),
		Map.entry("musicToast", AUDIO),
		Map.entry("musicFrequency", AUDIO),
		// Chat (chat* prefix covers the rest)
		Map.entry("textBackgroundOpacity", CHAT),
		Map.entry("backgroundForChatOnly", CHAT),
		Map.entry("reducedDebugInfo", CHAT),
		Map.entry("hideMatchedNames", CHAT),
		Map.entry("onlyShowSecureChat", CHAT),
		Map.entry("autoSuggestions", CHAT),
		Map.entry("saveChatDrafts", CHAT),
		// Accessibility
		Map.entry("narrator", ACCESSIBILITY),
		Map.entry("narratorHotkey", ACCESSIBILITY),
		Map.entry("highContrast", ACCESSIBILITY),
		Map.entry("highContrastBlockOutline", ACCESSIBILITY),
		Map.entry("darkMojangStudiosBackground", ACCESSIBILITY),
		Map.entry("hideLightningFlashes", ACCESSIBILITY),
		Map.entry("hideSplashTexts", ACCESSIBILITY),
		Map.entry("damageTiltStrength", ACCESSIBILITY),
		Map.entry("darknessEffectScale", ACCESSIBILITY),
		Map.entry("glintSpeed", ACCESSIBILITY),
		Map.entry("glintStrength", ACCESSIBILITY),
		Map.entry("panoramaScrollSpeed", ACCESSIBILITY),
		Map.entry("notificationDisplayTime", ACCESSIBILITY),
		Map.entry("forceUnicodeFont", ACCESSIBILITY),
		Map.entry("japaneseGlyphVariants", ACCESSIBILITY),
		// Skin
		Map.entry("mainHand", SKIN),
		// Resource packs & language
		Map.entry("resourcePacks", PACKS_AND_LANGUAGE),
		Map.entry("incompatibleResourcePacks", PACKS_AND_LANGUAGE),
		Map.entry("lang", PACKS_AND_LANGUAGE)
	);

	public static OptionCategory categorize(String key) {
		OptionCategory exact = EXACT.get(key);
		if (exact != null) {
			return exact;
		}
		if (key.startsWith("key_")) {
			return CONTROLS;
		}
		if (key.startsWith("soundCategory_")) {
			return AUDIO;
		}
		if (key.startsWith("modelPart_")) {
			return SKIN;
		}
		if (key.startsWith("chat")) {
			return CHAT;
		}
		return OTHER;
	}

	public static Set<OptionCategory> all() {
		return EnumSet.allOf(OptionCategory.class);
	}

	public String translationKey() {
		return "settingspresets.category." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
