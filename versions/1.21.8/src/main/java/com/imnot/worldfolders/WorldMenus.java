package com.imnot.worldfolders;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import net.minecraft.ChatFormatting;
import net.minecraft.FileUtil;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.stats.StatType;
import net.minecraft.stats.StatsCounter;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.imnot.worldfolders.Ui.tr;

/** Right-click menus of the singleplayer screen, and the world operations behind them. */
public final class WorldMenus {
	/** Copied world. A full path, so it survives browsing and switching the saves folder (copy from another instance). */
	private record Clip(Path dir, String name) {
	}

	private static @Nullable Clip clipboard;

	private static Hooks.OrganizedList list(Screen screen) {
		return (Hooks.OrganizedList) ((Hooks.ListHolder) screen).worldfolders$list();
	}

	private static void saveAndRefresh(Screen screen) {
		Store.save();
		list(screen).worldfolders$refresh();
	}

	public static void world(WorldSelectionList.WorldListEntry entry, LevelSummary summary) {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.screen;
		Space s = Store.worlds();
		String id = summary.getLevelId();
		if (s.selection.size() > 1 && s.selection.contains(id)) {
			multiMenu(screen, s, s.selected(Hooks.keys(list(screen))), () -> saveAndRefresh(screen));
			return;
		}
		ContextMenu menu = ContextMenu.on(screen);
		if (s.trashView) {
			menu.add(tr("restore"), () -> {
				s.restore(id);
				Ui.notify(tr("restored"), Component.literal(summary.getLevelName()));
				saveAndRefresh(screen);
			}).add(tr("delete_forever"), entry::deleteWorld).open();
			return;
		}
		boolean fav = s.favorites.contains(id);
		menu.add(tr("play"), entry::joinWorld)
				.add(tr("rename"), () -> mc.setScreen(namePrompt(tr("rename"), Ui.uncolors(summary.getLevelName()), name -> rename(screen, id, Ui.colors(name)), screen)))
				.add(tr("description"), () -> editDescription(screen, s.descriptions, id))
				.add(tr(fav ? "unfavorite" : "favorite"), () -> {
					if (fav) s.favorites.remove(id);
					else s.favorites.add(id);
					saveAndRefresh(screen);
				})
				.add(tr("new_folder_with"), () -> mc.setScreen(namePrompt(tr("new_folder"), "", name -> {
					String path = s.createFolder(s.folderOf(id), name);
					if (path != null) s.move(id, path);
					Store.save();
				}, screen)))
				.add(tr("move"), () -> mc.setScreen(new TreePickerScreen(screen, s, path -> {
					s.move(id, path);
					Store.save();
				})))
				.add(tr("copy"), () -> clipboard = new Clip(mc.getLevelSource().getBaseDir().resolve(id), summary.getLevelName()))
				.addIf(clipboard != null, tr("paste"), () -> paste(screen, s.folderOf(id)))
				.add(tr("open_folder"), () -> Util.getPlatform().openPath(mc.getLevelSource().getBaseDir().resolve(id)))
				.add(tr("gamerules"), () -> editGameRules(screen, id))
				.add(tr("stats"), () -> showStats(screen, id, summary.getLevelName()))
				.addIf(!fav, tr("to_trash"), () -> {
					s.toTrash(id);
					saveAndRefresh(screen);
				})
				.open();
	}

