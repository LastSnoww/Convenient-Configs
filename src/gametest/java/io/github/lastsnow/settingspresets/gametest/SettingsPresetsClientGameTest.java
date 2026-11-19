package io.github.lastsnow.settingspresets.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.lastsnow.settingspresets.SettingsPresetsClient;
import io.github.lastsnow.settingspresets.gui.PresetEditScreen;
import io.github.lastsnow.settingspresets.gui.PresetsScreen;
import io.github.lastsnow.settingspresets.preset.OptionCategory;
import io.github.lastsnow.settingspresets.preset.Preset;
import io.github.lastsnow.settingspresets.preset.PresetApplier;
import io.github.lastsnow.settingspresets.preset.PresetStore;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.sounds.SoundSource;

/**
 * End-to-end checks in a real client (./gradlew runClientGameTest). Covers the manual checklist
 * items that can be automated and saves screenshots to build/run/clientGameTest/screenshots.
 */
public class SettingsPresetsClientGameTest implements FabricClientGameTest {
	private static final InputConstants.Key KEY_SPACE = InputConstants.getKey("key.keyboard.space");
	private static final InputConstants.Key KEY_J = InputConstants.getKey("key.keyboard.j");
	private static final InputConstants.Key KEY_K = InputConstants.getKey("key.keyboard.k");

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try {
			run(ctx);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void run(ClientGameTestContext ctx) throws Exception {
		PresetStore store = SettingsPresetsClient.store();
		ctx.runOnClient(mc -> store.refresh().forEach(p -> unchecked(() -> store.delete(p))));

		// --- Entry point and empty state -----------------------------------------------------
		ctx.setScreen(() -> new OptionsScreen(new TitleScreen(), Minecraft.getInstance().options));
		ctx.takeScreenshot("01-options-screen");
		ctx.setScreen(() -> new PresetsScreen(null));
		ctx.takeScreenshot("02-presets-empty");

		// --- Check 1: five settings apply immediately ---------------------------------------
		ctx.runOnClient(mc -> {
			Options o = mc.options;
			o.fov().set(95);
			o.renderDistance().set(6);
			o.guiScale().set(1);
			o.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.3);
			setJump(o, KEY_J);
			store.create("A", OptionCategory.all(), PresetApplier.captureCurrent(OptionCategory.all()), PresetApplier.currentDataVersion());
			store.create("Audio only", EnumSet.of(OptionCategory.AUDIO), PresetApplier.captureCurrent(EnumSet.of(OptionCategory.AUDIO)), PresetApplier.currentDataVersion());

			o.fov().set(70);
			o.renderDistance().set(12);
			o.guiScale().set(0);
			o.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0);
			setJump(o, KEY_SPACE);
			check(mc.getWindow().getGuiScale() != 1, "precondition: auto GUI scale should not be 1");
		});
		ctx.waitTick();
		ctx.runOnClient(mc -> {
			Options o = mc.options;
			PresetApplier.Result result = PresetApplier.apply(byName(store, "A"), store);
			check(result.success(), "apply A succeeded");
			check(o.fov().get() == 95, "fov applied, got " + o.fov().get());
			check(o.renderDistance().get() == 6, "render distance applied");
			check(o.guiScale().get() == 1 && mc.getWindow().getGuiScale() == 1, "GUI scale applied to the window, got " + mc.getWindow().getGuiScale());
			check(Math.abs(o.getSoundSourceVolume(SoundSource.MASTER) - 0.3f) < 1e-4, "master volume applied");
			check(o.keyJump.saveString().equals("key.keyboard.j"), "jump key stored as J");
			KeyMapping.set(KEY_J, true);
			check(o.keyJump.isDown(), "pressing J now triggers jump (key lookup rebuilt)");
			KeyMapping.releaseAll();
			KeyMapping.set(KEY_SPACE, true);
			check(!o.keyJump.isDown(), "space no longer triggers jump");
			KeyMapping.releaseAll();
			check(result.notApplied().isEmpty(), "all keys applied, but not: " + result.notApplied());
		});

