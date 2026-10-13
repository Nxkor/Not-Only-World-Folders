package com.imnot.worldfolders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.imnot.worldfolders.Ui.tr;
import static com.imnot.worldfolders.WorldMenus.editDescription;
import static com.imnot.worldfolders.WorldMenus.namePrompt;

/** Right-click menus of the multiplayer screen, and the server operations behind them. */
public final class ServerMenus {
	private static @Nullable ServerData clipboard;

	public static String key(ServerData data) {
		return ((Hooks.ServerKey) data).worldfolders$key();
	}

	private static ServerSelectionList list(JoinMultiplayerScreen screen) {
		return (ServerSelectionList) ((Hooks.ListHolder) screen).worldfolders$list();
	}

	/** Saves our data and servers.dat (which holds the server ids), then re-arranges the list. */
	private static void saveAndRefresh(JoinMultiplayerScreen screen) {
		Store.save();
		screen.getServers().save();
		list(screen).updateOnlineServers(screen.getServers());
	}

	public static void server(JoinMultiplayerScreen screen, ServerSelectionList.OnlineServerEntry entry) {
		Minecraft mc = Minecraft.getInstance();
		Space s = Store.servers();
		ServerData data = entry.getServerData();
		String key = key(data);
		if (s.selection.size() > 1 && s.selection.contains(key)) {
			WorldMenus.multiMenu(screen, s, s.selected(Hooks.keys(list(screen))), () -> saveAndRefresh(screen));
			return;
		}
		ContextMenu menu = ContextMenu.on(screen);
		if (s.trashView) {
			menu.add(tr("restore"), () -> {
				s.restore(key);
				Ui.notify(tr("restored"), Component.literal(data.name));
				saveAndRefresh(screen);
			}).add(tr("delete_forever"), () -> deleteForever(screen, data)).open();
			return;
		}
		boolean fav = s.favorites.contains(key);
		menu.add(tr("join"), () -> {
					screen.setSelected(entry);
					screen.joinSelectedServer();
				})
				.add(tr("rename"), () -> mc.setScreen(namePrompt(tr("rename"), Ui.uncolors(data.name), name -> {
					if (!name.isBlank()) data.name = Ui.colors(name.trim());
					saveAndRefresh(screen);
				}, screen)))
				.add(tr("description"), () -> editDescription(screen, s.descriptions, key))
				.add(tr(fav ? "unfavorite" : "favorite"), () -> {
					if (fav) s.favorites.remove(key);
					else s.favorites.add(key);
					saveAndRefresh(screen);
				})
				.add(tr("new_folder_with"), () -> mc.setScreen(namePrompt(tr("new_folder"), "", name -> {
					String path = s.createFolder(s.folderOf(key), name);
					if (path != null) s.move(key, path);
					saveAndRefresh(screen);
				}, screen)))
				.add(tr("move"), () -> mc.setScreen(new TreePickerScreen(screen, s, path -> {
					s.move(key, path);
					saveAndRefresh(screen);
				})))
				.add(tr("copy"), () -> clipboard = data)
				.addIf(clipboard != null, tr("paste"), () -> paste(screen, s.folderOf(key)))
				.addIf(!fav, tr("to_trash"), () -> {
					s.toTrash(key);
					saveAndRefresh(screen);
				})
				.open();
	}

	/** The vanilla "Delete" button: to the trash, or for real from the trash view. */
	public static void deleteButton(JoinMultiplayerScreen screen) {
		if (!(list(screen).getSelected() instanceof ServerSelectionList.OnlineServerEntry entry)) return;
		Space s = Store.servers();
		String key = key(entry.getServerData());
		if (s.trashView) deleteForever(screen, entry.getServerData());
		else if (s.toTrash(key)) saveAndRefresh(screen);
		else Ui.toast(tr("favorite_protected"), Component.literal(entry.getServerData().name));
	}