	/** Right click on a multi-selection (worlds or servers): what applies to several rows at once. */
	static void multiMenu(Screen screen, Space s, List<String> keys, Runnable saveAndRefresh) {
		Minecraft mc = Minecraft.getInstance();
		Component n = Component.literal(" (" + keys.size() + ")");
		ContextMenu menu = ContextMenu.on(screen);
		if (s.trashView) {
			menu.add(tr("restore").append(n), () -> {
				keys.forEach(s::restore);
				Ui.notify(tr("restored"), tr("n_selected", keys.size()));
				s.selection.clear();
				saveAndRefresh.run();
			});
		} else {
			boolean allFavorites = s.favorites.containsAll(keys);
			menu.add(tr(allFavorites ? "unfavorite" : "favorite").append(n), () -> {
						if (allFavorites) keys.forEach(s.favorites::remove);
						else s.favorites.addAll(keys);
						saveAndRefresh.run();
					})
					.add(tr("new_folder_with").append(n), () -> mc.setScreen(namePrompt(tr("new_folder"), "", name -> {
						String path = s.createFolder(s.current, name);
						if (path != null) keys.forEach(k -> s.move(k, path));
						saveAndRefresh.run();
					}, screen)))
					.add(tr("move").append(n), () -> mc.setScreen(new TreePickerScreen(screen, s, path -> {
						keys.forEach(k -> s.move(k, path));
						saveAndRefresh.run();
					})))
					.add(tr("to_trash").append(n), () -> {
						long refused = keys.stream().filter(k -> !s.toTrash(k)).count();
						if (refused > 0) Ui.toast(tr("favorite_protected"), tr("n_selected", refused));
						s.selection.clear();
						saveAndRefresh.run();
					});
		}
		menu.add(tr("deselect"), s.selection::clear).open();
	}

	/** Menu of a folder row; the ".." row stands for the folder being browsed. */
	public static void folder(FolderRow row) {
		Screen screen = Minecraft.getInstance().screen;
		folderMenu(screen, Store.worlds(), row.path == null ? Store.worlds().current : row.path, row.path != null);
	}

	/** Right-click on the empty part of the list = the folder being browsed. */
	public static void background(Screen screen) {
		if (!Store.worlds().trashView) folderMenu(screen, Store.worlds(), Store.worlds().current, false);
	}

	/** {@code s}: the worlds, or the resource packs (their screen is a list holder too). */
	static void folderMenu(Screen screen, Space s, String path, boolean editable) {
		Minecraft mc = Minecraft.getInstance();
		ContextMenu.on(screen)
				.addIf(editable, tr("open"), () -> {
					s.current = path;
					list(screen).worldfolders$refresh();
				})
				.add(tr("new_subfolder"), () -> mc.setScreen(namePrompt(tr("new_folder"), "", name -> {
					s.createFolder(path, name);
					Store.save();
				}, screen)))
				.addIf(clipboard != null && s == Store.worlds(), tr("paste_here"), () -> paste(screen, path))
				.addIf(editable, tr("rename_folder"), () -> mc.setScreen(namePrompt(tr("rename_folder"), Space.name(path), name -> {
					s.renameFolder(path, name);
					Ui.notify(tr("folder_renamed"), Ui.format(name));
					Store.save();
				}, screen)))
				.addIf(editable, tr("icon"), () -> editIcon(screen, s, path))
				.addIf(editable, tr("description"), () -> editDescription(screen, s.folderDescriptions, path))
				.addIf(editable, tr("delete_folder"), () -> {
					s.deleteFolder(path);
					saveAndRefresh(screen);
				})
				.addIf(editable, tr("delete_folder_content"), () -> WorldMenus.confirmDeleteWithContent(screen, s, path, () -> saveAndRefresh(screen)))
				.open();
	}

	/** Asks, then deletes the folder and sends its content to the trash (favorites move up instead). */
	static void confirmDeleteWithContent(Screen screen, Space s, String path, Runnable refresh) {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(new ConfirmScreen(ok -> {
			if (ok) {
				s.deleteFolderWithContent(path);
				refresh.run();
			}
			mc.setScreen(screen);
		}, tr("delete_folder_content_title", Ui.format(Space.name(path))), tr("delete_folder_content_text")));
	}

	/** The "+" of a folder row: the vanilla world creation; the world made will go into {@code folder}. */
	static void createIn(String folder) {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.screen;
		HashSet<String> known = new HashSet<>();
		try (Stream<Path> dirs = Files.list(mc.getLevelSource().getBaseDir())) {
			dirs.forEach(p -> known.add(p.getFileName().toString()));
		} catch (IOException ignored) { // no saves folder yet: every world will be new
		}
		Store.worlds().expect(folder, known);
		Store.save();
		CreateWorldScreen.openFresh(mc, screen);
	}