		// --- Check 2: an Audio-only preset leaves keybinds and video alone --------------------
		ctx.runOnClient(mc -> {
			Options o = mc.options;
			o.fov().set(70);
			o.guiScale().set(0);
			setJump(o, KEY_K);
			o.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0);
			check(PresetApplier.apply(byName(store, "Audio only"), store).success(), "apply audio-only");
			check(Math.abs(o.getSoundSourceVolume(SoundSource.MASTER) - 0.3f) < 1e-4, "audio applied");
			check(o.fov().get() == 70, "fov untouched by audio-only preset");
			check(o.keyJump.saveString().equals("key.keyboard.k"), "keybind untouched by audio-only preset");
			o.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0);
			setJump(o, KEY_SPACE);
		});

		// --- Check 3: language preset switches the UI with one reload -------------------------
		ctx.runOnClient(mc -> {
			store.create("Deutsch", EnumSet.of(OptionCategory.PACKS_AND_LANGUAGE), Map.of("lang", "de_de"), PresetApplier.currentDataVersion());
			store.create("English", EnumSet.of(OptionCategory.PACKS_AND_LANGUAGE), Map.of("lang", "en_us"), PresetApplier.currentDataVersion());
			check(PresetApplier.apply(byName(store, "Deutsch"), store).success(), "apply Deutsch");
		});
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.runOnClient(mc -> check(mc.getLanguageManager().getSelected().equals("de_de"), "language switched to German"));
		ctx.setScreen(() -> new PresetsScreen(null, store.list().get(0).id()));
		ctx.takeScreenshot("03-presets-german");
		ctx.setScreen(() -> PresetEditScreen.create(new PresetsScreen(null), store));
		ctx.takeScreenshot("04-save-dialog-german");
		ctx.runOnClient(mc -> check(PresetApplier.apply(byName(store, "English"), store).success(), "apply English"));
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.runOnClient(mc -> check(mc.getLanguageManager().getSelected().equals("en_us"), "language back to English"));

		// --- Check 4: missing resource pack is dropped and reported -------------------------
		ctx.runOnClient(mc -> {
			store.create("Missing pack", EnumSet.of(OptionCategory.PACKS_AND_LANGUAGE),
				Map.of("resourcePacks", "[\"vanilla\",\"file/does-not-exist.zip\"]", "incompatibleResourcePacks", "[]"),
				PresetApplier.currentDataVersion());
			PresetApplier.Result result = PresetApplier.apply(byName(store, "Missing pack"), store);
			check(result.success(), "missing pack preset applies without crashing");
			check(result.missingPacks().equals(List.of("file/does-not-exist.zip")), "missing pack reported: " + result.missingPacks());
		});
		ctx.waitFor(mc -> mc.gui.overlay() == null);

		// --- Check 5 via the real UI: save, overwrite confirmation, delete --------------------
		ctx.setScreen(() -> new PresetsScreen(null));
		ctx.clickScreenButton("settingspresets.button.save_new");
		ctx.waitForScreen(PresetEditScreen.class);
		ctx.getInput().typeChars("Recording");
		ctx.takeScreenshot("05-save-dialog");
		ctx.clickScreenButton("settingspresets.edit.save");
		ctx.waitForScreen(PresetsScreen.class);
		ctx.runOnClient(mc -> check(store.findByName("Recording", null).isPresent(), "saved through the UI"));

		ctx.clickScreenButton("settingspresets.button.save_new");
		ctx.waitForScreen(PresetEditScreen.class);
		ctx.getInput().typeChars("recording");
		ctx.clickScreenButton("settingspresets.edit.save");
		ctx.waitTick();
		ctx.runOnClient(mc -> check(mc.gui.screen() instanceof PresetEditScreen, "duplicate name needs a second click"));
		ctx.takeScreenshot("06-save-dialog-overwrite");
		ctx.clickScreenButton("settingspresets.edit.overwrite");
		ctx.waitForScreen(PresetsScreen.class);
		ctx.runOnClient(mc -> check(store.list().stream().filter(p -> p.name().equalsIgnoreCase("recording")).count() == 1, "overwrite did not create a second preset"));

		String recordingId = ctx.computeOnClient(mc -> byName(store, "recording").id());
		ctx.setScreen(() -> new PresetsScreen(null, recordingId));
		ctx.takeScreenshot("07-presets-list");
		ctx.clickScreenButton("settingspresets.button.delete");
		ctx.waitForScreen(ConfirmScreen.class);
		ctx.takeScreenshot("08-delete-confirm");
		ctx.clickScreenButton("settingspresets.button.delete");
		ctx.waitForScreen(PresetsScreen.class);
		ctx.runOnClient(mc -> check(store.findByName("recording", null).isEmpty(), "deleted through the UI"));

		// --- Check 7: corrupt preset file does not break the screen ---------------------------
		ctx.runOnClient(mc -> {
			Files.writeString(store.directory().resolve("corrupt.json"), "{ not json");
			int before = store.list().size();
			check(store.refresh().size() == before, "corrupt file skipped");
		});
		ctx.setScreen(() -> new PresetsScreen(null));
		ctx.takeScreenshot("09-presets-with-corrupt-file");

		// --- Check 9: layout at GUI scales 1-4 and at minimum window size ---------------------
		ctx.getInput().resizeWindow(1280, 960);
		for (int scale = 1; scale <= 4; scale++) {
			int s = scale;
			ctx.runOnClient(mc -> mc.options.guiScale().set(s));
			ctx.setScreen(() -> new OptionsScreen(new TitleScreen(), Minecraft.getInstance().options));
			ctx.takeScreenshot("10-options-scale-" + scale);
			ctx.setScreen(() -> new PresetsScreen(null, store.list().get(0).id()));
			ctx.takeScreenshot("11-presets-scale-" + scale);
		}
		ctx.runOnClient(mc -> mc.options.guiScale().set(0));
		ctx.getInput().resizeWindow(320, 240);
		ctx.setScreen(() -> new OptionsScreen(new TitleScreen(), Minecraft.getInstance().options));
		ctx.takeScreenshot("12-options-min-window");
		ctx.setScreen(() -> new PresetsScreen(null, store.list().get(0).id()));
		ctx.takeScreenshot("13-presets-min-window");
		ctx.getInput().resizeWindow(854, 480);

		// --- Check 6: load while in a singleplayer world ---------------------------------------
		ctx.setScreen(() -> null);
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.runOnClient(mc -> {
				mc.options.fov().set(70);
				check(PresetApplier.apply(byName(store, "A"), store).success(), "apply in world");
				check(mc.options.fov().get() == 95, "fov applied in world");
			});
			ctx.setScreen(() -> new PresetsScreen(null, Objects.requireNonNull(byName(store, "A")).id()));
			ctx.takeScreenshot("14-presets-in-world");
			ctx.setScreen(() -> null);
		}
		ctx.runOnClient(mc -> store.refresh());
	}

	private static void setJump(Options options, InputConstants.Key key) {
		options.keyJump.setKey(key);
		KeyMapping.resetMapping();
		options.save();
	}

	private static Preset byName(PresetStore store, String name) {
		return store.findByName(name, null).orElseThrow(() -> new AssertionError("no preset named " + name));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private interface IoAction {
		void run() throws Exception;
	}

	private static void unchecked(IoAction action) {
		try {
			action.run();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