	private static void deleteForever(JoinMultiplayerScreen screen, ServerData data) {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(new ConfirmScreen(ok -> {
			if (ok) {
				screen.getServers().remove(data);
				Store.servers().forget(key(data));
				Ui.notify(tr("deleted"), Component.literal(data.name));
				list(screen).setSelected(null);
				saveAndRefresh(screen);
			}
			mc.setScreen(screen);
		}, Component.translatable("selectServer.deleteQuestion"), Component.translatable("selectServer.deleteWarning", data.name),
				Component.translatable("selectServer.deleteButton"), CommonComponents.GUI_CANCEL));
	}

	/** "Empty trash": removes every trashed server from the list for good, after asking. */
	public static void emptyTrash(JoinMultiplayerScreen screen) {
		Minecraft mc = Minecraft.getInstance();
		Space s = Store.servers();
		mc.setScreen(new ConfirmScreen(ok -> {
			if (ok) {
				ServerList servers = screen.getServers();
				for (int i = servers.size() - 1; i >= 0; i--) {
					if (s.trash.contains(key(servers.get(i)))) servers.remove(servers.get(i));
				}
				new ArrayList<>(s.trash).forEach(s::forget);
				list(screen).setSelected(null);
				saveAndRefresh(screen);
			}
			mc.setScreen(screen);
		}, tr("empty_trash_title"), tr("empty_trash_text", s.trash.size())));
	}

	public static void folder(FolderRow row) {
		Screen screen = Minecraft.getInstance().screen;
		if (screen instanceof JoinMultiplayerScreen jms) folderMenu(jms, row.path == null ? Store.servers().current : row.path, row.path != null);
	}

	public static void background(JoinMultiplayerScreen screen) {
		if (!Store.servers().trashView) folderMenu(screen, Store.servers().current, false);
	}

	private static void folderMenu(JoinMultiplayerScreen screen, String path, boolean editable) {
		Minecraft mc = Minecraft.getInstance();
		Space s = Store.servers();
		ContextMenu.on(screen)
				.addIf(editable, tr("open"), () -> {
					s.current = path;
					list(screen).updateOnlineServers(screen.getServers());
				})
				.add(tr("new_subfolder"), () -> mc.setScreen(namePrompt(tr("new_folder"), "", name -> {
					s.createFolder(path, name);
					saveAndRefresh(screen);
				}, screen)))
				.addIf(clipboard != null, tr("paste_here"), () -> paste(screen, path))
				.addIf(editable, tr("rename_folder"), () -> mc.setScreen(namePrompt(tr("rename_folder"), Space.name(path), name -> {
					s.renameFolder(path, name);
					Ui.notify(tr("folder_renamed"), Ui.format(name));
					saveAndRefresh(screen);
				}, screen)))
				.addIf(editable, tr("icon"), () -> WorldMenus.editIcon(screen, s, path))
				.addIf(editable, tr("description"), () -> editDescription(screen, s.folderDescriptions, path))
				.addIf(editable, tr("delete_folder"), () -> {
					s.deleteFolder(path);
					saveAndRefresh(screen);
				})
				.addIf(editable, tr("delete_folder_content"), () -> WorldMenus.confirmDeleteWithContent(screen, s, path, () -> saveAndRefresh(screen)))
				.open();
	}

	public static void newFolder(JoinMultiplayerScreen screen) {
		Space s = Store.servers();
		Minecraft.getInstance().setScreen(namePrompt(tr("new_folder"), "", name -> {
			s.createFolder(s.current, name);
			saveAndRefresh(screen);
		}, screen));
	}

	/** Adds a copy of the clipboard server (new id) into {@code folder}. */
	private static void paste(JoinMultiplayerScreen screen, String folder) {
		ServerList servers = screen.getServers();
		ServerData copy = new ServerData(clipboard.name, clipboard.ip, false);
		copy.copyFrom(clipboard);
		servers.add(copy, false);
		Store.servers().move(key(copy), folder);
		saveAndRefresh(screen);
	}
}
