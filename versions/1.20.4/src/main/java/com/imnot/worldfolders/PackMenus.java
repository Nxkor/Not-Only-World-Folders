package com.imnot.worldfolders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static com.imnot.worldfolders.Ui.tr;
import static com.imnot.worldfolders.WorldMenus.namePrompt;

/** Right-click menus of the "Available" list of the resource pack screen: the same as the worlds'. */
public final class PackMenus {
	private static Hooks.OrganizedList list(Screen screen) {
		return (Hooks.OrganizedList) ((Hooks.ListHolder) screen).worldfolders$list();
	}

	private static void saveAndRefresh(Screen screen) {
		Store.save();
		list(screen).worldfolders$refresh();
	}

	/** {@code select}: what a click on the pack's icon does. */
	public static void pack(String id, Component title, Runnable select) {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.screen;
		Space s = Store.packs();
		if (s.selection.size() > 1 && s.selection.contains(id)) {
			WorldMenus.multiMenu(screen, s, s.selected(Hooks.keys(list(screen))), () -> saveAndRefresh(screen));
			return;
		}
		Path path = WorldMenus.packPath(id);
		ContextMenu menu = ContextMenu.on(screen);
		if (s.trashView) {
			menu.add(tr("restore"), () -> {
				s.restore(id);
				Ui.notify(tr("restored"), title);
				saveAndRefresh(screen);
			}).addIf(path != null, tr("delete_forever"), () -> deleteForever(screen, id, title, path)).open();
			return;
		}
		boolean fav = s.favorites.contains(id);
		menu.add(tr("pack_select"), select)
				.add(tr("description"), () -> WorldMenus.editDescription(screen, s.descriptions, id))
				.add(tr(fav ? "unfavorite" : "favorite"), () -> {
					if (fav) s.favorites.remove(id);
					else s.favorites.add(id);
					saveAndRefresh(screen);
				})
				.add(tr("new_folder_with"), () -> mc.setScreen(namePrompt(tr("new_folder"), "", name -> {
					String folder = s.createFolder(s.folderOf(id), name);
					if (folder != null) s.move(id, folder);
					Store.save();
				}, screen)))
				.add(tr("move"), () -> mc.setScreen(new TreePickerScreen(screen, s, folder -> {
					s.move(id, folder);
					Store.save();
				})))
				.addIf(path != null, tr("pack_open_folder"), () -> WorldMenus.openDir(path.getParent()))
				.addIf(!fav, tr("to_trash"), () -> {
					s.toTrash(id);
					saveAndRefresh(screen);
				})
				.open();
	}

	/** Menu of a folder row; the ".." row stands for the folder being browsed. */
	public static void folder(FolderRow row) {
		WorldMenus.folderMenu(Minecraft.getInstance().screen, Store.packs(), row.path == null ? Store.packs().current : row.path, row.path != null);
	}

	/** Right-click on the empty part of the list = the folder being browsed. */
	public static void background(Screen screen) {
		if (!Store.packs().trashView) WorldMenus.folderMenu(screen, Store.packs(), Store.packs().current, false);
	}

	public static void newFolder(Screen screen) {
		Space s = Store.packs();
		Minecraft.getInstance().setScreen(namePrompt(tr("new_folder"), "", name -> {
			s.createFolder(s.current, name);
			Store.save();
		}, screen));
	}

	/** Asks, showing the full path, then deletes the pack's file or folder from the disk. */
	private static void deleteForever(Screen screen, String id, Component title, Path path) {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(new ConfirmScreen(ok -> {
			if (ok) {
				try (Stream<Path> files = Files.walk(path)) { // links are not followed
					files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
				} catch (IOException ignored) { // already gone
				}
				if (Files.exists(path)) {
					Ui.toast(tr("pack_delete_failed"), title); // e.g. a zip the game still holds open
				} else {
					Store.packs().forget(id);
					Store.save();
					Ui.notify(tr("deleted"), title);
					list(screen).worldfolders$reload();
				}
			}
			mc.setScreen(screen);
		}, tr("pack_delete_title", title), Component.literal(path.toString())));
	}
}
