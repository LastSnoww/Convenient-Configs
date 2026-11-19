package io.github.lastsnow.settingspresets;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.lastsnow.settingspresets.gui.PresetsScreen;

/** Optional: only loaded by Mod Menu when it is installed. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return PresetsScreen::new;
	}
}
