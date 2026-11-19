package io.github.lastsnow.settingspresets;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.lastsnow.settingspresets.gui.PresetsScreen;
import io.github.lastsnow.settingspresets.preset.PresetStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SettingsPresetsClient implements ClientModInitializer {
	public static final String MOD_ID = "settingspresets";
	public static final Logger LOGGER = LoggerFactory.getLogger("SettingsPresets");

	/** Vanilla sprite; the mod ships no custom textures. */
	private static final Identifier BUTTON_SPRITE = Identifier.withDefaultNamespace("icon/news");
	private static final Component BUTTON_LABEL = Component.translatable("settingspresets.title");

	private static PresetStore store;
	private static KeyMapping openKey;

	public static PresetStore store() {
		if (store == null) {
			store = new PresetStore(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));
		}
		return store;
	}

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.settingspresets.open", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category)
		);

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (openKey.consumeClick()) {
				if (minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new PresetsScreen(null));
				}
			}
		});

		// Entry point on the vanilla Options screen: a 20x20 icon button in the top-left corner,
		// clear of the centred header (title + FOV/Online row start at y >= 12 + 9 + 8).
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof OptionsScreen) {
				SpriteIconButton button = SpriteIconButton.builder(BUTTON_LABEL, b -> minecraft.gui.setScreen(new PresetsScreen(screen)), true)
					.size(20, 20)
					.sprite(BUTTON_SPRITE, 14, 14)
					.build();
				button.setPosition(4, 4);
				button.setTooltip(Tooltip.create(BUTTON_LABEL));
				Screens.getWidgets(screen).add(button);
			}
		});
	}
}