	public static void newFolder(Screen screen) {
		Space s = Store.worlds();
		Minecraft.getInstance().setScreen(namePrompt(tr("new_folder"), "", name -> {
			s.createFolder(s.current, name);
			Store.save();
		}, screen));
	}

	/** {@code descriptions}: of worlds, servers or folders. */
	static void editDescription(Screen screen, Map<String, String> descriptions, String key) {
		Minecraft.getInstance().setScreen(new PromptScreen(tr("description_hint"), descriptions.getOrDefault(key, ""), text -> {
			if (text.isBlank()) descriptions.remove(key);
			else descriptions.put(key, text);
			if (descriptions == Store.worlds().folderDescriptions || descriptions == Store.servers().folderDescriptions || descriptions == Store.packs().folderDescriptions) Ui.notify(tr("folder_description_edited"), Ui.format(Space.name(key)));
			Store.save();
		}, screen).maxLength(512));
	}

	/** Prompt for a world, server or folder name, which takes "&" colour codes. */
	static PromptScreen namePrompt(Component title, String initial, Consumer<String> onDone, Screen parent) {
		return new PromptScreen(title, initial, onDone, parent).hint(tr("color_hint"));
	}

	/** Blank = back to the default icon. An unknown id is refused, so a typo doesn't silently show a chest. */
	static void editIcon(Screen screen, Space s, String path) {
		Minecraft.getInstance().setScreen(new PromptScreen(tr("icon"), s.folderIcons.getOrDefault(path, ""), id -> {
			id = id.trim();
			if (id.isEmpty()) s.folderIcons.remove(path);
			else if (!Ui.item(id).isEmpty()) s.folderIcons.put(path, id);
			else Ui.toast(tr("unknown_item"), Component.literal(id));
			if (id.isEmpty() || !Ui.item(id).isEmpty()) Ui.notify(tr("folder_icon_edited"), Ui.format(Space.name(path)));
			Store.save();
		}, screen).hint(tr("icon_hint")).suggestions(typed -> Space.suggest(Ui.itemIds(), typed, Integer.MAX_VALUE))
				.preview(id -> Ui.item(id.isBlank() ? Store.config().folderIcon : id)));
	}

	/** Longest play time among the players of the world (stats/<uuid>.json), in seconds. 0 if never saved. */
	public static long playSeconds(String id) {
		long ticks = 0;
		try (Stream<Path> files = Files.list(Minecraft.getInstance().getLevelSource().getBaseDir().resolve(id).resolve("stats"))) {
			for (Path p : (Iterable<Path>) files::iterator) {
				try {
					JsonObject custom = JsonParser.parseString(Files.readString(p)).getAsJsonObject().getAsJsonObject("stats").getAsJsonObject("minecraft:custom");
					ticks = Math.max(ticks, custom.get("minecraft:play_time").getAsLong());
				} catch (Exception ignored) { // not a stats file, or no play time in it yet
				}
			}
		} catch (IOException ignored) { // no stats folder yet
		}
		return ticks / 20;
	}

	private static final Map<Path, Long> SIZES = new ConcurrentHashMap<>();

	/** Size of the world folder in bytes; -1 while it is being measured, off the render thread. */
	public static long sizeOf(String id) {
		Path dir = Minecraft.getInstance().getLevelSource().getBaseDir().resolve(id);
		Long size = SIZES.putIfAbsent(dir, -1L);
		if (size != null) return size;
		CompletableFuture.runAsync(() -> {
			try (Stream<Path> files = Files.walk(dir)) {
				SIZES.put(dir, files.mapToLong(p -> p.toFile().length()).sum()); // 0 for folders
			} catch (Exception e) { // the world vanished meanwhile
				SIZES.put(dir, 0L);
			}
		}, Util.ioPool());
		return -1;
	}

