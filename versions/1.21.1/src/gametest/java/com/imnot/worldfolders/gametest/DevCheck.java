package com.imnot.worldfolders.gametest;

import com.imnot.worldfolders.FolderRow;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.Presets;
import com.imnot.worldfolders.PromptScreen;
import com.imnot.worldfolders.ServerMenus;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import com.imnot.worldfolders.Ui;
import com.imnot.worldfolders.WorldMenus;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * No client gametest API before 1.21.4, so this drives the real game by hand: audits every mixin,
 * opens both screens and plays the mouse through the game's own MouseHandler (press, move, release:
 * calling Screen.mouseClicked directly would skip the dispatch a real click goes through), takes
 * screenshots (build/run/devCheck/screenshots) and quits. Keep the real mouse out of the window.
 * Run: ./gradlew runDevCheck
 */
public class DevCheck implements ClientModInitializer {
	private int step, ticks;
	private String key = "";

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft mc) {
		ticks++;
		switch (step) {
			case 0 -> {
				if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return; // wait for the loading overlay to finish
				MixinEnvironment.getCurrentEnvironment().audit(); // applies every mixin now; a broken one crashes here
				reset(Store.worlds());
				reset(Store.servers());
				Store.worlds().folderIcons.put("Survie", "minecraft:diamond");
				ServerList servers = new ServerList(mc);
				servers.load();
				if (servers.size() == 0) servers.add(new ServerData("Serveur A", "127.0.0.1:25999", ServerData.Type.OTHER), false);
				servers.save();
				mc.setScreen(new SelectWorldScreen(mc.screen));
				next();
			}
			case 1 -> {
				if (ticks < 40) return;
				if (worldRows(mc) < 1) throw new AssertionError("the dev check needs a world in build/run/devCheck/saves");
				shot(mc, "worlds");
				// Trash button twice (in and out): the screen must still hold exactly one world list.
				// (Entering an empty trash leaves it at once, so trash a world for the first press.)
				String world = Hooks.keyOf(list(mc).children().get(list(mc).children().size() - 1));
				Store.worlds().trash.add(world);
				for (int i = 0; i < 2; i++) click(mc, holder(mc).worldfolders$trashButton());
				Store.worlds().trash.clear();
				refresh(mc);
				long lists = mc.screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.screens.worldselection.WorldSelectionList).count();
				if (lists != 1 || Store.worlds().trashView) throw new AssertionError("trash toggle broke the screen: " + lists + " lists");
				pressSortButton(mc);
				next();
			}
			case 2 -> {
				if (ticks < 5) return;
				shot(mc, "sort_dropdown");
				mc.screen.keyPressed(256, 0, 0); // Escape: closes the dropdown only
				rightClick(mc, rowX(mc), rowY(mc, 1)); // the "Survie" folder row
				next();
			}
			case 3 -> {
				if (ticks < 5) return;
				shot(mc, "folder_menu");
				click(mc, rowX(mc) + 10, rowY(mc, 1) + 8); // first item of the menu, which opened at the mouse: "Open"
				if (!Store.worlds().current.equals("Survie")) throw new AssertionError("folder menu > open: in '" + Store.worlds().current + "'");
				// Now inside the empty folder, only ".." is listed: a right-click under it is on the background.
				rightClick(mc, rowX(mc), rowY(mc, 2));
				click(mc, rowX(mc) + 10, rowY(mc, 2) + 8); // "New subfolder"
				if (!(mc.screen instanceof PromptScreen)) throw new AssertionError("background menu > new subfolder: " + mc.screen);
				mc.screen.onClose();
				Store.worlds().current = "";
				refresh(mc);
				rightClick(mc, rowX(mc), rowY(mc, 2)); // first world
				next();
			}
			case 4 -> {
				if (ticks < 5) return;
				shot(mc, "world_menu");
				mc.screen.keyPressed(256, 0, 0); // closes the menu
				// Drag and drop: last world onto the first folder row.
				int last = list(mc).children().size() - 1;
				key = Hooks.keyOf(list(mc).children().get(last));
				press(mc, rowX(mc), rowY(mc, last));
				move(mc, rowX(mc), rowY(mc, 0));
				next();
			}
			case 5 -> {
				if (ticks < 8) return; // over 250 ms since the last press on this row: not a double-click
				String folder = ((FolderRow.Holder) list(mc).children().get(0)).row().path;
				release(mc, rowX(mc), rowY(mc, 0));
				if (!Store.worlds().folderOf(key).equals(folder)) throw new AssertionError("drag onto a folder: " + key + " in " + Store.worlds().folderOf(key));
				Store.worlds().items.clear();
				refresh(mc);
				// ...onto the trash button
				press(mc, rowX(mc), rowY(mc, list(mc).children().size() - 1));
				move(mc, centerX(holder(mc).worldfolders$trashButton()), 32);
				next();
			}
			case 6 -> {
				if (ticks < 8) return; // over 250 ms since the last press on this row: not a double-click
				shot(mc, "drag_to_trash");
				release(mc, centerX(holder(mc).worldfolders$trashButton()), 32);
				if (!Store.worlds().trash.contains(key)) throw new AssertionError("drag onto the trash: " + key + " not trashed");
				if (Store.worlds().trashView) throw new AssertionError("drag onto the trash opened the trash");
				Store.worlds().trash.clear();
				refresh(mc);
				// ...onto "New folder", from inside a folder: the new folder is its subfolder
				Store.worlds().move(key, "Survie");
				Store.worlds().expanded.add("Survie");
				refresh(mc); // rows: Colorée, Survie, the world, the other world
				press(mc, rowX(mc), rowY(mc, 2));
				move(mc, centerX(holder(mc).worldfolders$newFolderButton()), 32);
				next();
			}
			case 7 -> {
				if (ticks < 8) return; // over 250 ms since the last press on this row: not a double-click
				release(mc, centerX(holder(mc).worldfolders$newFolderButton()), 32);
				if (!(mc.screen instanceof PromptScreen prompt)) throw new AssertionError("drag onto New folder: no name prompt, " + mc.screen);
				prompt.children().stream().filter(c -> c instanceof EditBox).forEach(c -> ((EditBox) c).setValue("Drop"));
				prompt.keyPressed(257, 0, 0); // Enter
				if (!(mc.screen instanceof SelectWorldScreen) || !Store.worlds().folderOf(key).equals("Survie/Drop")) {
					throw new AssertionError("drag onto New folder: " + key + " in '" + Store.worlds().folderOf(key) + "', screen " + mc.screen);
				}
				next();
			}
			case 8 -> {
				if (ticks < 5) return;
				if (list(mc).children().stream().noneMatch(r -> r instanceof FolderRow.Holder h && "Survie/Drop".equals(h.row().path))) {
					throw new AssertionError("the new folder is not listed after the prompt");
				}
				reset(Store.worlds());
				refresh(mc);
				next();
			}
			case 9 -> {
				if (ticks < 5) return; // the "+" of the folder rows has been drawn
				shot(mc, "folder_plus");
				FolderRow survie = ((FolderRow.Holder) list(mc).children().get(1)).row();
				click(mc, survie.plusX() + 4, rowY(mc, 1));
				if (!(mc.screen instanceof CreateWorldScreen) || !"Survie".equals(Store.worlds().pendingFolder)) {
					throw new AssertionError("folder \"+\": screen " + mc.screen + ", expecting a world for " + Store.worlds().pendingFolder);
				}
				// Stands for the creation itself: a new world appears in the saves folder.
				copyWorld(mc, mc.getLevelSource().getBaseDir().resolve("PlusWorld"));
				mc.setScreen(new SelectWorldScreen(new TitleScreen()));
				next();
			}
			case 10 -> {
				if (ticks < 20) return;
				String folder = Store.worlds().folderOf("PlusWorld");
				deleteTree(mc.getLevelSource().getBaseDir().resolve("PlusWorld"));
				Store.worlds().forget("PlusWorld");
				if (!folder.equals("Survie") || Store.worlds().pendingFolder != null) {
					throw new AssertionError("world made from the \"+\" of Survie is in '" + folder + "', still expecting " + Store.worlds().pendingFolder);
				}
				// v2.2 title screen: the screenshots button (right click: another instance's) and "+" beside "Singleplayer".
				Store.config().screenshotsDir = mc.gameDirectory.toPath().resolve("elsewhere").toString();
				mc.setScreen(new TitleScreen());
				next();
			}
			case 11 -> {
				if (ticks < 40) return; // the title screen fades in
				Button shots = WorldMenus.screenshotsButton;
				if (shots == null || !mc.screen.children().contains(shots)) throw new AssertionError("no screenshots button on the title screen");
				rightClick(mc, centerX(shots), shots.getY() + 10);
				next();
			}
			case 12 -> {
				if (ticks < 5) return;
				shot(mc, "title_screenshots_menu");
				// First item of the dropdown ("This instance"), placed like ContextMenu does: under the button, kept on screen.
				Button shots = WorldMenus.screenshotsButton;
				int rows = Math.min(WorldMenus.instances().size(), (mc.screen.height - 6) / 14);
				click(mc, shots.getX() + 10, Math.max(0, Math.min(shots.getY() + 20, mc.screen.height - (rows * 14 + 6))) + 3 + 7);
				if (!Store.config().screenshotsDir.isEmpty()) throw new AssertionError("screenshots instance not chosen from the right click menu: " + Store.config().screenshotsDir);
				move(mc, centerX(shots), shots.getY() + 10);
				next();
			}
			case 13 -> {
				if (ticks < 15) return; // long enough for the tooltip
				shot(mc, "title_buttons");
				Button plus = (Button) mc.screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals("+")).findFirst().orElseThrow();
				if (plus.getY() != WorldMenus.screenshotsButton.getY()) throw new AssertionError("title buttons not on the same row");
				click(mc, plus); // v2.3: a dropdown, "Create a world" first
				click(mc, plus.getX() + 10, plus.getY() + 20 + 3 + 7);
				if (!(mc.screen instanceof CreateWorldScreen)) throw new AssertionError("title \"+\": screen " + mc.screen);
				// v2.3 world presets: the creation screen saves one instead of making a world, and is filled from it later.
				Presets.create(new TitleScreen());
				if (!(mc.screen instanceof CreateWorldScreen creation)) throw new AssertionError("new preset: screen " + mc.screen);
				creation.getUiState().setName("Preset A");
				creation.getUiState().setSeed("1234");
				click(mc, (Button) mc.screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals(Ui.tr("save_preset").getString())).findFirst().orElseThrow());
				if (mc.screen instanceof CreateWorldScreen || !Store.presets().containsKey("Preset A")) throw new AssertionError("preset not saved: screen " + mc.screen + ", presets " + Store.presets().keySet());
				Presets.use(new TitleScreen(), "Preset A");
				if (!(mc.screen instanceof CreateWorldScreen filled) || !filled.getUiState().getName().equals("Preset A") || !filled.getUiState().getSeed().equals("1234")) {
					throw new AssertionError("creation screen not filled with the preset: " + mc.screen);
				}
				// v2.3.1: the superflat customisation is part of the preset.
				var ui = filled.getUiState();
				ui.getNormalPresetList().stream().filter(type -> type.preset().is(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT)).findFirst().ifPresent(ui::setWorldType);
				var flat = ((net.minecraft.world.level.levelgen.FlatLevelSource) ui.getSettings().selectedDimensions().overworld()).settings();
				var custom = flat.withBiomeAndLayers(List.of(new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(1, net.minecraft.world.level.block.Blocks.BEDROCK),
						new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(7, net.minecraft.world.level.block.Blocks.SANDSTONE)), flat.structureOverrides(), flat.getBiome());
				ui.updateDimensions((registries, dimensions) -> dimensions.replaceOverworldGenerator(registries, new net.minecraft.world.level.levelgen.FlatLevelSource(custom)));
				ui.setName("Preset Flat");
				if (!Presets.save(null, ui)) throw new AssertionError("superflat preset not saved");
				Presets.use(new TitleScreen(), "Preset Flat");
				if (!(mc.screen instanceof CreateWorldScreen flatScreen) || !(flatScreen.getUiState().getSettings().selectedDimensions().overworld() instanceof net.minecraft.world.level.levelgen.FlatLevelSource source)
						|| source.settings().getLayersInfo().size() != 2) {
					throw new AssertionError("superflat layers not back with the preset: " + Store.presets().get("Preset Flat").flat);
				}
				Store.presets().clear();
				mc.setScreen(new SelectWorldScreen(new TitleScreen()));
				next();
			}
			case 14 -> {
				if (ticks < 20) return;
				if (worldRows(mc) < 2) throw new AssertionError("back on the default saves folder: " + worldRows(mc) + " worlds");
				mc.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
				next();
			}
			case 15 -> {
				if (ticks < 20) return;
				rightClick(mc, rowX(mc), rowY(mc, 0)); // first folder row
				next();
			}
			case 16 -> {
				if (ticks < 5) return;
				shot(mc, "server_folder_menu");
				mc.screen.keyPressed(256, 0, 0);
				rightClick(mc, rowX(mc), rowY(mc, 2)); // first server
				next();
			}
			case 17 -> {
				if (ticks < 5) return;
				shot(mc, "server_menu");
				mc.screen.keyPressed(256, 0, 0);
				if (!(list(mc).children().get(2) instanceof ServerSelectionList.OnlineServerEntry server)) throw new AssertionError("row 2 is not a server");
				key = ServerMenus.key(server.getServerData());
				press(mc, rowX(mc), rowY(mc, 2));
				move(mc, centerX(holder(mc).worldfolders$trashButton()), 16);
				next();
			}
			case 18 -> {
				if (ticks < 8) return; // over 250 ms since the last press on this row: not a double-click
				release(mc, centerX(holder(mc).worldfolders$trashButton()), 16);
				if (!Store.servers().trash.contains(key)) throw new AssertionError("drag a server onto the trash: not trashed");
				reset(Store.servers());
				Store.save();
				next();
			}
			// --- v2.4 ---
			case 19 -> {
				mc.setScreen(new TitleScreen());
				next();
			}
			case 20 -> {
				if (ticks < 10) return;
				Button solo = WorldMenus.soloButton;
				rightClick(mc, centerX(solo), solo.getY() + 10); // right click on Singleplayer: the instance dropdown
				next();
			}
			case 21 -> {
				if (ticks < 3) return;
				if (((Hooks.Overlay) mc.screen).worldfolders$menu() == null) throw new AssertionError("right click on Singleplayer: no instance dropdown");
				shot(mc, "solo_instance_menu");
				mc.screen.keyPressed(256, 0, 0);
				if (!(mc.screen instanceof TitleScreen) || ((Hooks.Overlay) mc.screen).worldfolders$menu() != null) throw new AssertionError("Escape must close the dropdown only");
				key = firstWorld(mc);
				// The statistics of a world, if it was ever played.
				if (Files.isDirectory(mc.getLevelSource().getBaseDir().resolve(key).resolve("stats"))) WorldMenus.showStats(mc.screen, key, key);
				next();
			}
			case 22 -> {
				if (ticks < 5) return;
				if (Files.isDirectory(mc.getLevelSource().getBaseDir().resolve(key).resolve("stats"))) {
					if (!(mc.screen instanceof StatsScreen) || mc.screen.children().stream().noneMatch(c -> c instanceof AbstractSelectionList<?> l && !l.children().isEmpty())) {
						throw new AssertionError("statistics of the world not listed: " + mc.screen);
					}
					shot(mc, "world_stats");
				} else {
					Store.LOG.info("World Folders dev check: {} has no stats yet, statistics screen not checked", key);
				}
				// --- v2.5: the resource pack screen ---
				try {
					Path game = mc.gameDirectory.toPath();
					for (Path pack : List.of(game.resolve("packInstance/resourcepacks/OtherPack"), game.resolve("resourcepacks/OwnPack"), game.resolve("resourcepacks/PackB"))) {
						Files.createDirectories(pack);
						Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"test\"}}");
					}
					Files.createDirectories(game.resolve("packInstance/saves"));
					String saves = game.resolve("packInstance/saves").toString();
					if (!Store.config().otherSavesDirs.contains(saves)) Store.config().otherSavesDirs.add(saves);
				} catch (IOException e) {
					throw new AssertionError(e);
				}
				// A folder and two favorites: they are the first three rows, whatever packs this computer has.
				Space packs = Store.packs();
				reset(packs);
				packs.folders.remove("&6Colorée");
				packs.favorites.addAll(List.of("file/OwnPack", "file/PackB"));
				var repository = mc.getResourcePackRepository();
				selectedBefore = List.copyOf(repository.getSelectedIds());
				repository.reload();
				List<String> selected = new java.util.ArrayList<>(selectedBefore);
				selected.add("file/OtherPack");
				repository.setSelected(selected);
				mc.setScreen(new PackSelectionScreen(repository, r -> {
				}, mc.getResourcePackDirectory(), net.minecraft.network.chat.Component.literal("Packs")));
				next();
			}
			case 23 -> {
				if (ticks < 10) return;
				shot(mc, "packs");
				if (mc.screen.children().stream().noneMatch(c -> c instanceof TransferableSelectionList l && l != list(mc)
						&& l.children().stream().anyMatch(e -> e.getPackId().equals("file/OtherPack")))) throw new AssertionError("the pack of another instance is not selected any more");
				if (!(list(mc).children().get(0) instanceof FolderRow.Holder) || !"file/OwnPack".equals(Hooks.keyOf(list(mc).children().get(1))) || !"file/PackB".equals(Hooks.keyOf(list(mc).children().get(2)))) {
					throw new AssertionError("packs not arranged: folder, then the favorites");
				}
				// Trash button in and out, like the worlds: still two lists, and the favorites back.
				packs().favorites.remove("file/PackB");
				packs().trash.add("file/PackB");
				refresh(mc);
				click(mc, holder(mc).worldfolders$trashButton());
				if (!packs().trashView || !"file/PackB".equals(Hooks.keyOf(list(mc).children().get(0)))) throw new AssertionError("trash view of the packs");
				click(mc, holder(mc).worldfolders$trashButton());
				packs().trash.clear();
				packs().favorites.add("file/PackB");
				refresh(mc);
				long lists = mc.screen.children().stream().filter(c -> c instanceof TransferableSelectionList).count();
				if (lists != 2 || packs().trashView) throw new AssertionError("pack trash toggle broke the screen: " + lists + " lists");
				rightClick(mc, rowX(mc), packRowY(1));
				next();
			}
			case 24 -> {
				if (ticks < 5) return;
				shot(mc, "pack_menu");
				if (((Hooks.Overlay) mc.screen).worldfolders$menu() == null) throw new AssertionError("no menu on a right-clicked pack");
				click(mc, rowX(mc) + 10, packRowY(1) + 8 + 2 * 14); // third item: "Remove from favorites"
				if (packs().favorites.contains("file/OwnPack")) throw new AssertionError("pack menu > unfavorite");
				// PackB, the only favorite left, is row 1: drag it onto the folder.
				press(mc, rowX(mc), packRowY(1));
				move(mc, rowX(mc), packRowY(0));
				next();
			}
			case 25 -> {
				if (ticks < 8) return;
				shot(mc, "pack_drag");
				release(mc, rowX(mc), packRowY(0));
				if (!"Survie".equals(packs().items.get("file/PackB"))) throw new AssertionError("pack dragged onto a folder: " + packs().items);
				mc.getResourcePackRepository().setSelected(selectedBefore);
				mc.setScreen(new OptionsScreen(new TitleScreen(), mc.options));
				next();
			}
			case 26 -> { // v2.6: the instance button of the options screen; the shader packs of every instance (with Iris)
				if (ticks < 5) return;
				shot(mc, "options_instance");
				if (mc.screen.children().stream().noneMatch(c -> c instanceof Button b && b.getMessage().getString().startsWith("\u25bc"))) throw new AssertionError("no instance button on the options screen");
				if (FabricLoader.getInstance().isModLoaded("iris")) IrisCheck.open(mc);
				next();
			}
			case 27 -> {
				if (ticks < 10) return;
				if (FabricLoader.getInstance().isModLoaded("iris")) {
					shot(mc, "shaders");
					IrisCheck.check(mc);
				}
				mc.setScreen(new TitleScreen());
				Store.LOG.info("World Folders dev check done");
				mc.stop();
				next();
			}
			default -> {
			}
		}
	}

	private void next() {
		step++;
		ticks = 0;
	}

	/** Two folders and nothing else, whatever earlier runs left. */
	private static void reset(Space s) {
		s.folders.clear();
		s.folders.add("Survie");
		s.folders.add("&6Colorée");
		s.items.clear();
		s.trash.clear();
		s.favorites.clear();
		s.expanded.clear();
		s.order.clear();
		s.sort = Space.Sort.DEFAULT;
		s.current = "";
		s.trashView = false;
	}

	private static Hooks.ListHolder holder(Minecraft mc) {
		return (Hooks.ListHolder) mc.screen;
	}

	private static AbstractSelectionList<?> list(Minecraft mc) {
		return holder(mc).worldfolders$list();
	}

	private static void refresh(Minecraft mc) {
		((Hooks.OrganizedList) list(mc)).worldfolders$refresh();
	}

	private static long worldRows(Minecraft mc) {
		return list(mc).children().stream().filter(r -> Hooks.keyOf(r) != null).count();
	}

	private static double rowX(Minecraft mc) {
		return list(mc).getRowLeft() + 80;
	}

	/** Rows are 36 high under the list's top (getY); list not scrolled. */
	private static double rowY(Minecraft mc, int index) {
		return list(mc).getY() + 4 + index * 36 + 12;
	}

	private static double centerX(Button b) {
		return b.getX() + b.getWidth() / 2.0;
	}

	/** A copy of one world of the saves folder, as another instance's world or a world just created. */
	private static void copyWorld(Minecraft mc, Path target) {
		try (var worlds = Files.list(mc.getLevelSource().getBaseDir())) {
			Path world = worlds.filter(w -> Files.exists(w.resolve("level.dat"))).findFirst().orElseThrow();
			Files.createDirectories(target);
			try (var files = Files.list(world)) {
				for (Path p : (Iterable<Path>) files::iterator) {
					if (Files.isRegularFile(p) && !p.getFileName().toString().equals("session.lock")) Files.copy(p, target.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void deleteTree(Path dir) {
		try (var files = Files.walk(dir)) {
			for (Path p : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}


	// --- v2.5 ---

	private List<String> selectedBefore = List.of();

	private static Space packs() {
		return Store.packs();
	}

	/** Pack rows are 36 high under the list top (33) and its title line (13); list not scrolled. */
	private static double packRowY(int index) {
		return 33 + 4 + 13 + index * 36 + 12;
	}

	// --- v2.4 ---


	private static String firstWorld(Minecraft mc) {
		try (var worlds = Files.list(mc.getLevelSource().getBaseDir())) {
			return worlds.filter(w -> Files.exists(w.resolve("level.dat"))).map(w -> w.getFileName().toString()).sorted().findFirst().orElseThrow();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	// --- the real mouse: MouseHandler's GLFW callbacks ---

	private static final Method ON_MOVE = handler("onMove", long.class, double.class, double.class);
	private static final Method ON_PRESS = handler("onPress", long.class, int.class, int.class, int.class);

	private static Method handler(String name, Class<?>... args) {
		try {
			Method m = MouseHandler.class.getDeclaredMethod(name, args);
			m.setAccessible(true);
			return m;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void call(Method m, Object... args) {
		try {
			m.invoke(Minecraft.getInstance().mouseHandler, args);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("mouse handler failed", e.getCause() != null ? e.getCause() : e);
		}
	}

	private static void move(Minecraft mc, double x, double y) {
		Window w = mc.getWindow();
		call(ON_MOVE, w.getWindow(), x * w.getScreenWidth() / w.getGuiScaledWidth(), y * w.getScreenHeight() / w.getGuiScaledHeight());
	}

	private static void press(Minecraft mc, double x, double y) {
		move(mc, x, y);
		call(ON_PRESS, mc.getWindow().getWindow(), 0, 1, 0);
	}

	private static void release(Minecraft mc, double x, double y) {
		move(mc, x, y);
		call(ON_PRESS, mc.getWindow().getWindow(), 0, 0, 0);
	}

	private static void click(Minecraft mc, double x, double y) {
		press(mc, x, y);
		release(mc, x, y);
	}

	private static void click(Minecraft mc, Button b) {
		click(mc, centerX(b), b.getY() + b.getHeight() / 2.0);
	}

	private static void rightClick(Minecraft mc, double x, double y) {
		move(mc, x, y);
		call(ON_PRESS, mc.getWindow().getWindow(), 1, 1, 0);
		call(ON_PRESS, mc.getWindow().getWindow(), 1, 0, 0);
	}

	private static void pressSortButton(Minecraft mc) {
		for (var child : List.copyOf(mc.screen.children())) {
			if (child instanceof Button b && b.getMessage().getString().isEmpty()) {
				click(mc, b);
				return;
			}
		}
		throw new AssertionError("no sort button");
	}

	private static void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> {});
	}
	/** The shaders screen of Iris: its own class, loaded only when Iris is there. */
	private static final class IrisCheck {
		static void open(Minecraft mc) {
			Path other = mc.gameDirectory.toPath().resolve("optInstance");
			try {
				Files.createDirectories(other.resolve("saves"));
				Files.createDirectories(other.resolve("shaderpacks/OtherShader/shaders"));
			} catch (IOException e) {
				throw new AssertionError(e);
			}
			Store.config().otherSavesDirs.add(other.resolve("saves").toString());
			mc.setScreen(new net.irisshaders.iris.gui.screen.ShaderPackScreen(new TitleScreen()));
		}

		static void check(Minecraft mc) {
			String path = mc.gameDirectory.toPath().resolve("optInstance/shaderpacks/OtherShader").toAbsolutePath().normalize().toString();
			boolean listed = mc.screen.children().stream().anyMatch(c -> c instanceof net.irisshaders.iris.gui.element.ShaderPackSelectionList l
					&& l.children().stream().anyMatch(e -> e instanceof net.irisshaders.iris.gui.element.ShaderPackSelectionList.ShaderPackEntry p && p.getPackName().equals(path)));
			Store.config().otherSavesDirs.removeIf(dir -> dir.contains("optInstance"));
			Store.save();
			if (!listed) throw new AssertionError("the shader pack of another instance is not listed");
		}
	}
}
