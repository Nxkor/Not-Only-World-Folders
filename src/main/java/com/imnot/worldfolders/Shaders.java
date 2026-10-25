package com.imnot.worldfolders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.imnot.worldfolders.Ui.tr;

/**
 * The shaders screen of Iris lists the shader packs of every instance of this computer, and gets a trash.
 * No Iris class here: the Iris mixins (mixin.iris, only applied when Iris is there) call in.
 * A pack of another instance goes to Iris by its full path, which Iris resolves as is.
 */
public final class Shaders {
	/** The screen shows the trash instead of the packs; a new screen starts on the packs. */
	public static boolean trashView;
	/** Full path of another instance's pack -> "name (instance)", for the list. */
	private static final Map<String, String> LABELS = new HashMap<>();

	/**
	 * {@code names}: what Iris found in its own folder {@code root}. Returns those plus the packs of the other
	 * instances, without the trashed ones; or only the trashed ones in the trash view.
	 */
	public static List<String> arrange(Path root, List<String> names) {
		List<String> all = new ArrayList<>(names);
		LABELS.clear();
		List<Instances.Instance> instances = WorldMenus.instances();
		Path own = root.toAbsolutePath().normalize();
		for (Instances.Instance instance : instances.subList(1, instances.size())) {
			Path dir = instance.saves().toAbsolutePath().normalize().resolveSibling("shaderpacks");
			if (dir.equals(own)) continue;
			try (Stream<Path> packs = Files.list(dir)) {
				// What Iris shows: folders and zips.
				packs.filter(p -> Files.isDirectory(p) || p.toString().endsWith(".zip")).sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase())).forEach(p -> {
					String path = p.toString();
					LABELS.put(path, p.getFileName() + " (" + instance.label() + ")");
					all.add(path);
				});
			} catch (IOException ignored) { // no shaderpacks folder there
			}
		}
		return all.stream().filter(name -> Store.shaderTrash().contains(key(root, name)) == trashView).toList();
	}

	/** Full path of a pack as Iris names it: a file name in its folder, or a full path. */
	private static String key(Path root, String name) {
		return root.resolve(name).toAbsolutePath().normalize().toString();
	}

	/** What the list shows for {@code name}; unknown names (a cut one) are left as they are. */
	public static String label(String name) {
		return LABELS.getOrDefault(name, name);
	}

	/** Right-click on a pack: trash, folder; in the trash view restore or delete. {@code refresh} fills the list again. */
	public static void menu(Path root, String name, Runnable refresh) {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.screen;
		String key = key(root, name);
		Path path = Path.of(key);
		Component title = Component.literal(label(name));
		ContextMenu menu = ContextMenu.on(screen);
		if (trashView) {
			menu.add(tr("restore"), () -> {
				Store.shaderTrash().remove(key);
				Store.save();
				Ui.notify(tr("restored"), title);
				refresh.run();
			}).add(tr("delete_forever"), () -> deleteForever(screen, key, path, title, refresh)).open();
			return;
		}
		menu.add(tr("pack_open_folder"), () -> WorldMenus.openDir(path.getParent()))
				.add(tr("to_trash"), () -> {
					Store.shaderTrash().add(key);
					Store.save();
					refresh.run();
				}).open();
	}

	/** Asks, showing the full path, then deletes the pack and its settings file (pack name + ".txt"). */
	private static void deleteForever(Screen screen, String key, Path path, Component title, Runnable refresh) {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(new ConfirmScreen(ok -> {
			if (ok) {
				try (Stream<Path> files = Files.walk(path)) { // links are not followed
					files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
				} catch (IOException ignored) { // already gone
				}
				if (Files.exists(path)) {
					Ui.toast(tr("pack_delete_failed"), title); // e.g. the zip of the pack in use
				} else {
					path.resolveSibling(path.getFileName() + ".txt").toFile().delete();
					Store.shaderTrash().remove(key);
					Store.save();
					Ui.notify(tr("deleted"), title);
				}
			}
			mc.setScreen(screen);
			refresh.run();
		}, tr("pack_delete_title", title), Component.literal(path.toString())));
	}
}
