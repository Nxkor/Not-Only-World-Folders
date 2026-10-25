package com.imnot.worldfolders;

import com.imnot.worldfolders.mixin.OptionsAccessor;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.imnot.worldfolders.Ui.tr;

/**
 * The options of another instance, like its worlds: picked in the options screen, the game reads and saves
 * that instance's options.txt from then on, until another one is picked (each instance is a preset of options).
 */
public final class InstanceOptions {
	private record Choice(String label, File file) {
	}

	/** The options.txt to read at startup: the one picked, or {@code own} if none is, or it is gone. */
	public static File file(File own) {
		String picked = Store.config().optionsFile;
		return !picked.isEmpty() && new File(picked).isFile() ? new File(picked) : own;
	}

	/** This instance's options.txt, then every other instance that has one. */
	private static List<Choice> choices() {
		List<Choice> out = new ArrayList<>();
		List<Instances.Instance> all = WorldMenus.instances();
		for (int i = 0; i < all.size(); i++) {
			File f = all.get(i).saves().toAbsolutePath().normalize().resolveSibling("options.txt").toFile();
			if (i == 0 || f.isFile()) out.add(new Choice(all.get(i).label(), f));
		}
		return out;
	}

	private static boolean same(File a, File b) {
		return a.toPath().toAbsolutePath().normalize().equals(b.toPath().toAbsolutePath().normalize());
	}

	/** Label of the options in use, for the button of the options screen. */
	public static String current() {
		File now = Minecraft.getInstance().options.getFile();
		return choices().stream().filter(c -> same(c.file, now)).map(Choice::label).findFirst().orElse(now.getParent());
	}

	public static Component tooltip() {
		return tr("options_tooltip", Minecraft.getInstance().options.getFile().getAbsolutePath());
	}

	/** The instance dropdown under {@code button} of the options screen. */
	public static void menu(Screen screen, Button button, Screen lastScreen) {
		List<Choice> all = choices();
		File now = Minecraft.getInstance().options.getFile();
		ContextMenu menu = ContextMenu.on(screen);
		for (int i = 0; i < all.size(); i++) {
			Choice c = all.get(i);
			boolean own = i == 0;
			menu.add(Component.literal((same(c.file, now) ? "✔ " : "    ") + c.label), () -> {
				if (!same(c.file, now)) use(c, own, lastScreen);
			});
		}
		button.setTooltip(null); // it would show through the menu
		menu.onClose(() -> button.setTooltip(Tooltip.create(tooltip()))).openAt(button.getX(), button.getY() + button.getHeight());
	}

	/** Saves the options in use to their file, then reads {@code c}'s and applies what needs more than a read. */
	private static void use(Choice c, boolean own, Screen lastScreen) {
		Minecraft mc = Minecraft.getInstance();
		Options options = mc.options;
		options.save();
		if (!own) {
			// This game writes the options it knows: what only the other game version or its mods know would be
			// lost from that file. A copy of it as it was, once.
			Path backup = c.file.toPath().resolveSibling("options.txt.nowf-backup");
			try {
				if (!Files.exists(backup)) Files.copy(c.file.toPath(), backup);
			} catch (IOException e) {
				Store.LOG.error("Not Only World Folders: could not back up {}", c.file, e);
			}
		}
		Store.config().optionsFile = own ? "" : c.file.getAbsolutePath();
		Store.save();
		((OptionsAccessor) options).worldfolders$setFile(c.file);
		options.load(); // the key bindings too
		options.loadSelectedResourcePacks(mc.getResourcePackRepository());
		mc.getLanguageManager().setSelected(options.languageCode);
		Window window = mc.getWindow();
		if (window.isFullscreen() != options.fullscreen().get()) window.toggleFullScreen();
		window.updateVsync(options.enableVsync().get());
		mc.setScreen(new OptionsScreen(lastScreen, options)); // its buttons show the new values
		mc.resizeDisplay(); // GUI scale
		mc.reloadResourcePacks(); // packs, language, graphics, sounds
		Ui.notify(tr("options_loaded"), Component.literal(c.label));
	}
}
