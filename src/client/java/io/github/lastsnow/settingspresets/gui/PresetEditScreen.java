package io.github.lastsnow.settingspresets.gui;

import io.github.lastsnow.settingspresets.SettingsPresetsClient;
import io.github.lastsnow.settingspresets.preset.OptionCategory;
import io.github.lastsnow.settingspresets.preset.Preset;
import io.github.lastsnow.settingspresets.preset.PresetApplier;
import io.github.lastsnow.settingspresets.preset.PresetStore;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** "Save current as new" (name + categories) and "Rename" (name only) dialog. */
public class PresetEditScreen extends Screen {
	private static final int MAX_NAME_LENGTH = 48;
	private static final int MAX_COLUMN_WIDTH = 120;
	private static final int CHECKBOX_SPACING = 6;

	private final PresetsScreen parent;
	private final PresetStore store;
	/** The preset being renamed, or null when saving a new preset. */
	private final @Nullable Preset renaming;
	private final Set<OptionCategory> selectedCategories = EnumSet.allOf(OptionCategory.class);
	private String name;
	private boolean overwriteArmed;

	// Rebuilt by init(); All/None rebuilds the widgets because Checkbox has no setter.
	private HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private @Nullable EditBox nameBox;
	private @Nullable StringWidget warningText;
	private @Nullable Button confirmButton;

	private PresetEditScreen(Component title, PresetsScreen parent, PresetStore store, @Nullable Preset renaming, String initialName) {
		super(title);
		this.parent = parent;
		this.store = store;
		this.renaming = renaming;
		this.name = initialName;
	}

	public static PresetEditScreen create(PresetsScreen parent, PresetStore store) {
		return new PresetEditScreen(Component.translatable("settingspresets.edit.save_title"), parent, store, null, "");
	}

	public static PresetEditScreen rename(PresetsScreen parent, PresetStore store, Preset preset) {
		return new PresetEditScreen(Component.translatable("settingspresets.edit.rename_title"), parent, store, preset, preset.name());
	}

	private boolean isRename() {
		return this.renaming != null;
	}

