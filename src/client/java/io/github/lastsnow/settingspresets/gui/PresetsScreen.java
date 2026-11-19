package io.github.lastsnow.settingspresets.gui;

import com.mojang.blaze3d.Blaze3D;
import io.github.lastsnow.settingspresets.SettingsPresetsClient;
import io.github.lastsnow.settingspresets.preset.Preset;
import io.github.lastsnow.settingspresets.preset.PresetApplier;
import io.github.lastsnow.settingspresets.preset.PresetStore;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/** Main screen: list of presets plus Load / Save / Rename / Update / Duplicate / Delete. */
public class PresetsScreen extends Screen {
	private static final Component TITLE = Component.translatable("settingspresets.title");
	private static final Component EMPTY = Component.translatable("settingspresets.empty");
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();
	private static final int MAX_TOAST_KEYS = 4;

	private final @Nullable Screen lastScreen;
	private final PresetStore store;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33, 84);
	private @Nullable PresetList list;
	private @Nullable Button loadButton;
	private @Nullable Button renameButton;
	private @Nullable Button updateButton;
	private @Nullable Button duplicateButton;
	private @Nullable Button deleteButton;
	/** Survives re-init (window resize, GUI scale change after a load). */
	private @Nullable String selectedId;

	public PresetsScreen(@Nullable Screen lastScreen) {
		this(lastScreen, null);
	}

	/** Opens the screen with the given preset (by id) already selected. */
	public PresetsScreen(@Nullable Screen lastScreen, @Nullable String selectedId) {
		super(TITLE);
		this.lastScreen = lastScreen;
		this.store = SettingsPresetsClient.store();
		this.selectedId = selectedId;
	}

	@Override
	public void added() {
		super.added();
		// init() only runs the first time; when we come back from a dialog, refresh in place.
		this.store.refresh();
		if (this.list != null) {
			this.refreshList(this.selectedId);
		}
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(TITLE, this.font);

		this.list = this.layout.addToContents(new PresetList(this.minecraft, this.width, this.layout.getContentHeight(), this::onSelected, this::load));
		this.list.setPresets(this.store.list(), this.selectedId);

		GridLayout footer = this.layout.addToFooter(new GridLayout().columnSpacing(8).rowSpacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter();
		GridLayout.RowHelper rows = footer.createRowHelper(4);
		this.loadButton = rows.addChild(Button.builder(Component.translatable("settingspresets.button.load"), b -> this.withSelected(this::load)).build(), 2);
		rows.addChild(Button.builder(Component.translatable("settingspresets.button.save_new"), b -> this.openSaveNew()).build(), 2);
		this.renameButton = rows.addChild(Button.builder(Component.translatable("settingspresets.button.rename"), b -> this.withSelected(this::openRename)).width(71).build());
		this.updateButton = rows.addChild(
			Button.builder(Component.translatable("settingspresets.button.update"), b -> this.withSelected(this::confirmUpdate))
				.width(71)
				.tooltip(Tooltip.create(Component.translatable("settingspresets.button.update.tooltip")))
				.build()
		);
		this.duplicateButton = rows.addChild(Button.builder(Component.translatable("settingspresets.button.duplicate"), b -> this.withSelected(this::duplicate)).width(71).build());
		this.deleteButton = rows.addChild(Button.builder(Component.translatable("settingspresets.button.delete"), b -> this.withSelected(this::confirmDelete)).width(71).build());
		rows.addChild(
			Button.builder(Component.translatable("settingspresets.button.open_folder"), b -> this.openFolder())
				.tooltip(Tooltip.create(Component.translatable("settingspresets.button.open_folder.tooltip")))
				.build(),
			2
		);
		rows.addChild(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).build(), 2);

		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.updateButtonStatus();
	}

	@Override
	protected void repositionElements() {
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}
		this.layout.arrangeElements();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.list != null && this.list.isEmpty()) {
			List<FormattedCharSequence> lines = this.font.split(EMPTY, Math.min(300, this.width - 40));
			int y = this.layout.getHeaderHeight() + (this.layout.getContentHeight() - lines.size() * 12) / 2;
			for (FormattedCharSequence line : lines) {
				graphics.centeredText(this.font, line, this.width / 2, y, PresetList.SECONDARY_TEXT_COLOR);
				y += 12;
			}
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.lastScreen);
	}

	// ---------------------------------------------------------------- state

	private void onSelected(@Nullable Preset preset) {
		this.selectedId = preset == null ? null : preset.id();
		this.updateButtonStatus();
	}

	private void updateButtonStatus() {
		boolean hasSelection = this.list != null && this.list.getSelectedPreset() != null;
		for (Button button : new Button[]{this.loadButton, this.renameButton, this.updateButton, this.duplicateButton, this.deleteButton}) {
			if (button != null) {
				button.active = hasSelection;
			}
		}
	}

	private void withSelected(java.util.function.Consumer<Preset> action) {
		if (this.list != null && this.list.getSelectedPreset() != null) {
			action.accept(this.list.getSelectedPreset());
		}
	}

	/** Re-reads the cached list into the widget, keeping (or changing) the selection. */
	private void refreshList(@Nullable String selectId) {
		this.selectedId = selectId;
		if (this.list != null) {
			this.list.setPresets(this.store.list(), selectId);
		}
		this.updateButtonStatus();
	}

	/** Called by the edit screen before it returns here. */
	void onPresetSaved(Preset preset) {
		this.selectedId = preset.id();
	}

	// ---------------------------------------------------------------- actions

	private void load(Preset preset) {
		this.selectedId = preset.id();
		PresetApplier.Result result = PresetApplier.apply(preset, this.store);
		if (!result.success()) {
			showToast(Component.translatable("settingspresets.toast.load_failed", preset.name()), Component.translatable("settingspresets.toast.see_log"));
			return;
		}

		Component message = null;
		if (!result.missingPacks().isEmpty()) {
			message = Component.translatable("settingspresets.toast.missing_packs", summarize(result.missingPacks()));
		} else if (!result.notApplied().isEmpty()) {
			message = Component.translatable("settingspresets.toast.not_applied", summarize(result.notApplied()));
		}
		showToast(Component.translatable("settingspresets.toast.loaded", preset.name()), message);
	}

	private void openSaveNew() {
		this.minecraft.gui.setScreen(PresetEditScreen.create(this, this.store));
	}

	private void openRename(Preset preset) {
		this.minecraft.gui.setScreen(PresetEditScreen.rename(this, this.store, preset));
	}

	private void confirmUpdate(Preset preset) {
		this.minecraft.gui.setScreen(new ConfirmScreen(
			confirmed -> {
				if (confirmed) {
					try {
						Preset updated = this.store.updateOptions(preset, PresetApplier.captureCurrent(preset.categories()), PresetApplier.currentDataVersion());
						this.selectedId = updated.id();
						showToast(Component.translatable("settingspresets.toast.updated", updated.name()), null);
					} catch (IOException e) {
						this.ioError(e);
					}
				}
				this.minecraft.gui.setScreen(this);
			},
			Component.translatable("settingspresets.confirm.update.title"),
			Component.translatable("settingspresets.confirm.update.message", preset.name()),
			Component.translatable("settingspresets.button.update"),
			CommonComponents.GUI_CANCEL
		));
	}

	private void duplicate(Preset preset) {
		try {
			String name = this.store.uniqueName(Component.translatable("settingspresets.copy_name", preset.name()).getString());
			Preset copy = this.store.duplicate(preset, name);
			this.refreshList(copy.id());
		} catch (IOException e) {
			this.ioError(e);
		}
	}

	private void confirmDelete(Preset preset) {
		this.minecraft.gui.setScreen(new ConfirmScreen(
			confirmed -> {
				if (confirmed) {
					try {
						this.store.delete(preset);
						this.selectedId = null;
					} catch (IOException e) {
						this.ioError(e);
					}
				}
				this.minecraft.gui.setScreen(this);
			},
			Component.translatable("settingspresets.confirm.delete.title"),
			Component.translatable("settingspresets.confirm.delete.message", preset.name()),
			Component.translatable("settingspresets.button.delete"),
			CommonComponents.GUI_CANCEL
		));
	}

	private void openFolder() {
		try {
			Files.createDirectories(this.store.directory());
		} catch (IOException e) {
			this.ioError(e);
			return;
		}
		Blaze3D.openPath(this.store.directory());
	}

	// ---------------------------------------------------------------- helpers

	private void ioError(IOException e) {
		SettingsPresetsClient.LOGGER.error("Settings Presets file operation failed", e);
		showToast(Component.translatable("settingspresets.toast.io_error").withStyle(ChatFormatting.RED), Component.translatable("settingspresets.toast.see_log"));
	}

	static void showToast(Component title, @Nullable Component message) {
		SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), TOAST, title, message);
	}

	private static String summarize(List<String> items) {
		if (items.size() <= MAX_TOAST_KEYS) {
			return String.join(", ", items);
		}
		return String.join(", ", items.subList(0, MAX_TOAST_KEYS)) + ", … (+" + (items.size() - MAX_TOAST_KEYS) + ")";
	}
}
