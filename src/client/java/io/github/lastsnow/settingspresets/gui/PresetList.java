package io.github.lastsnow.settingspresets.gui;

import io.github.lastsnow.settingspresets.preset.OptionCategory;
import io.github.lastsnow.settingspresets.preset.Preset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/** Two-line preset list modelled on the vanilla world/server selection lists. */
public class PresetList extends ObjectSelectionList<PresetList.Entry> {
	static final int SECONDARY_TEXT_COLOR = 0xFFA0A0A0;
	private static final int ROW_WIDTH = 305;
	private static final int ITEM_HEIGHT = 28;
	private static final DateTimeFormatter DATE_FORMAT = Util.localizedDateFormatter(FormatStyle.SHORT);

	private final Consumer<@Nullable Preset> onSelect;
	private final Consumer<Preset> onInteract;

	public PresetList(Minecraft minecraft, int width, int height, Consumer<@Nullable Preset> onSelect, Consumer<Preset> onInteract) {
		super(minecraft, width, height, 0, ITEM_HEIGHT);
		this.onSelect = onSelect;
		this.onInteract = onInteract;
	}

	public void setPresets(List<Preset> presets, @Nullable String selectedId) {
		this.clearEntries();
		Entry toSelect = null;
		for (Preset preset : presets) {
			Entry entry = new Entry(preset);
			this.addEntry(entry);
			if (preset.id().equals(selectedId)) {
				toSelect = entry;
			}
		}
		this.setSelected(toSelect);
		if (toSelect != null) {
			this.scrollToEntry(toSelect);
		}
	}

	public boolean isEmpty() {
		return this.children().isEmpty();
	}

	public @Nullable Preset getSelectedPreset() {
		Entry selected = this.getSelected();
		return selected == null ? null : selected.preset;
	}

	@Override
	public void setSelected(@Nullable Entry selected) {
		super.setSelected(selected);
		this.onSelect.accept(selected == null ? null : selected.preset);
	}

	@Override
	public int getRowWidth() {
		return ROW_WIDTH;
	}

	static Component describeCategories(Preset preset) {
		if (preset.hasAllCategories()) {
			return Component.translatable("settingspresets.categories.all");
		}
		if (preset.categories().isEmpty()) {
			return Component.translatable("settingspresets.categories.none");
		}
		MutableComponent result = Component.empty();
		boolean first = true;
		for (OptionCategory category : OptionCategory.values()) {
			if (preset.categories().contains(category)) {
				if (!first) {
					result.append(", ");
				}
				result.append(Component.translatable(category.translationKey()));
				first = false;
			}
		}
		return result;
	}

	public final class Entry extends ObjectSelectionList.Entry<Entry> {
		private final Preset preset;
		private final StringWidget nameText;
		private final StringWidget summaryText;

		Entry(Preset preset) {
			this.preset = preset;
			int maxTextWidth = PresetList.this.getRowWidth() - 4 - 2;
			var font = PresetList.this.minecraft.font;

			Component name = Component.literal(preset.name());
			this.nameText = new StringWidget(name, font).setMaxWidth(maxTextWidth);
			if (font.width(name) > maxTextWidth) {
				this.nameText.setTooltip(Tooltip.create(name));
			}

			String date = DATE_FORMAT.format(ZonedDateTime.ofInstant(preset.modifiedAt(), ZoneId.systemDefault()));
			Component summary = Component.translatable("settingspresets.entry.summary", describeCategories(preset), date)
				.withColor(SECONDARY_TEXT_COLOR);
			this.summaryText = new StringWidget(summary, font).setMaxWidth(maxTextWidth);
			if (font.width(summary) > maxTextWidth) {
				this.summaryText.setTooltip(Tooltip.create(summary));
			}
		}

		public Preset preset() {
			return this.preset;
		}

		@Override
		public Component getNarration() {
			return Component.translatable(
				"narrator.select",
				CommonComponents.joinForNarration(Component.literal(this.preset.name()), describeCategories(this.preset))
			);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			int textX = this.getContentX() + 2;
			this.nameText.setPosition(textX, this.getContentY() + 1);
			this.nameText.extractRenderState(graphics, mouseX, mouseY, a);
			this.summaryText.setPosition(textX, this.getContentY() + 9 + 4);
			this.summaryText.extractRenderState(graphics, mouseX, mouseY, a);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (doubleClick) {
				PresetList.this.setSelected(this);
				this.interact();
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.isSelection()) {
				this.interact();
				return true;
			}
			return super.keyPressed(event);
		}

		private void interact() {
			PresetList.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			PresetList.this.onInteract.accept(this.preset);
		}
	}
}
