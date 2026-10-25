package com.imnot.worldfolders;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.concurrent.CompletableFuture;

import static com.imnot.worldfolders.Ui.tr;

/** Mod options (opened from Mod Menu). */
public class ConfigScreen extends Screen {
	private final Screen parent;

	public ConfigScreen(Screen parent) {
		super(Component.literal("Not Only World Folders (NOWF)"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Store.Config c = Store.config();
		int x = width / 2 - 155, y = Math.min(height / 4, height - 150); // five rows above the Done button
		String dir = c.savesDir.isEmpty() ? tr("default_saves").getString() : c.savesDir;
		addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(tr("saves_dir").getString() + ": " + dir, 300)), b -> pickFolder())
				.tooltip(Tooltip.create(tr("saves_dir_tooltip", dir)))
				.bounds(x, y, 310, 20).build());
		addRenderableWidget(Button.builder(tr("reset_saves"), b -> {
			c.savesDir = "";
			Store.save();
			rebuildWidgets();
		}).bounds(x, y + 24, 150, 20).build()).active = !c.savesDir.isEmpty();
		addRenderableWidget(CycleButton.onOffBuilder(c.dragAndDrop).create(x + 160, y + 24, 150, 20, tr("drag_and_drop"), (b, value) -> {
			c.dragAndDrop = value;
			Store.save();
		}));
		addRenderableWidget(Button.builder(tr("default_icon", c.folderIcon), b -> minecraft.gui.setScreen(new PromptScreen(tr("default_icon", ""), c.folderIcon, id -> {
			if (Ui.item(id).isEmpty()) {
				Ui.toast(tr("unknown_item"), Component.literal(id));
				return;
			}
			c.folderIcon = id.trim();
			Store.save();
		}, this).hint(tr("icon_hint")).suggestions(typed -> Space.suggest(Ui.itemIds(), typed, Integer.MAX_VALUE)).preview(Ui::item))).bounds(x, y + 48, 310, 20).build());
		addRenderableWidget(CycleButton.onOffBuilder(c.notifications).create(x, y + 72, 150, 20, tr("notifications"), (b, value) -> {
			c.notifications = value;
			Store.save();
		}));
		// How long a notification stays: 0.5 s to 10 s, by half seconds. Saved when the screen closes.
		addRenderableWidget(new AbstractSliderButton(x + 160, y + 72, 150, 20, CommonComponents.EMPTY, (c.notificationSeconds - 0.5) / 9.5) {
			{
				updateMessage();
			}

			@Override
			protected void updateMessage() {
				setMessage(tr("notification_seconds", String.valueOf(Math.round((0.5 + value * 9.5) * 2) / 2.0)));
			}

			@Override
			protected void applyValue() {
				c.notificationSeconds = Math.round((0.5 + value * 9.5) * 2) / 2.0;
			}
		});
		addRenderableWidget(Button.builder(tr("edit_presets"), b -> Presets.menu(this, b, true)).bounds(x, y + 96, 150, 20).build());
		toggle(x + 160, y + 96, "skip_pack_warning", c.skipPackWarning, value -> c.skipPackWarning = value);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, height - 30, 200, 20).build());
	}

	/** An on/off option, half a row wide, explained by its tooltip. */
	private void toggle(int x, int y, String key, boolean now, BooleanConsumer set) {
		addRenderableWidget(CycleButton.onOffBuilder(now).withTooltip(value -> Tooltip.create(tr(key + "_tooltip"))).create(x, y, 150, 20, tr(key), (b, value) -> {
			set.accept(value);
			Store.save();
		}));
	}

	private void pickFolder() {
		pickFolder(this::rebuildWidgets);
	}

	/**
	 * Native folder dialog, opened in %APPDATA% (where launchers keep their instances). The folder
	 * picked becomes the worlds folder and joins the instance dropdown; then {@code done} runs.
	 */
	public static void pickFolder(Runnable done) {
		Store.Config c = Store.config();
		String appData = System.getenv("APPDATA");
		String start = !c.savesDir.isEmpty() ? c.savesDir : appData != null ? appData + "\\" : System.getProperty("user.home");
		String title = tr("saves_dir").getString();
		// ponytail: the dialog runs off the render thread so the game window keeps painting while it is open.
		CompletableFuture.supplyAsync(() -> TinyFileDialogs.tinyfd_selectFolderDialog(title, start), Util.ioPool())
				.thenAcceptAsync(path -> {
					if (path == null) return;
					if (!Instances.hasWorlds(java.nio.file.Path.of(path))) { // showing it would only open the world creation screen
						Ui.toast(tr("no_world"), Component.literal(path));
						return;
					}
					c.savesDir = path;
					if (!c.otherSavesDirs.contains(path)) c.otherSavesDirs.add(path);
					Store.save();
					done.run();
				}, Minecraft.getInstance());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		Store.save();
		minecraft.gui.setScreen(parent);
	}
}