	/**
	 * Changes when a world is added, removed or saved in the saves folder (another launcher, a copy in the
	 * file explorer...): the worlds screen then reloads by itself. Order-free, so it doesn't matter how the
	 * folder lists. ponytail: a stat per world every 2 s; a WatchService if big saves folders ever feel it.
	 */
	public static long savesFingerprint() {
		long sum = 0;
		try (Stream<Path> dirs = Files.list(Minecraft.getInstance().getLevelSource().getBaseDir())) {
			for (Path d : (Iterable<Path>) dirs::iterator) sum += d.getFileName().toString().hashCode() * 31L + d.resolve("level.dat").toFile().lastModified();
		} catch (IOException ignored) { // no saves folder yet
		}
		return sum;
	}

	/** The worlds on disk changed: sizes must be measured again. */
	public static void forgetSizes() {
		SIZES.clear();
	}

	/** "Empty trash": deletes every trashed world for good, after asking. A world open in another game stays. */
	public static void emptyTrash(Screen screen) {
		Minecraft mc = Minecraft.getInstance();
		Space s = Store.worlds();
		List<String> ids = new ArrayList<>(s.trash);
		mc.setScreen(new ConfirmScreen(ok -> {
			if (!ok) {
				mc.setScreen(screen);
				return;
			}
			LevelStorageSource source = mc.getLevelSource();
			mc.setScreen(new GenericMessageScreen(tr("emptying_trash")));
			CompletableFuture.supplyAsync(() -> {
				List<String> gone = new ArrayList<>();
				for (String id : ids) {
					// Already gone (deleted outside the game): just forget it. Opening an access would recreate the folder.
					if (!Files.isDirectory(source.getBaseDir().resolve(id))) {
						gone.add(id);
						continue;
					}
					try (LevelStorageSource.LevelStorageAccess access = source.validateAndCreateAccess(id)) {
						access.deleteLevel();
						gone.add(id);
					} catch (Exception e) {
						Store.LOG.error("Not Only World Folders: could not delete {}", id, e);
					}
				}
				return gone;
			}, Util.ioPool()).thenAcceptAsync(gone -> {
				gone.forEach(s::forget);
				if (!gone.isEmpty()) Ui.notify(tr("deleted"), tr("n_worlds", gone.size()));
				Store.save();
				if (gone.size() < ids.size()) Ui.toast(tr("empty_trash_failed"), Component.literal(String.valueOf(ids.size() - gone.size())));
				forgetSizes();
				list(screen).worldfolders$reload();
				mc.setScreen(screen);
			}, mc);
		}, tr("empty_trash_title"), tr("empty_trash_text", ids.size())));
	}

	/** Every saves folder of this computer: this instance's, the launchers' ones found, and the ones picked by hand. */
	public static List<Instances.Instance> instances() {
		Minecraft mc = Minecraft.getInstance();
		Path own = mc.gameDirectory.toPath().resolve("saves").toAbsolutePath().normalize();
		String appData = System.getenv("APPDATA");
		List<Instances.Instance> out = new ArrayList<>();
		out.add(new Instances.Instance(tr("this_instance").getString(), own));
		for (Instances.Instance i : Instances.find(Path.of(System.getProperty("user.home")), appData == null ? null : Path.of(appData), Store.config().otherSavesDirs)) {
			if (!i.saves().toAbsolutePath().normalize().equals(own)) out.add(i);
		}
		return out;
	}

	/** Label of the saves folder shown now, for the instance button. */
	public static String currentInstance(List<Instances.Instance> all) {
		return instanceLabel(all, Minecraft.getInstance().getLevelSource().getBaseDir().toAbsolutePath().normalize());
	}