	@Override
	protected void init() {
		this.layout = new HeaderAndFooterLayout(this, 33, 33);
		this.layout.addTitleHeader(this.title, this.font);

		LinearLayout contents = this.layout.addToContents(LinearLayout.vertical().spacing(4));
		contents.defaultCellSetting().alignHorizontallyCenter();

		// Label sits left-aligned above the box, like vanilla's world name field.
		LinearLayout nameField = contents.addChild(LinearLayout.vertical().spacing(4));
		nameField.addChild(new StringWidget(Component.translatable("settingspresets.edit.name"), this.font).setMaxWidth(200), LayoutSettings::alignHorizontallyLeft);
		this.nameBox = nameField.addChild(new EditBox(this.font, 200, 20, Component.translatable("settingspresets.edit.name")));
		this.nameBox.setMaxLength(MAX_NAME_LENGTH);
		this.nameBox.setValue(this.name);
		this.nameBox.setResponder(value -> {
			this.name = value;
			this.overwriteArmed = false;
			this.updateState();
		});
		this.warningText = contents.addChild(new StringWidget(Component.empty(), this.font).setMaxWidth(310));

		if (!this.isRename()) {
			contents.addChild(new StringWidget(Component.translatable("settingspresets.edit.categories"), this.font));
			GridLayout grid = contents.addChild(new GridLayout().columnSpacing(CHECKBOX_SPACING).rowSpacing(4));
			GridLayout.RowHelper rows = grid.createRowHelper(3);
			int columnWidth = this.checkboxColumnWidth();
			for (OptionCategory category : OptionCategory.values()) {
				// Vanilla Checkbox limits its label to the full widget width but draws it after the box,
				// so subtract the box to keep long (translated) labels inside the column; they wrap instead.
				Checkbox checkbox = Checkbox.builder(Component.translatable(category.translationKey()), this.font)
					.selected(this.selectedCategories.contains(category))
					.maxWidth(columnWidth - Checkbox.getBoxSize(this.font) - 4)
					.onValueChange((box, value) -> {
						if (value) {
							this.selectedCategories.add(category);
						} else {
							this.selectedCategories.remove(category);
						}
						this.updateState();
					})
					.build();
				FrameLayout cell = rows.addChild(new FrameLayout(columnWidth, 0));
				cell.addChild(checkbox, LayoutSettings::alignHorizontallyLeft);
			}
			LinearLayout allNone = contents.addChild(LinearLayout.horizontal().spacing(8));
			allNone.addChild(Button.builder(Component.translatable("settingspresets.edit.all"), b -> this.setAllCategories(true)).width(100).build());
			allNone.addChild(Button.builder(Component.translatable("settingspresets.edit.none"), b -> this.setAllCategories(false)).width(100).build());
		}

		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		this.confirmButton = footer.addChild(Button.builder(this.confirmLabel(), b -> this.onConfirm()).width(150).build());
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose()).width(150).build());

		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.updateState();
	}

	@Override
	protected void setInitialFocus() {
		if (this.nameBox != null) {
			this.setInitialFocus(this.nameBox);
		}
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation() && this.getFocused() == this.nameBox && this.confirmButton != null && this.confirmButton.active) {
			this.onConfirm();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	/** Widest label (box included), limited so three columns fit the screen and stay near vanilla widths. */
	private int checkboxColumnWidth() {
		int widest = 0;
		for (OptionCategory category : OptionCategory.values()) {
			widest = Math.max(widest, Checkbox.getBoxSize(this.font) + 4 + this.font.width(Component.translatable(category.translationKey())));
		}
		int fitting = (this.width - 20 - 2 * CHECKBOX_SPACING) / 3;
		return Math.max(60, Math.min(widest, Math.min(MAX_COLUMN_WIDTH, fitting)));
	}

	private void setAllCategories(boolean selected) {
		if (selected) {
			this.selectedCategories.addAll(EnumSet.allOf(OptionCategory.class));
		} else {
			this.selectedCategories.clear();
		}
		this.rebuildWidgets();
	}

	private Optional<Preset> conflict() {
		return this.store.findByName(this.name, this.renaming == null ? null : this.renaming.id());
	}

	private Component confirmLabel() {
		if (this.overwriteArmed) {
			return Component.translatable("settingspresets.edit.overwrite");
		}
		return this.isRename() ? Component.translatable("settingspresets.button.rename") : Component.translatable("settingspresets.edit.save");
	}

	private void updateState() {
		boolean blank = this.name.isBlank();
		Optional<Preset> conflict = blank ? Optional.empty() : this.conflict();
		if (this.confirmButton != null) {
			this.confirmButton.active = !blank && (this.isRename() || !this.selectedCategories.isEmpty());
			this.confirmButton.setMessage(this.confirmLabel());
		}
		if (this.warningText != null) {
			this.warningText.setMessage(conflict
				.<Component>map(p -> Component.translatable("settingspresets.edit.exists", p.name()).withStyle(ChatFormatting.RED))
				.orElse(Component.empty()));
			this.layout.arrangeElements();
		}
	}

	private void onConfirm() {
		if (this.name.isBlank() || (!this.isRename() && this.selectedCategories.isEmpty())) {
			return;
		}
		Optional<Preset> conflict = this.conflict();
		if (conflict.isPresent() && !this.overwriteArmed) {
			// First click only arms the overwrite; the button now reads "Overwrite?".
			this.overwriteArmed = true;
			this.updateState();
			return;
		}

		try {
			Preset result;
			if (this.renaming != null) {
				if (conflict.isPresent()) {
					this.store.delete(conflict.get());
				}
				result = this.store.rename(this.renaming, this.name);
			} else {
				var options = PresetApplier.captureCurrent(this.selectedCategories);
				int dataVersion = PresetApplier.currentDataVersion();
				result = conflict.isPresent()
					? this.store.overwrite(conflict.get(), this.name, this.selectedCategories, options, dataVersion)
					: this.store.create(this.name, this.selectedCategories, options, dataVersion);
				PresetsScreen.showToast(Component.translatable("settingspresets.toast.saved", result.name()), null);
			}
			this.parent.onPresetSaved(result);
		} catch (IOException e) {
			SettingsPresetsClient.LOGGER.error("Could not save preset \"{}\"", this.name, e);
			PresetsScreen.showToast(
				Component.translatable("settingspresets.toast.io_error").withStyle(ChatFormatting.RED),
				Component.translatable("settingspresets.toast.see_log")
			);
		}
		this.onClose();
	}
}