	private static String instanceLabel(List<Instances.Instance> all, Path now) {
		return all.stream().filter(i -> i.saves().toAbsolutePath().normalize().equals(now)).map(Instances.Instance::label).findFirst()
				.orElse(String.valueOf(now.getParent() != null ? now.getParent().getFileName() : now));
	}

	/** Saves folders left because they had no world (any more): still in the instance dropdown until the game restarts. */
	private static final HashSet<Path> EMPTIED = new HashSet<>();
	/** An empty saves folder picked on purpose in the dropdown: shown once (Minecraft then offers to create a world). */
	private static @Nullable Path allowEmpty;

	/**
	 * The instances of the dropdown: {@link #instances} without the saves folders that hold no world. Showing
	 * one sends Minecraft straight to the world creation screen, with no dropdown left to come back.
	 */
	public static List<Instances.Instance> savesInstances() {
		List<Instances.Instance> all = instances(), out = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			Path saves = all.get(i).saves().toAbsolutePath().normalize();
			if (i == 0 || Instances.hasWorlds(saves) || EMPTIED.contains(saves)) out.add(all.get(i));
		}
		return out;
	}

	/**
	 * Before the worlds are read: another instance's saves folder with no world in it (the last one was just
	 * deleted, or deleted outside the game) is left for this game's own.
	 */
	public static void leaveEmptyInstance() {
		Store.Config c = Store.config();
		if (c.savesDir.isEmpty()) return;
		Path saves = Path.of(c.savesDir).toAbsolutePath().normalize();
		if (Instances.hasWorlds(saves)) return;
		if (saves.equals(allowEmpty)) {
			allowEmpty = null;
			return;
		}
		EMPTIED.add(saves);
		c.savesDir = "";
		Store.save();
		Store.applySavesDir();
		Ui.toast(tr("no_world"), tr("back_to_own"));
	}

	/** The instance dropdown under {@code button}: picking an instance shows its worlds, without leaving the game. */
	public static void instanceMenu(Screen screen, Button button, Screen lastScreen) {
		Minecraft mc = Minecraft.getInstance();
		List<Instances.Instance> all = savesInstances();
		// The new screen points Minecraft at the folder picked.
		instanceItems(screen, all, currentInstance(all), () -> mc.setScreen(new SelectWorldScreen(lastScreen))).openAt(button.getX(), button.getY() + button.getHeight());
	}

	/** The Singleplayer button of the title screen, so a right click can be told to be on it. */
	public static @Nullable Button soloButton;

	/** The saves folder the worlds screen will show: this game's, or the instance picked. */
	private static Path configuredSaves() {
		String dir = Store.config().savesDir;
		return (dir.isEmpty() ? Minecraft.getInstance().gameDirectory.toPath().resolve("saves") : Path.of(dir)).toAbsolutePath().normalize();
	}

	public static Component soloTooltip() {
		return tr("solo_tooltip", instanceLabel(instances(), configuredSaves()));
	}

	/**
	 * Right click on Singleplayer: the instance dropdown, without going through the worlds screen. The
	 * instance picked is the one a left click then opens. Returns false when the click was not for the button.
	 */
	public static boolean soloMenu() {
		Minecraft mc = Minecraft.getInstance();
		Button button = soloButton;
		if (button == null || mc.screen == null || !mc.screen.children().contains(button) || !button.isMouseOver(Ui.mouseX(), Ui.mouseY())) return false;
		List<Instances.Instance> all = savesInstances();
		Runnable tooltip = () -> button.setTooltip(Tooltip.create(soloTooltip()));
		button.setTooltip(null); // it would show through the menu
		instanceItems(mc.screen, all, instanceLabel(all, configuredSaves()), tooltip).onClose(tooltip).openAt(button.getX(), button.getY() + button.getHeight());
		return true;
	}

	/** One row per instance, the one shown now ticked; {@code picked} runs once another saves folder is chosen. */
	private static ContextMenu instanceItems(Screen screen, List<Instances.Instance> all, String now, Runnable picked) {
		Store.Config c = Store.config();
		ContextMenu menu = ContextMenu.on(screen);
		for (int i = 0; i < all.size(); i++) {
			Instances.Instance instance = all.get(i);
			boolean own = i == 0;
			menu.add(Component.literal((instance.label().equals(now) ? "✔ " : "    ") + instance.label()), () -> {
				c.savesDir = own ? "" : instance.saves().toString();
				allowEmpty = own || Instances.hasWorlds(instance.saves()) ? null : instance.saves().toAbsolutePath().normalize();
				Store.save();
				picked.run();
			});
		}
		return menu.add(tr("other_folder"), () -> ConfigScreen.pickFolder(picked));
	}

	private static final Map<String, PackSource> PACK_SOURCES = new HashMap<>();
	/** Pack id -> the resourcepacks folder of another instance it was last read from. */
	private static final Map<String, Path> PACK_DIRS = new HashMap<>();

	/** The file or folder of a resource pack ("file/..." ids only): in this instance, else in the one it is listed from. Null if unknown. */
	static @Nullable Path packPath(String id) {
		if (!id.startsWith("file/")) return null;
		Path own = Minecraft.getInstance().getResourcePackDirectory().resolve(id.substring(5));
		if (Files.exists(own)) return own;
		Path dir = PACK_DIRS.get(id);
		return dir == null ? null : dir.resolve(id.substring(5));
	}

	/**
	 * The resource packs of the other instances, for the pack screen of this one. Each says where it comes
	 * from after its description. Folders that don't exist are skipped: reading one would create it.
	 */
	public static void otherPacks(Consumer<Pack> out) {
		Minecraft mc = Minecraft.getInstance();
		List<Instances.Instance> all = instances();
		PACK_DIRS.clear();
		for (Instances.Instance instance : all.subList(1, all.size())) {
			Path dir = instance.saves().toAbsolutePath().normalize().resolveSibling("resourcepacks");
			if (!Files.isDirectory(dir)) continue;
			// One source per instance, kept: a pack read again equals the one read before only if its source is the same
			// object, and the pack screen drops the selected packs it doesn't find again.
			PackSource source = PACK_SOURCES.computeIfAbsent(instance.label(), label -> PackSource.create(description -> Component.translatable("pack.nameAndSource", description, label).withStyle(ChatFormatting.GRAY), false));
			try {
				new FolderRepositorySource(dir, PackType.CLIENT_RESOURCES, source, mc.directoryValidator()).loadPacks(pack -> {
					PACK_DIRS.put(pack.getId(), dir);
					out.accept(pack);
				});
			} catch (Exception e) { // an unreadable folder must not take the game's own packs down with it
				Store.LOG.error("Not Only World Folders: could not list the packs of {}", dir, e);
			}
		}
	}

	/**
	 * The statistics screen of the game, filled from the world folder: those of this player if they
	 * played there, else of the player with the biggest file.
	 */
	public static void showStats(Screen screen, String id, String name) {
		Minecraft mc = Minecraft.getInstance();
		Path dir = mc.getLevelSource().getBaseDir().resolve(id).resolve("stats");
		StatsCounter counter = new StatsCounter();
		try (Stream<Path> files = Files.list(dir)) {
			Path own = dir.resolve(mc.getUser().getProfileId() + ".json");
			Path file = Files.isRegularFile(own) ? own : files.filter(p -> p.toString().endsWith(".json")).max(Comparator.comparingLong(p -> p.toFile().length())).orElseThrow();
			JsonObject stats = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("stats");
			for (Map.Entry<String, JsonElement> type : stats.entrySet()) {
				StatType<?> statType = BuiltInRegistries.STAT_TYPE.getOptional(ResourceLocation.tryParse(type.getKey())).orElse(null);
				if (statType == null) continue;
				for (Map.Entry<String, JsonElement> stat : type.getValue().getAsJsonObject().entrySet()) putStat(counter, statType, stat.getKey(), stat.getValue().getAsInt());
			}
		} catch (Exception e) { // never played, or a file from before 1.13
			Ui.toast(tr("no_stats"), Component.literal(name));
			return;
		}
		StatsScreen stats = new StatsScreen(screen, counter);
		mc.setScreen(stats);
		stats.onStatsUpdated(); // what the answer of a server would do
	}

	/** Ids this game doesn't know (a mod that is not here, a renamed item) are left out. */
	private static <T> void putStat(StatsCounter counter, StatType<T> type, String id, int value) {
		type.getRegistry().getOptional(ResourceLocation.tryParse(id)).ifPresent(what -> counter.setValue(null, type.get(what), value));
	}

	/** Pause menu "Save world": saves everything now, without leaving the world, then tells so. */
	public static void quickSave(Button button) {
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) return;
		button.active = false;
		button.setMessage(tr("saving"));
		server.execute(() -> {
			boolean ok = server.saveEverything(false, true, true); // flush: really on disk when the toast shows
			mc.execute(() -> {
				Ui.toast(tr(ok ? "saved" : "save_failed"), Component.literal(server.getWorldData().getLevelName()));
				button.setMessage(tr("save_world"));
				button.active = true;
			});
		});
	}

	private static void rename(Screen screen, String id, String name) {
		if (name.isBlank()) return; // vanilla refuses an empty world name too
		Minecraft mc = Minecraft.getInstance();
		try (LevelStorageSource.LevelStorageAccess access = mc.getLevelSource().validateAndCreateAccess(id)) {
			access.renameLevel(name);
		} catch (Exception e) {
			fail(id, e);
		}
		list(screen).worldfolders$reload();
	}

	/** Copies the clipboard world into this saves folder (new directory name, name + " (copy)"), into {@code folder}. */
	private static void paste(Screen screen, String folder) {
		Minecraft mc = Minecraft.getInstance();
		Clip clip = clipboard;
		LevelStorageSource source = mc.getLevelSource();
		Space s = Store.worlds();
		String newName = I18n.get("worldfolders.copy_of", clip.name());
		mc.setScreen(new GenericMessageScreen(tr("copying", clip.name())));
		CompletableFuture.supplyAsync(() -> {
			try {
				String id = FileUtil.findAvailableName(source.getBaseDir(), clip.dir().getFileName().toString(), "");
				copyTree(clip.dir(), source.getBaseDir().resolve(id));
				try (LevelStorageSource.LevelStorageAccess access = source.validateAndCreateAccess(id)) {
					access.renameLevel(newName);
				}
				return id;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}, Util.ioPool()).whenCompleteAsync((id, error) -> {
			if (error != null) fail(clip.name(), error);
			else {
				s.move(id, folder);
				Store.save();
			}
			list(screen).worldfolders$reload();
			mc.setScreen(screen);
		}, mc);
	}

	private static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> files = Files.walk(from)) {
			for (Path p : (Iterable<Path>) files::iterator) {
				if (p.getFileName().toString().equals("session.lock")) continue;
				Path dest = to.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(dest);
				else Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES);
			}
		}
	}

	/** Edits the GameRules of level.dat directly, without loading the world. */
	private static void editGameRules(Screen parent, String id) {
		Minecraft mc = Minecraft.getInstance();
		CompoundTag stored;
		try {
			CompoundTag root = NbtIo.readCompressed(mc.getLevelSource().getBaseDir().resolve(id).resolve("level.dat"), NbtAccounter.unlimitedHeap());
			stored = root.getCompoundOrEmpty("Data").getCompoundOrEmpty("GameRules");
		} catch (Exception e) {
			fail(id, e);
			return;
		}
		GameRules rules = new GameRules(FeatureFlags.DEFAULT_FLAGS, new Dynamic<>(NbtOps.INSTANCE, stored));
		mc.setScreen(new EditGameRulesScreen(rules, result -> {
			result.ifPresent(r -> saveGameRules(id, r));
			mc.setScreen(parent);
			if (parent instanceof Hooks.ListHolder holder) holder.worldfolders$upToDate(); // our own write to level.dat
		}));
	}

	private static void saveGameRules(String id, GameRules rules) {
		// The access holds the session lock, so a world open in another game is never written.
		try (LevelStorageSource.LevelStorageAccess access = Minecraft.getInstance().getLevelSource().validateAndCreateAccess(id)) {
			Path file = access.getLevelPath(LevelResource.LEVEL_DATA_FILE);
			CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			CompoundTag data = root.getCompoundOrEmpty("Data");
			CompoundTag stored = data.getCompoundOrEmpty("GameRules");
			stored.merge(rules.createTag()); // merge keeps rules we don't list (experiments, other mods)
			data.put("GameRules", stored);
			root.put("Data", data);
			Path tmp = file.resolveSibling("level.dat_wf");
			NbtIo.writeCompressed(root, tmp);
			Files.copy(file, file.resolveSibling("level.dat_old"), StandardCopyOption.REPLACE_EXISTING);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			fail(id, e);
		}
	}

	private static void fail(String what, Throwable e) {
		Store.LOG.error("Not Only World Folders: operation on {} failed", what, e);
		SystemToast.onWorldAccessFailure(Minecraft.getInstance(), what);
	}

	/** Shows {@code dir} in the file explorer, made first if it isn't there yet (no screenshot taken so far). */
	static void openDir(Path dir) {
		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) { // the explorer will say so
		}
		Util.getPlatform().openPath(dir);
	}

	/** The saves folder button of the worlds screen: opens the folder shown now in the file explorer. */
	public static void openSaves() {
		openDir(Minecraft.getInstance().getLevelSource().getBaseDir());
	}

	/** Title screen "+": straight to the world creation screen, in the saves folder the worlds screen shows. */
	public static void createWorld(Screen screen) {
		Store.applySavesDir();
		CreateWorldScreen.openFresh(Minecraft.getInstance(), screen);
	}

	/** The screenshots button of the title screen, so a right click can be told to be on it. */
	public static @Nullable Button screenshotsButton;

	/** The screenshots folder the title screen button opens: this game's, or the instance picked with a right click. */
	public static Path screenshotsDir() {
		String dir = Store.config().screenshotsDir;
		return dir.isEmpty() ? Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots") : Path.of(dir);
	}

	public static void openScreenshots() {
		openDir(screenshotsDir());
	}

	public static Component screenshotsTooltip() {
		return tr("screenshots_tooltip", screenshotsDir().toAbsolutePath().normalize().toString());
	}

	/**
	 * Right click: if it is on the screenshots button, lists the instances under it; the one picked is the
	 * one whose screenshots the button opens from now on. Returns false when the click was not for the button.
	 */
	public static boolean screenshotsMenu() {
		Minecraft mc = Minecraft.getInstance();
		Button button = screenshotsButton;
		if (button == null || mc.screen == null || !mc.screen.children().contains(button) || !button.isMouseOver(Ui.mouseX(), Ui.mouseY())) return false;
		Path now = screenshotsDir().toAbsolutePath().normalize();
		List<Instances.Instance> all = instances();
		ContextMenu menu = ContextMenu.on(mc.screen);
		for (int i = 0; i < all.size(); i++) {
			Instances.Instance instance = all.get(i);
			Path shots = instance.saves().toAbsolutePath().normalize().resolveSibling("screenshots");
			boolean own = i == 0;
			menu.add(Component.literal((shots.equals(now) ? "✔ " : "    ") + instance.label()), () -> {
				Store.config().screenshotsDir = own ? "" : shots.toString();
				Store.save();
				button.setTooltip(Tooltip.create(screenshotsTooltip()));
			});
		}
		button.setTooltip(null); // it would show through the menu
		menu.onClose(() -> button.setTooltip(Tooltip.create(screenshotsTooltip()))).openAt(button.getX(), button.getY() + button.getHeight());
		return true;
	}
}
