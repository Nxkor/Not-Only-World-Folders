package com.imnot.worldfolders.gametest;

import com.imnot.worldfolders.ConfigScreen;
import com.imnot.worldfolders.Ui;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import com.imnot.worldfolders.FolderRow;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.PromptScreen;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import com.imnot.worldfolders.WorldMenus;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.client.gui.screens.worldselection.WorldCreationGameRulesScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.level.storage.LevelSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.lwjgl.glfw.GLFW;

/**
 * Drives the real UI: folders, context menu, drag and drop, expand/browse, trash, favorites,
 * servers and the config screen. Screenshots land in build/run/clientGameTest/screenshots.
 */
public class WorldFoldersClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try {
			run(ctx);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void run(ClientGameTestContext ctx) throws Exception {
		for (int i = 0; i < 3; i++) {
			try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
				ctx.waitTicks(20); // let the world load before closing it
			}
		}

		// --- singleplayer ---
		ctx.setScreen(() -> new SelectWorldScreen(new TitleScreen()));
		ctx.waitTicks(20);
		ctx.takeScreenshot("01_worlds");
		check(ctx.computeOnClient(mc -> WorldMenus.playSeconds("New World") > 0), "play time read from the world's stats");

		ctx.clickScreenButton("worldfolders.new_folder");
		ctx.waitForScreen(PromptScreen.class);
		ctx.getInput().typeChars("Survie");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		ctx.waitForScreen(SelectWorldScreen.class);
		ctx.waitTicks(5);
		ctx.takeScreenshot("02_folder_created");
		check(ctx.computeOnClient(mc -> Store.worlds().folders.contains("Survie")), "folder created");
		check(rowPath(ctx, 0).equals("Survie"), "new folder shown without leaving the screen");

		cursorOnRow(ctx, 1);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(2);
		ctx.takeScreenshot("03_world_menu");
		clickMenuItem(ctx, 3, 11); // "favorite"
		check(ctx.computeOnClient(mc -> Store.worlds().favorites.size() == 1), "favorite from menu");

		// drag the world on row 2 onto the folder (row 0)
		cursorOnRow(ctx, 2);
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		moveToRow(ctx, 0);
		ctx.waitTicks(2);
		ctx.takeScreenshot("04_dragging");
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> Store.worlds().items.containsValue("Survie")), "drag and drop moved the world");

		cursorOnRow(ctx, 0);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); // single click: expand
		ctx.waitTicks(10);
		ctx.takeScreenshot("05_expanded");
		check(ctx.computeOnClient(mc -> Store.worlds().expanded.contains("Survie")), "expand");

		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); // double click: browse into
		ctx.waitTicks(2);
		ctx.takeScreenshot("06_inside_folder");
		check(ctx.computeOnClient(mc -> Store.worlds().current.equals("Survie")), "double click browses");

		cursorOnRow(ctx, 0);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); // ".." back to root
		ctx.waitTicks(2);

		// trash the last (non favorite) world with vanilla "Delete"
		int last = ctx.computeOnClient(mc -> ((Hooks.ListHolder) mc.screen).worldfolders$list().children().size() - 1);
		cursorOnRow(ctx, last);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		ctx.clickScreenButton("selectWorld.delete");
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> Store.worlds().trash.size() == 1), "delete goes to trash");
		ctx.takeScreenshot("07_after_trash");

		// User report: trash view via the button, restore from its menu, leave the trash: every row must still react.
		clickButton(ctx, "worldfolders.trash");
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> Store.worlds().trashView && ((Hooks.ListHolder) mc.screen).worldfolders$list().children().size() == 1), "trash view by button");
		cursorOnRow(ctx, 0);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		clickMenuItem(ctx, 0, 2); // restore
		check(ctx.computeOnClient(mc -> Store.worlds().trash.isEmpty()), "restore from the trash");
		check(ctx.computeOnClient(mc -> !Store.worlds().trashView), "v2.1: restoring the last trashed world leaves the trash");
		int restored = ctx.computeOnClient(mc -> ((Hooks.ListHolder) mc.screen).worldfolders$list().children().size() - 1);
		cursorOnRow(ctx, restored);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> {
			var list = ((Hooks.ListHolder) mc.screen).worldfolders$list();
			return !Store.worlds().trashView && list.getSelected() == list.children().get(restored);
		}), "rows still react after leaving the trash");
		ctx.takeScreenshot("07a_after_leaving_trash");
		ctx.clickScreenButton("selectWorld.delete"); // back to the trash for the next steps
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> Store.worlds().trash.size() == 1), "trash again");

		// --- file operations on the favorite world (row 2; its menu has no "trash" item) ---
		cursorOnRow(ctx, 2);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		clickMenuItem(ctx, 6, 10); // copy
		cursorOnRow(ctx, 2);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		clickMenuItem(ctx, 7, 11); // paste (now listed)
		ctx.waitTicks(40);
		ctx.waitForScreen(SelectWorldScreen.class);
		check(ctx.computeOnClient(mc -> worldNames(mc).stream().filter(n -> n.endsWith("(copy)")).count() == 1), "paste copied the world");

		cursorOnRow(ctx, 2);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		clickMenuItem(ctx, 1, 11); // rename
		ctx.waitForScreen(PromptScreen.class);
		ctx.getInput().typeChars(" &aRenamed");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		ctx.waitForScreen(SelectWorldScreen.class);
		ctx.waitTicks(20);
		check(ctx.computeOnClient(mc -> worldNames(mc).stream().anyMatch(n -> n.endsWith(" §aRenamed"))), "rename with a colour code");

		cursorOnRow(ctx, 2);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		clickMenuItem(ctx, 9, 11); // game rules
		ctx.waitForScreen(WorldCreationGameRulesScreen.class);
		ctx.takeScreenshot("07b_gamerules");
		ctx.clickScreenButton("gui.done");
		ctx.waitForScreen(SelectWorldScreen.class);
		check(ctx.computeOnClient(mc -> worldNames(mc).size() == 4), "every level.dat still readable after game rules save");

		// --- v2: folder icon and description from the folder menu (open, new, paste here, rename, icon, description, delete x2) ---
		cursorOnRow(ctx, 0);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		clickMenuItem(ctx, 4, 8); // icon
		ctx.waitForScreen(PromptScreen.class);
		ctx.getInput().typeChars("mine");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_TAB); // v2.1: completes the namespace first
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> promptText(mc).equals("minecraft:")), "icon suggestion completes the namespace");
		ctx.getInput().typeChars("dia");
		ctx.waitTicks(2);
		ctx.takeScreenshot("07c1_icon_suggestions");
		ctx.getInput().typeChars("mond");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		ctx.waitForScreen(SelectWorldScreen.class);
		check(ctx.computeOnClient(mc -> "minecraft:diamond".equals(Store.worlds().folderIcons.get("Survie"))), "folder icon");
		cursorOnRow(ctx, 0);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(2);
		ctx.takeScreenshot("debug_before_description");
		clickMenuItem(ctx, 5, 8); // description
		ctx.waitForScreen(PromptScreen.class);
		ctx.getInput().typeChars("&bMes mondes &lsurvie");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		ctx.waitForScreen(SelectWorldScreen.class);
		check(ctx.computeOnClient(mc -> Store.worlds().folderDescriptions.containsKey("Survie")), "folder description");
		cursorOnRow(ctx, 0);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		clickMenuItem(ctx, 3, 8); // rename folder
		ctx.waitForScreen(PromptScreen.class);
		ctx.runOnClient(mc -> mc.screen.children().stream().filter(c -> c instanceof EditBox).forEach(c -> ((EditBox) c).setValue("&6Survie")));
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		ctx.waitForScreen(SelectWorldScreen.class);
		ctx.waitTicks(2);
		check(rowPath(ctx, 0).equals("&6Survie"), "renamed folder shown without leaving the screen");
		cursorOnRow(ctx, 0);
		ctx.waitTicks(3);
		ctx.takeScreenshot("07d_folder_icon_colour_description");
		check(ctx.computeOnClient(mc -> Store.worlds().folderIcons.containsKey("&6Survie")), "icon follows the folder rename");

		// sort dropdown: "Most recent first" (4th item)
		clickLiteralButton(ctx, "");
		ctx.waitTicks(2);
		ctx.takeScreenshot("07e_sort_dropdown");
		clickDropdownItem(ctx, "", 3, 7);
		check(ctx.computeOnClient(mc -> Store.worlds().sort == Space.Sort.NEWEST), "sort from the dropdown");
		ctx.runOnClient(mc -> {
			Store.worlds().sort = Space.Sort.FAVORITES;
			((Hooks.OrganizedList) ((Hooks.ListHolder) mc.screen).worldfolders$list()).worldfolders$refresh();
		});
		ctx.waitTicks(2);
		ctx.takeScreenshot("07f_favorites_only");
		check(ctx.computeOnClient(mc -> ((Hooks.ListHolder) mc.screen).worldfolders$list().children().stream()
				.allMatch(e -> Hooks.keyOf(e) == null || Store.worlds().favorites.contains(Hooks.keyOf(e)))), "favorites filter");
		ctx.runOnClient(mc -> Store.worlds().sort = Space.Sort.DEFAULT);

		// --- v2.1 drag and drop: between rows, right click cancels, hovering a folder opens it, trash and "New folder" ---
		ctx.runOnClient(mc -> {
			Store.worlds().favorites.clear();
			Store.worlds().expanded.clear();
			refresh(mc);
		});
		ctx.waitTicks(2); // rows: [0] folder, [1] x, [2] y
		// --- 2.1.4: the "+" of a folder row opens the world creation; the world made lands in that folder ---
		String plusFolder = ctx.computeOnClient(mc -> ((FolderRow.Holder) ((Hooks.ListHolder) mc.screen).worldfolders$list().children().get(0)).row().path);
		double[] plus = ctx.computeOnClient(mc -> {
			AbstractSelectionList<?> list = ((Hooks.ListHolder) mc.screen).worldfolders$list();
			double scale = mc.getWindow().getGuiScale();
			return new double[]{(((FolderRow.Holder) list.children().get(0)).row().plusX() + 4) * scale, (list.getRowTop(0) + 12) * scale};
		});
		ctx.waitTicks(6);
		ctx.getInput().setCursorPos(plus[0], plus[1]);
		ctx.waitTicks(3);
		ctx.takeScreenshot("11a_folder_plus");
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitForScreen(net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.class);
		check(ctx.computeOnClient(mc -> plusFolder.equals(Store.worlds().pendingFolder)), "folder + : a world is expected for the folder");
		ctx.runOnClient(mc -> { // stands for the creation itself: a new world appears in the saves folder
			Path made = mc.getLevelSource().getBaseDir().resolve("PlusWorld");
			Files.createDirectories(made);
			try (var files = Files.list(mc.getLevelSource().getBaseDir().resolve("New World"))) {
				for (Path p : (Iterable<Path>) files::iterator) if (Files.isRegularFile(p) && !p.getFileName().toString().equals("session.lock")) Files.copy(p, made.resolve(p.getFileName()));
			}
			mc.setScreen(new SelectWorldScreen(new TitleScreen()));
		});
		ctx.waitTicks(20);
		String plusLanded = ctx.computeOnClient(mc -> Store.worlds().folderOf("PlusWorld") + "|" + Store.worlds().pendingFolder);
		ctx.runOnClient(mc -> { // the copy goes away: the rest of the test counts the worlds
			try (var files = Files.walk(mc.getLevelSource().getBaseDir().resolve("PlusWorld"))) {
				for (Path p : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
			}
			Store.worlds().forget("PlusWorld");
			mc.setScreen(new SelectWorldScreen(new TitleScreen()));
		});
		ctx.waitTicks(20);
		check(plusLanded.equals(plusFolder + "|null"), "the world made from the + of " + plusFolder + " is in: " + plusLanded);

		// --- 2.1.2: a refresh moves the rows instead of making them again; the background menu works beside a rival mod ---
		check(ctx.computeOnClient(mc -> {
			AbstractSelectionList<?> list = ((Hooks.ListHolder) mc.screen).worldfolders$list();
			Object row = list.children().get(1);
			refresh(mc);
			return list.children().get(1) == row;
		}), "a refresh re-uses the world rows");
		double[] empty = ctx.computeOnClient(mc -> {
			AbstractSelectionList<?> list = ((Hooks.ListHolder) mc.screen).worldfolders$list();
			double scale = mc.getWindow().getGuiScale(), ex = list.getRowLeft() + 80, ey = list.getBottom() - 4;
			if (((Hooks.OrganizedList) list).worldfolders$rowAt(ex, ey) != null) throw new AssertionError("no empty space under the rows");
			return new double[]{ex * scale, ey * scale};
		});
		ctx.waitTicks(6);
		ctx.getInput().setCursorPos(empty[0], empty[1]);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.takeScreenshot("11b_background_menu");
		clickMenuItem(ctx, 0, 2); // "New subfolder"
		ctx.waitForScreen(PromptScreen.class);
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitForScreen(SelectWorldScreen.class);
		ctx.waitTicks(2);
		String x = keyAt(ctx, 1), y = keyAt(ctx, 2);
		cursorOnRow(ctx, 2);
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		moveToRow(ctx, 1);
		ctx.waitTicks(3);
		ctx.takeScreenshot("12_drag_between_rows");
		check(keyAt(ctx, 1).equals(y) && keyAt(ctx, 2).equals(x), "live placement while dragging");
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> Store.worlds().order.indexOf(y) < Store.worlds().order.indexOf(x)), "order kept after the drop");

		cursorOnRow(ctx, 2);
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		moveToRow(ctx, 1);
		ctx.waitTicks(3);
		check(keyAt(ctx, 1).equals(x), "second drag placed live");
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(2);
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		check(keyAt(ctx, 1).equals(y) && keyAt(ctx, 2).equals(x) && ctx.computeOnClient(mc -> mc.screen instanceof SelectWorldScreen), "right click cancels the drag");

		cursorOnRow(ctx, 1);
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		moveToRow(ctx, 0);
		ctx.waitFor(mc -> Store.worlds().expanded.contains("&6Survie"), 2000);
		ctx.waitTicks(2); // rows: [0] folder, [1] its world, [2] y (dragged), [3] x
		moveToRow(ctx, 1);
		ctx.waitTicks(3);
		ctx.takeScreenshot("13_drag_into_opened_folder");
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> Store.worlds().folderOf(y).equals("&6Survie")) && keyAt(ctx, 1).equals(y), "hover opens the folder, drop between its worlds");

		cursorOnRow(ctx, 3);
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		moveToRow(ctx, 2);
		double[] trash = buttonPos(ctx, "worldfolders.trash");
		ctx.getInput().setCursorPos(trash[0], trash[1]);
		ctx.waitTicks(3);
		ctx.takeScreenshot("14_drag_on_trash");
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> Store.worlds().trash.contains(x)), "drop on the trash");

		cursorOnRow(ctx, 1);
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		moveToRow(ctx, 2);
		double[] newFolder = buttonPos(ctx, "worldfolders.new_folder");
		ctx.getInput().setCursorPos(newFolder[0], newFolder[1]);
		ctx.waitTicks(2);
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitForScreen(PromptScreen.class);
		ctx.getInput().typeChars("Drop");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		ctx.waitForScreen(SelectWorldScreen.class);
		ctx.waitTicks(5);
		ctx.takeScreenshot("15_dropped_on_new_folder");
		check(ctx.computeOnClient(mc -> Store.worlds().folderOf(y).equals("&6Survie/Drop")), "drop on \"New folder\" makes a subfolder of the folder the world was in");
		ctx.runOnClient(mc -> { // the rest of the test wants "Drop" at the root
			Store.worlds().movePrefix("&6Survie/Drop", "Drop");
			refresh(mc);
		});
		ctx.waitTicks(2);

		// --- v2.2: auto refresh, multi-selection, group drag, auto scroll, recursive counter, size ---
		ctx.runOnClient(mc -> { // four worlds copied in by hand, as from the file explorer
			Store.worlds().expanded.clear();
			Path saves = mc.getLevelSource().getBaseDir();
			for (int i = 1; i <= 4; i++) {
				Files.createDirectories(saves.resolve("Copy" + i));
				Files.copy(saves.resolve("New World").resolve("level.dat"), saves.resolve("Copy" + i).resolve("level.dat"));
			}
		});
		ctx.waitFor(mc -> Hooks.keys(((Hooks.ListHolder) mc.screen).worldfolders$list()).contains("Copy4"), 2000);
		ctx.waitTicks(2); // rows: [0] Survie, [1] Drop, [2..5] Copy1-4
		ctx.takeScreenshot("16_auto_refresh");
		check(keyAt(ctx, 2).startsWith("Copy") && keyAt(ctx, 5).startsWith("Copy"), "worlds added outside the game show up by themselves");

		String c1 = keyAt(ctx, 2), c2 = keyAt(ctx, 3), c3 = keyAt(ctx, 4);
		ctx.getInput().holdKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		cursorOnRow(ctx, 2);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		cursorOnRow(ctx, 3);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> Store.worlds().selection.equals(new java.util.HashSet<>(List.of(c1, c2)))), "ctrl + click selects several worlds");
		ctx.runOnClient(mc -> ((Hooks.ListHolder) mc.screen).worldfolders$list().setScrollAmount(100)); // row 4 is below the small test window
		ctx.waitTicks(2);
		ctx.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
		cursorOnRow(ctx, 4);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
		ctx.runOnClient(mc -> ((Hooks.ListHolder) mc.screen).worldfolders$list().setScrollAmount(0));
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> Store.worlds().selection.equals(new java.util.HashSet<>(List.of(c2, c3)))), "shift + click selects a range from the last ctrl-clicked world: " + ctx.computeOnClient(mc -> Store.worlds().selection + " rows " + Hooks.keys(((Hooks.ListHolder) mc.screen).worldfolders$list())) + " c2=" + c2 + " c3=" + c3);
		cursorOnRow(ctx, 3);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(2);
		ctx.takeScreenshot("17_multi_selection_menu");
		clickMenuItem(ctx, 0, 5); // favorite (2)
		check(ctx.computeOnClient(mc -> Store.worlds().favorites.containsAll(List.of(c2, c3))), "group favorite from the menu");
		ctx.runOnClient(mc -> {
			Store.worlds().favorites.removeAll(List.of(c2, c3));
			refresh(mc);
		});
		ctx.waitTicks(2);

		cursorOnRow(ctx, 3); // drag the selection onto the "Drop" folder
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		moveToRow(ctx, 1);
		ctx.waitTicks(3);
		ctx.takeScreenshot("18_group_drag");
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> Store.worlds().folderOf(c2).equals("Drop") && Store.worlds().folderOf(c3).equals("Drop")), "the selection dragged together into a folder");
		ctx.runOnClient(mc -> { // a world in a subfolder of Survie: Survie shows "(direct · with subfolders)"
			Store.worlds().move(c1, Store.worlds().createFolder("&6Survie", "Sub"));
			refresh(mc);
		});
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> ((FolderRow.Holder) ((Hooks.ListHolder) mc.screen).worldfolders$list().children().get(0)).row().label().getString().matches(".*\\(\\d+ · \\d+\\)")),
				"recursive counter: " + ctx.computeOnClient(mc -> ((FolderRow.Holder) ((Hooks.ListHolder) mc.screen).worldfolders$list().children().get(0)).row().label().getString()));
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> mc.screen instanceof SelectWorldScreen && Store.worlds().selection.isEmpty()), "escape clears the selection, the screen stays");

		ctx.runOnClient(mc -> { // enough rows to scroll: open both folders
			Store.worlds().expanded.addAll(List.of("&6Survie", "Drop"));
			refresh(mc);
		});
		ctx.waitTicks(2);
		cursorOnRow(ctx, 2);
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		ctx.getInput().setCursorPos(ctx.computeOnClient(mc -> (((Hooks.ListHolder) mc.screen).worldfolders$list().getRowLeft() + 80) * mc.getWindow().getGuiScale()),
				ctx.computeOnClient(mc -> (((Hooks.ListHolder) mc.screen).worldfolders$list().getBottom() - 3) * mc.getWindow().getGuiScale()));
		ctx.waitFor(mc -> ((Hooks.ListHolder) mc.screen).worldfolders$list().scrollAmount() > 20, 2000);
		ctx.takeScreenshot("19_auto_scroll");
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); // cancel
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);

		cursorOnRow(ctx, 2); // world size in the tooltip, measured in the background
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitFor(mc -> WorldMenus.sizeOf(keyAt(mc, 2)) > 0, 400);
		ctx.getInput().moveCursor(2, 0);
		ctx.waitTicks(2);
		ctx.takeScreenshot("20_size_in_tooltip");
		ctx.runOnClient(mc -> Store.worlds().expanded.clear());

		// --- another saves folder (e.g. another launcher's instance) ---
		ctx.runOnClient(mc -> {
			Path other = mc.gameDirectory.toPath().resolve("otherSaves");
			Files.createDirectories(other.resolve("Imported"));
			try (var files = Files.list(mc.getLevelSource().getBaseDir().resolve("New World"))) {
				for (Path p : (Iterable<Path>) files::iterator) if (Files.isRegularFile(p)) Files.copy(p, other.resolve("Imported").resolve(p.getFileName()));
			}
			Store.config().otherSavesDirs.add(other.toString());
		});
		literalButton(ctx, "📂"); // v2.2 saves folder button is there (a click would open the file explorer)
		clickLiteralButton(ctx, "▼"); // v2.2 instance dropdown
		ctx.waitTicks(2);
		ctx.takeScreenshot("21_instance_dropdown");
		int otherIndex = ctx.computeOnClient(mc -> {
			List<Path> all = WorldMenus.savesInstances().stream().map(i -> i.saves().toAbsolutePath().normalize()).toList();
			return all.indexOf(mc.gameDirectory.toPath().resolve("otherSaves").toAbsolutePath().normalize());
		});
		check(otherIndex > 0, "picked folder listed in the instance dropdown");
		clickDropdownItem(ctx, "▼", otherIndex, ctx.computeOnClient(mc -> WorldMenus.savesInstances().size() + 1));
		ctx.waitTicks(20);
		ctx.takeScreenshot("07c_other_saves");
		check(ctx.computeOnClient(mc -> worldNames(mc).size() == 1 && Hooks.keys(((Hooks.ListHolder) mc.screen).worldfolders$list()).equals(List.of("Imported"))), "other instance chosen from the dropdown");
		clickLiteralButton(ctx, "▼");
		clickDropdownItem(ctx, "▼", 0, ctx.computeOnClient(mc -> WorldMenus.savesInstances().size() + 1)); // this instance
		ctx.waitTicks(20);
		check(ctx.computeOnClient(mc -> worldNames(mc).size() == 8 && Store.config().savesDir.isEmpty()), "back to the instance's saves");
		ctx.runOnClient(mc -> {
			for (String k : Store.worlds().trash) Store.worlds().descriptions.put(k, "&6Monde &lspécial\\n&7joué {time} au total");
			Store.worlds().trashView = true;
			mc.setScreen(new SelectWorldScreen(new TitleScreen()));
		});
		ctx.waitTicks(10);
		check(ctx.computeOnClient(mc -> !Store.worlds().trashView), "v2.1: the worlds screen opens outside the trash");
		clickButton(ctx, "worldfolders.trash");
		ctx.waitTicks(2);
		cursorOnRow(ctx, 0);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.getInput().moveCursor(2, 0);
		ctx.waitTicks(2);
		ctx.takeScreenshot("08_trash_view_description");
		// v2.2 "Empty trash": the trashed worlds deleted for good after confirming, then the empty trash closes
		List<String> trashed = ctx.computeOnClient(mc -> List.copyOf(Store.worlds().trash));
		clickButton(ctx, "worldfolders.empty_trash");
		ctx.waitForScreen(ConfirmScreen.class);
		ctx.takeScreenshot("22_empty_trash_confirm");
		ctx.clickScreenButton("gui.yes");
		ctx.waitForScreen(SelectWorldScreen.class);
		ctx.waitTicks(20);
		check(!trashed.isEmpty() && ctx.computeOnClient(mc -> Store.worlds().trash.isEmpty() && !Store.worlds().trashView
				&& trashed.stream().noneMatch(k -> Files.exists(mc.getLevelSource().getBaseDir().resolve(k)))), "empty trash deletes the worlds");

		// --- multiplayer ---
		ctx.runOnClient(mc -> {
			ServerList servers = new ServerList(mc);
			servers.load();
			servers.add(new ServerData("Serveur A", "127.0.0.1:25999", ServerData.Type.OTHER), false);
			servers.add(new ServerData("Serveur B", "127.0.0.1:25998", ServerData.Type.OTHER), false);
			servers.save();
			mc.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
		});
		ctx.waitTicks(10);
		ctx.clickScreenButton("worldfolders.new_folder");
		ctx.waitForScreen(PromptScreen.class);
		ctx.getInput().typeChars("Survie");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		ctx.waitForScreen(JoinMultiplayerScreen.class);
		ctx.waitTicks(5);
		cursorOnRow(ctx, 1);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(2);
		ctx.takeScreenshot("09_server_menu");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> mc.screen instanceof JoinMultiplayerScreen), "escape closes only the menu");
		cursorOnRow(ctx, 1);
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		moveToRow(ctx, 0);
		ctx.waitTick();
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> Store.servers().items.containsValue("Survie")), "server drag and drop");
		cursorOnRow(ctx, 0);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(5);
		ctx.takeScreenshot("10_servers_expanded");
		ctx.runOnClient(mc -> Store.statsForUpdate("127.0.0.1:25999").playSeconds = 3720);
		cursorOnRow(ctx, 1);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
		ctx.takeScreenshot("10b_server_play_time");

		// --- config ---
		ctx.setScreen(() -> new ConfigScreen(new TitleScreen()));
		ctx.waitTicks(2);
		ctx.takeScreenshot("11_config");
		// --- v2.2 pause menu "Save world": saves without leaving the world ---
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.waitTicks(20); // let the world load
			ctx.setScreen(() -> new PauseScreen(true));
			ctx.waitTicks(20); // pausing saves once by itself: measure after that
			Path levelDat = ctx.computeOnClient(mc -> mc.getSingleplayerServer().getWorldPath(LevelResource.LEVEL_DATA_FILE));
			long before = Files.getLastModifiedTime(levelDat).toMillis();
			ctx.takeScreenshot("23_pause_save_button");
			clickButton(ctx, "worldfolders.save_world");
			ctx.waitFor(mc -> {
				try {
					return Files.getLastModifiedTime(levelDat).toMillis() > before;
				} catch (java.io.IOException e) {
					return false;
				}
			}, 400);
			ctx.waitTicks(5);
			ctx.takeScreenshot("24_world_saved");
			check(ctx.computeOnClient(mc -> mc.screen instanceof PauseScreen && mc.level != null), "saved without leaving the world");
		}
		// --- v2.2 title screen: screenshots button (right click = another instance) and "+" (create a world) ---
		ctx.runOnClient(mc -> {
			Path other = mc.gameDirectory.toPath().resolve("otherInstance").resolve("saves");
			Files.createDirectories(other);
			Store.config().otherSavesDirs.add(other.toString());
		});
		ctx.setScreen(TitleScreen::new);
		ctx.waitTicks(40); // the title screen fades in
		double[] shots = literalButton(ctx, "📷");
		ctx.getInput().setCursorPos(shots[0], shots[1]);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(2);
		ctx.takeScreenshot("25_title_screenshots_menu");
		int shotsIndex = ctx.computeOnClient(mc -> {
			List<Path> all = WorldMenus.instances().stream().map(i -> i.saves().toAbsolutePath().normalize()).toList();
			return all.indexOf(mc.gameDirectory.toPath().resolve("otherInstance").resolve("saves").toAbsolutePath().normalize());
		});
		clickDropdownItem(ctx, "📷", shotsIndex, ctx.computeOnClient(mc -> WorldMenus.instances().size()));
		check(ctx.computeOnClient(mc -> WorldMenus.screenshotsDir().toAbsolutePath().normalize().equals(mc.gameDirectory.toPath().resolve("otherInstance").resolve("screenshots").toAbsolutePath().normalize())), "screenshots instance chosen with a right click");
		ctx.runOnClient(mc -> Store.config().screenshotsDir = "");
		ctx.getInput().setCursorPos(shots[0], shots[1]);
		ctx.waitTicks(20);
		ctx.takeScreenshot("26_title_buttons");
		clickLiteralButton(ctx, "+");
		clickDropdownItem(ctx, "+", 0, 2); // v2.3: "Create a world" in its dropdown
		ctx.waitForScreen(CreateWorldScreen.class);
		ctx.takeScreenshot("27_create_world_from_title");
		check(ctx.computeOnClient(mc -> mc.screen instanceof CreateWorldScreen), "title \"+\" > create a world");

		// --- v2.3: an instance without a world is not offered, and one found emptySaves is left for this game's own ---
		ctx.runOnClient(mc -> {
			Path emptySaves = mc.gameDirectory.toPath().resolve("otherInstance").resolve("saves"); // listed by hand above, no world in it
			check(WorldMenus.instances().stream().anyMatch(i -> i.saves().toAbsolutePath().normalize().equals(emptySaves.toAbsolutePath().normalize())), "emptySaves instance known");
			check(WorldMenus.savesInstances().stream().noneMatch(i -> i.saves().toAbsolutePath().normalize().equals(emptySaves.toAbsolutePath().normalize())), "emptySaves instance not in the dropdown");
			Store.config().savesDir = emptySaves.toString();
		});
		ctx.setScreen(() -> new SelectWorldScreen(new TitleScreen()));
		ctx.waitTicks(20);
		ctx.takeScreenshot("28_back_from_empty_instance");
		check(ctx.computeOnClient(mc -> mc.screen instanceof SelectWorldScreen && Store.config().savesDir.isEmpty() && !Hooks.keys(((Hooks.ListHolder) mc.screen).worldfolders$list()).isEmpty()),
				"emptySaves instance left for this game's own");

		// --- v2.3: folders are dragged too: between two worlds, then into another folder ---
		ctx.runOnClient(mc -> {
			Space s = Store.worlds();
			s.folders.clear();
			s.items.clear();
			s.order.clear();
			s.expanded.clear();
			s.favorites.clear();
			s.trash.clear();
			s.sort = Space.Sort.DEFAULT;
			s.folders.add("Dossier Yak");
			s.folders.add("Dossier Zed");
			refresh(mc);
		});
		ctx.waitTicks(3);
		String firstWorld = keyAt(ctx, 2);
		cursorOnRow(ctx, 1); // "Zed"
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		moveToRow(ctx, 2);
		ctx.waitTicks(3);
		ctx.takeScreenshot("29_dragging_folder");
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
		check(rowPath(ctx, 0).equals("Dossier Yak") && keyAt(ctx, 1).equals(firstWorld) && rowPath(ctx, 2).equals("Dossier Zed"), "folder dragged under a world: " + rowPath(ctx, 0) + " " + keyAt(ctx, 1) + " " + rowPath(ctx, 2));
		cursorOnRow(ctx, 2); // "Zed" again, onto the middle of "Yak"
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		moveToRow(ctx, 0);
		ctx.waitTicks(2);
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
		check(ctx.computeOnClient(mc -> Store.worlds().folders.contains("Dossier Yak/Dossier Zed") && !Store.worlds().folders.contains("Dossier Zed")), "folder dragged into another folder");
		ctx.takeScreenshot("30_folder_in_folder");

		// --- v2.3: world presets, made and loaded from the worlds screen ---
		clickLiteralButton(ctx, "P+");
		ctx.waitForScreen(CreateWorldScreen.class);
		ctx.runOnClient(mc -> {
			((CreateWorldScreen) mc.screen).getUiState().setName("Preset A");
			((CreateWorldScreen) mc.screen).getUiState().setSeed("1234");
		});
		ctx.waitTicks(2);
		ctx.takeScreenshot("31_preset_creation");
		clickButton(ctx, "worldfolders.save_preset");
		ctx.waitForScreen(SelectWorldScreen.class);
		ctx.waitTicks(5);
		ctx.takeScreenshot("32_preset_saved_notification");
		check(ctx.computeOnClient(mc -> Store.presets().containsKey("Preset A") && Store.presets().get("Preset A").seed.equals("1234")), "preset saved, no world made");
		check(ctx.computeOnClient(mc -> !Files.exists(mc.getLevelSource().getBaseDir().resolve("Preset A"))), "saving a preset makes no world");
		clickLiteralButton(ctx, "P▼");
		ctx.waitTicks(2);
		ctx.takeScreenshot("33_preset_dropdown");
		clickDropdownItem(ctx, "P▼", 0, 2);
		ctx.waitForScreen(CreateWorldScreen.class);
		check(ctx.computeOnClient(mc -> ((CreateWorldScreen) mc.screen).getUiState().getName().equals("Preset A") && ((CreateWorldScreen) mc.screen).getUiState().getSeed().equals("1234")), "creation screen filled with the preset");

		// --- v2.3.1: the superflat customisation is part of the preset ---
		ctx.runOnClient(mc -> {
			var ui = ((CreateWorldScreen) mc.screen).getUiState();
			ui.getNormalPresetList().stream().filter(type -> type.preset().is(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT)).findFirst().ifPresent(ui::setWorldType);
			var flat = ((net.minecraft.world.level.levelgen.FlatLevelSource) ui.getSettings().selectedDimensions().overworld()).settings();
			var custom = flat.withBiomeAndLayers(List.of(new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(1, net.minecraft.world.level.block.Blocks.BEDROCK),
					new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(7, net.minecraft.world.level.block.Blocks.SANDSTONE)), flat.structureOverrides(), flat.getBiome());
			ui.updateDimensions((registries, dimensions) -> dimensions.replaceOverworldGenerator(registries, new net.minecraft.world.level.levelgen.FlatLevelSource(custom)));
			ui.setName("Preset Flat");
			check(com.imnot.worldfolders.Presets.save(null, ui), "superflat preset saved");
		});
		ctx.setScreen(TitleScreen::new);
		ctx.runOnClient(mc -> com.imnot.worldfolders.Presets.use(new TitleScreen(), "Preset Flat"));
		ctx.waitForScreen(CreateWorldScreen.class);
		check(ctx.computeOnClient(mc -> ((CreateWorldScreen) mc.screen).getUiState().getSettings().selectedDimensions().overworld() instanceof net.minecraft.world.level.levelgen.FlatLevelSource source
				&& source.settings().getLayersInfo().size() == 2 && source.settings().getLayersInfo().get(1).getBlockState().is(net.minecraft.world.level.block.Blocks.SANDSTONE)), "superflat layers come back with the preset");

		// --- v2.3.1: emptying the search brings the folders back ---
		ctx.setScreen(() -> new SelectWorldScreen(new TitleScreen()));
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> mc.screen.children().stream().filter(c -> c instanceof EditBox).forEach(c -> ((EditBox) c).setValue("new")));
		ctx.waitTicks(3);
		check(rowPath(ctx, 0).equals("-"), "a search lists the worlds flat");
		ctx.runOnClient(mc -> mc.screen.children().stream().filter(c -> c instanceof EditBox).forEach(c -> ((EditBox) c).setValue("")));
		ctx.waitTicks(3);
		check(!rowPath(ctx, 0).equals("-"), "folders are back once the search is emptied");

		// --- v2.3.1: in a wide window the instance dropdown and the saves folder button sit left of "New folder" ---
		int oldScale = ctx.computeOnClient(mc -> mc.options.guiScale().get());
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(1);
			mc.resizeGui();
		});
		ctx.waitTicks(5);
		ctx.takeScreenshot("36_wide_window_layout");
		double[] instance = literalButton(ctx, "▼"), folder = literalButton(ctx, "📂");
		double[] newFolderButton = buttonPos(ctx, "worldfolders.new_folder");
		check(folder[0] < instance[0] && instance[0] < newFolderButton[0] && instance[1] == newFolderButton[1] && folder[1] == newFolderButton[1], "saves folder button, then instance dropdown, left of \"New folder\"");
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(oldScale);
			mc.resizeGui();
		});
		ctx.waitTicks(5);

		// --- v2.3: the title "+" offers the presets in a submenu ---
		ctx.setScreen(TitleScreen::new);
		ctx.waitTicks(40);
		clickLiteralButton(ctx, "+");
		double[] plusButton = literalButton(ctx, "+");
		double guiScale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos((plusButton[2] + 10) * guiScale, (plusButton[3] + 3 + 14 + 7) * guiScale); // "Load a preset"
		ctx.waitTicks(3);
		ctx.takeScreenshot("34_title_preset_submenu");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

		// --- v2.3: Tab walks the item suggestions, the list scrolls ---
		ctx.setScreen(() -> new PromptScreen(net.minecraft.network.chat.Component.literal("Tab"), "minecraft:diamond_", text -> {
		}, new TitleScreen()).suggestions(typed -> Space.suggest(com.imnot.worldfolders.Ui.itemIds(), typed, Integer.MAX_VALUE)).preview(com.imnot.worldfolders.Ui::item));
		ctx.waitTicks(2);
		ctx.getInput().pressKey(GLFW.GLFW_KEY_TAB);
		ctx.getInput().pressKey(GLFW.GLFW_KEY_TAB);
		ctx.waitTicks(2);
		ctx.takeScreenshot("35_tab_through_suggestions");
		check(ctx.computeOnClient(mc -> promptText(mc).equals("minecraft:diamond_block")), "second Tab takes the second suggestion: " + ctx.computeOnClient(mc -> promptText(mc)));
		v24(ctx);
		v25(ctx);
		v26(ctx);
		ctx.setScreen(TitleScreen::new);
	}

	/** v2.5: folders shared between instances, a pack of another instance stays selected, the packs are organised like the worlds. */
	private static void v25(ClientGameTestContext ctx) throws Exception {
		// --- one organisation per saves folder, whatever the config calls it, kept in the shared file ---
		check(ctx.computeOnClient(mc -> {
			Space own = Store.worlds();
			Store.config().savesDir = mc.gameDirectory.toPath().resolve("saves").toString(); // this instance, as another one would name it
			boolean same = Store.worlds() == own;
			Store.config().savesDir = "";
			Store.save();
			return same && Files.readString(mc.gameDirectory.toPath().resolve("config/worldfolders-shared.json")).contains("\"packs\"");
		}), "the folders of a saves folder don't depend on the instance that shows it");

		// --- a pack of another instance, selected, is still selected when the pack screen opens again ---
		List<String> selectedBefore = ctx.computeOnClient(mc -> {
			Path game = mc.gameDirectory.toPath();
			for (Path pack : List.of(game.resolve("packInstance/resourcepacks/OtherPack"), game.resolve("resourcepacks/OwnPack"), game.resolve("resourcepacks/PackB"))) {
				Files.createDirectories(pack);
				Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"test\"}}");
			}
			Files.createDirectories(game.resolve("packInstance/saves"));
			Store.config().otherSavesDirs.add(game.resolve("packInstance/saves").toString());
			Space s = Store.packs();
			s.folders.clear();
			s.items.clear();
			s.favorites.clear();
			s.trash.clear();
			var repository = mc.getResourcePackRepository();
			List<String> before = List.copyOf(repository.getSelectedIds());
			repository.reload();
			List<String> now = new java.util.ArrayList<>(before);
			now.add("file/OtherPack");
			repository.setSelected(now);
			return before;
		});
		openPacks(ctx);
		ctx.takeScreenshot("45_packs");
		check(ctx.computeOnClient(mc -> mc.screen.children().stream().anyMatch(c -> c instanceof TransferableSelectionList l && l != ((Hooks.ListHolder) mc.screen).worldfolders$list()
				&& l.children().stream().anyMatch(e -> e.getPackId().equals("file/OtherPack")))), "the pack of another instance is still selected");
		check(packRow(ctx, "file/OwnPack") >= 0 && packRow(ctx, "file/OtherPack") < 0, "available packs listed");

		// --- new folder, favorite and trash from the menu, restore, drag into the folder ---
		clickButton(ctx, "worldfolders.new_folder");
		ctx.waitForScreen(PromptScreen.class);
		ctx.getInput().typeChars("Mes packs longs");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		ctx.waitForScreen(PackSelectionScreen.class);
		ctx.waitTicks(5);
		check(rowPath(ctx, PACK_TOP).equals("Mes packs longs"), "pack folder shown first");

		cursorOnPack(ctx, "file/OwnPack");
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(2);
		ctx.takeScreenshot("46_pack_menu");
		clickMenuItem(ctx, 2, 7); // select, description, favorite, new folder, move, open folder, trash
		check(ctx.computeOnClient(mc -> Store.packs().favorites.contains("file/OwnPack")) && packRow(ctx, "file/OwnPack") == PACK_TOP + 1, "favorite pack comes first");

		cursorOnPack(ctx, "file/PackB");
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		clickMenuItem(ctx, 6, 7);
		check(ctx.computeOnClient(mc -> Store.packs().trash.contains("file/PackB")) && packRow(ctx, "file/PackB") < 0, "pack sent to the trash");
		clickButton(ctx, "worldfolders.trash");
		ctx.waitTicks(2);
		ctx.takeScreenshot("47_pack_trash");
		check(ctx.computeOnClient(mc -> Store.packs().trashView) && packRow(ctx, "file/PackB") == PACK_TOP, "trash view of the packs");
		cursorOnRow(ctx, PACK_TOP);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		clickMenuItem(ctx, 0, 2); // restore, delete for good
		check(ctx.computeOnClient(mc -> !Store.packs().trashView && Store.packs().trash.isEmpty()) && packRow(ctx, "file/PackB") > 0, "pack restored, trash left");
		cursorOnPack(ctx, "file/PackB");
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		clickMenuItem(ctx, 2, 7); // a favorite too: now right under the folder and the other favorite
		check(packRow(ctx, "file/PackB") == PACK_TOP + 2, "second favorite pack");

		cursorOnPack(ctx, "file/PackB");
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		moveToRow(ctx, PACK_TOP);
		ctx.waitTicks(2);
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> "Mes packs longs".equals(Store.packs().items.get("file/PackB"))) && packRow(ctx, "file/PackB") < 0, "pack dragged into the folder");
		cursorOnRow(ctx, PACK_TOP);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); // expand
		ctx.waitTicks(10);
		ctx.takeScreenshot("48_pack_folder_open");
		check(packRow(ctx, "file/PackB") == PACK_TOP + 1, "open folder shows its pack");

		ctx.runOnClient(mc -> {
			mc.getResourcePackRepository().setSelected(selectedBefore);
			Store.config().otherSavesDirs.removeIf(dir -> dir.contains("packInstance"));
			Store.save();
		});
	}

	/** v2.6: the options of another instance, picked in the options screen; the shader packs of every instance (with Iris). */
	private static void v26(ClientGameTestContext ctx) throws Exception {
		Path other = ctx.computeOnClient(mc -> {
			Path game = mc.gameDirectory.toPath().resolve("optInstance");
			Files.createDirectories(game.resolve("saves"));
			Files.writeString(game.resolve("options.txt"), "renderDistance:7\n");
			Files.createDirectories(game.resolve("shaderpacks/OtherShader/shaders"));
			Store.config().otherSavesDirs.add(game.resolve("saves").toString());
			return game;
		});
		int ownDistance = ctx.computeOnClient(mc -> mc.options.renderDistance().get());
		// The dropdown: this instance, then every instance with an options.txt (the real ones of this computer too).
		List<Path> choices = ctx.computeOnClient(mc -> {
			List<Path> out = new java.util.ArrayList<>();
			var all = WorldMenus.instances();
			for (int i = 0; i < all.size(); i++) {
				Path f = all.get(i).saves().toAbsolutePath().normalize().resolveSibling("options.txt");
				if (i == 0 || Files.isRegularFile(f)) out.add(f);
			}
			return out;
		});
		int index = choices.indexOf(other.resolve("options.txt").toAbsolutePath().normalize());
		check(index > 0, "an instance with options is offered");
		ctx.setScreen(() -> new OptionsScreen(new TitleScreen(), Minecraft.getInstance().options, false));
		ctx.waitTicks(5);
		ctx.takeScreenshot("50_options_instance_button");
		clickLiteralButton(ctx, "▼");
		ctx.waitTicks(2);
		ctx.takeScreenshot("51_options_instance_menu");
		clickDropdownItem(ctx, "▼", index, choices.size());
		ctx.waitFor(mc -> mc.getOverlay() == null && mc.screen instanceof OptionsScreen); // the resources reload
		ctx.takeScreenshot("52_options_of_other_instance");
		check(ctx.computeOnClient(mc -> mc.options.renderDistance().get() == 7 && !Store.config().optionsFile.isEmpty()
				&& Files.exists(other.resolve("options.txt.nowf-backup"))), "the options of the other instance are in use");
		clickLiteralButton(ctx, "▼");
		clickDropdownItem(ctx, "▼", 0, choices.size()); // this instance
		ctx.waitFor(mc -> mc.getOverlay() == null && mc.screen instanceof OptionsScreen); // the resources reload
		check(ctx.computeOnClient(mc -> mc.options.renderDistance().get() == ownDistance && Store.config().optionsFile.isEmpty()), "back to the options of this instance");

		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) IrisCheck.run(ctx, other);
		ctx.runOnClient(mc -> {
			Store.config().otherSavesDirs.removeIf(dir -> dir.contains("optInstance"));
			Store.save();
		});
	}

	/** The shaders screen of Iris: its own class, loaded only when Iris is there. */
	private static final class IrisCheck {
		static void run(ClientGameTestContext ctx, Path other) {
			ctx.setScreen(() -> new net.irisshaders.iris.gui.screen.ShaderPackScreen(new TitleScreen()));
			ctx.waitTicks(10);
			String path = other.resolve("shaderpacks/OtherShader").toAbsolutePath().normalize().toString();
			ctx.takeScreenshot("53_shaders_all_instances");
			int row = shaderRow(ctx, path);
			check(row >= 0, "the shader pack of another instance is listed");
			cursorOnShader(ctx, row);
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.takeScreenshot("54_shader_menu");
			clickMenuItem(ctx, 1, 2); // open folder, trash
			check(shaderRow(ctx, path) < 0 && ctx.computeOnClient(mc -> Store.shaderTrash().contains(path)), "shader pack sent to the trash");
			clickButton(ctx, "worldfolders.trash");
			ctx.waitTicks(5);
			ctx.takeScreenshot("55_shader_trash");
			row = shaderRow(ctx, path);
			check(row >= 0, "the trash view shows it");
			cursorOnShader(ctx, row);
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			clickMenuItem(ctx, 0, 2); // restore, delete for good
			check(shaderRow(ctx, path) < 0 && ctx.computeOnClient(mc -> Store.shaderTrash().isEmpty()), "shader pack restored");
		}

		private static int shaderRow(ClientGameTestContext ctx, String name) {
			ctx.waitTicks(2);
			return ctx.computeOnClient(mc -> {
				for (var child : mc.screen.children()) {
					if (child instanceof net.irisshaders.iris.gui.element.ShaderPackSelectionList list) {
						for (int i = 0; i < list.children().size(); i++) {
							if (list.children().get(i) instanceof net.irisshaders.iris.gui.element.ShaderPackSelectionList.ShaderPackEntry e && e.getPackName().equals(name)) return i;
						}
					}
				}
				return -1;
			});
		}

		private static void cursorOnShader(ClientGameTestContext ctx, int index) {
			double[] p = ctx.computeOnClient(mc -> {
				for (var child : mc.screen.children()) {
					if (child instanceof net.irisshaders.iris.gui.element.ShaderPackSelectionList list) {
						list.setScrollAmount(Math.max(0, list.scrollAmount() + list.getRowTop(index) - list.getY() - 40));
						double scale = mc.getWindow().getGuiScale();
						return new double[]{(list.getRowLeft() + 40) * scale, (list.getRowTop(index) + 8) * scale};
					}
				}
				throw new AssertionError("no shader list");
			});
			ctx.getInput().setCursorPos(p[0], p[1]);
		}
	}

	/** Rows above the first pack or folder of the "Available" list: its title is a row since 1.21.9. */
	private static final int PACK_TOP = 1;

	private static void openPacks(ClientGameTestContext ctx) {
		ctx.setScreen(() -> new PackSelectionScreen(Minecraft.getInstance().getResourcePackRepository(), repository -> {
		}, Minecraft.getInstance().getResourcePackDirectory(), net.minecraft.network.chat.Component.literal("Packs")));
		ctx.waitTicks(10);
	}

	/** Scrolls the "Available" list to the pack {@code id} (the packs of the real instances of this computer are listed too) and puts the cursor on it. */
	private static void cursorOnPack(ClientGameTestContext ctx, String id) {
		int row = packRow(ctx, id);
		check(row >= 0, "pack listed: " + id);
		ctx.runOnClient(mc -> ((Hooks.ListHolder) mc.screen).worldfolders$list().setScrollAmount(Math.max(0, (row - PACK_TOP) * 36 - 72)));
		ctx.waitTick();
		cursorOnRow(ctx, row);
	}

	/** Row of the pack {@code id} in the "Available" list, -1 if it isn't shown. */
	private static int packRow(ClientGameTestContext ctx, String id) {
		return ctx.computeOnClient(mc -> {
			var rows = ((Hooks.ListHolder) mc.screen).worldfolders$list().children();
			for (int i = 0; i < rows.size(); i++) if (id.equals(Hooks.keyOf(rows.get(i)))) return i;
			return -1;
		});
	}


	/** v2.4: statistics of a world, instance picked from the title screen, exact FOV. */
	private static void v24(ClientGameTestContext ctx) {
		// --- statistics of a world, read from its folder ---
		ctx.runOnClient(mc -> {
			Store.worlds().trashView = false;
			Store.worlds().current = "";
			Store.worlds().selection.clear();
		});
		ctx.setScreen(() -> new SelectWorldScreen(new TitleScreen()));
		ctx.waitTicks(20);
		int[] row = ctx.computeOnClient(mc -> {
			var rows = ((Hooks.ListHolder) mc.screen).worldfolders$list().children();
			for (int i = 0; i < rows.size(); i++) {
				String key = Hooks.keyOf(rows.get(i));
				if (key != null) return new int[]{i, Store.worlds().favorites.contains(key) ? 1 : 0};
			}
			throw new AssertionError("no world row");
		});
		cursorOnRow(ctx, row[0]);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(2);
		ctx.takeScreenshot("40_world_menu_stats");
		clickMenuItem(ctx, 10, 12 - row[1]); // "Statistics": after "paste" (a world was copied above) and "game rules"
		ctx.waitForScreen(StatsScreen.class);
		ctx.waitTicks(5);
		ctx.takeScreenshot("41_world_stats");
		check(ctx.computeOnClient(mc -> mc.screen.children().stream().anyMatch(c -> c instanceof AbstractSelectionList<?> l && !l.children().isEmpty())), "statistics of the world listed");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitForScreen(SelectWorldScreen.class);

		// --- right click on Singleplayer: the instance dropdown ---
		ctx.setScreen(TitleScreen::new);
		ctx.waitTicks(40);
		double[] solo = buttonPos(ctx, "menu.singleplayer");
		ctx.getInput().setCursorPos(solo[0], solo[1]);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(3);
		ctx.takeScreenshot("42_solo_instance_menu");
		check(ctx.computeOnClient(mc -> ((Hooks.Overlay) mc.screen).worldfolders$menu() != null), "right click on Singleplayer opens the instance dropdown");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> mc.screen instanceof TitleScreen && ((Hooks.Overlay) mc.screen).worldfolders$menu() == null), "Escape closes the dropdown, not the title screen");

		// --- Ctrl + click on the FOV slider: the exact value is typed ---
		ctx.setScreen(() -> new OptionsScreen(new TitleScreen(), Minecraft.getInstance().options, false));
		ctx.waitTicks(5);
		double[] fov = fovSlider(ctx, "");
		ctx.getInput().setCursorPos(fov[0], fov[1]);
		ctx.getInput().holdKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		ctx.waitForScreen(PromptScreen.class);
		ctx.runOnClient(mc -> mc.screen.children().stream().filter(c -> c instanceof EditBox).forEach(c -> ((EditBox) c).setValue("93")));
		ctx.waitTicks(2);
		ctx.takeScreenshot("43_exact_fov_prompt");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		ctx.waitForScreen(OptionsScreen.class);
		ctx.waitTicks(2);
		ctx.takeScreenshot("44_fov_93");
		check(ctx.computeOnClient(mc -> mc.options.fov().get() == 93), "FOV typed is the FOV set");
		fovSlider(ctx, "93"); // throws if the slider doesn't show it
		ctx.runOnClient(mc -> {
			mc.options.fov().set(70);
			mc.options.save();
		});

	}

	/** Center, in window pixels, of the FOV slider whose label contains {@code text}. */
	private static double[] fovSlider(ClientGameTestContext ctx, String text) {
		return ctx.computeOnClient(mc -> {
			for (var child : mc.screen.children()) {
				if (child instanceof AbstractWidget w && Ui.isFov(w.getMessage()) && w.getMessage().getString().contains(text)) {
					double scale = mc.getWindow().getGuiScale();
					return new double[]{(w.getX() + w.getWidth() / 2.0) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
				}
			}
			throw new AssertionError("no FOV slider showing \"" + text + "\"");
		});
	}

	/** Clicks, with the mouse, the button whose label is translation {@code key} (whatever its arguments). */
	private static void clickButton(ClientGameTestContext ctx, String key) {
		ctx.waitTicks(6);
		double[] p = buttonPos(ctx, key);
		ctx.getInput().setCursorPos(p[0], p[1]);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	/** Center, in window pixels, of the visible button whose label is translation {@code key}. */
	private static double[] buttonPos(ClientGameTestContext ctx, String key) {
		return ctx.computeOnClient(mc -> {
			for (var child : mc.screen.children()) {
				if (child instanceof AbstractWidget w && w.visible && w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
					double scale = mc.getWindow().getGuiScale();
					return new double[]{(w.getX() + w.getWidth() / 2.0) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
				}
			}
			throw new AssertionError("no button " + key);
		});
	}

	private static void refresh(Minecraft mc) {
		((Hooks.OrganizedList) ((Hooks.ListHolder) mc.screen).worldfolders$list()).worldfolders$refresh();
	}

	/** Key of the world/server on row {@code index} ("" for a folder row). */
	private static String keyAt(ClientGameTestContext ctx, int index) {
		return ctx.computeOnClient(mc -> keyAt(mc, index));
	}

	private static String keyAt(Minecraft mc, int index) {
		return String.valueOf(Hooks.keyOf(((Hooks.ListHolder) mc.screen).worldfolders$list().children().get(index)));
	}

	/** Path of the folder row {@code index} as displayed ("-" if it isn't a folder row). */
	private static String rowPath(ClientGameTestContext ctx, int index) {
		return ctx.computeOnClient(mc -> ((Hooks.ListHolder) mc.screen).worldfolders$list().children().get(index) instanceof FolderRow.Holder h ? String.valueOf(h.row().path) : "-");
	}

	private static String promptText(Minecraft mc) {
		return mc.screen.children().stream().filter(c -> c instanceof EditBox).map(c -> ((EditBox) c).getValue()).findFirst().orElse("");
	}

	/** Center of the visible button labelled {@code text}, in window pixels (x, y), then its bottom-left corner in GUI units. */
	private static double[] literalButton(ClientGameTestContext ctx, String text) {
		ctx.waitTicks(2);
		return ctx.computeOnClient(mc -> {
			for (var child : mc.screen.children()) {
				// "" = the sort button, the only one without a label (its arrows are drawn over it)
				if (child instanceof AbstractWidget w && w.visible && (text.isEmpty() ? w instanceof net.minecraft.client.gui.components.Button && w.getMessage().getString().isEmpty() : w.getMessage().getString().startsWith(text))) {
					double scale = mc.getWindow().getGuiScale();
					return new double[]{(w.getX() + w.getWidth() / 2.0) * scale, (w.getY() + w.getHeight() / 2.0) * scale, w.getX(), w.getY() + w.getHeight()};
				}
			}
			throw new AssertionError("no button " + text);
		});
	}

	private static void clickLiteralButton(ClientGameTestContext ctx, String text) {
		double[] p = literalButton(ctx, text);
		ctx.getInput().setCursorPos(p[0], p[1]);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	/** Clicks item {@code i} of the dropdown opened under the button labelled {@code text}. */
	/**
	 * Clicks item {@code i} of the {@code count}-item dropdown opened under the button labelled {@code text},
	 * placed and scrolled like ContextMenu does (kept on screen, wheel when taller than it).
	 */
	private static void clickDropdownItem(ClientGameTestContext ctx, String text, int i, int count) {
		double[] p = literalButton(ctx, text);
		double[] geo = ctx.computeOnClient(mc -> {
			int rows = Math.max(1, Math.min(count, (mc.screen.height - 6) / 14));
			return new double[]{mc.getWindow().getGuiScale(), rows, Math.max(0, Math.min(p[3], mc.screen.height - (rows * 14 + 6)))};
		});
		int scroll = Math.max(0, i - (int) geo[1] + 1);
		ctx.getInput().setCursorPos((p[2] + 10) * geo[0], (geo[2] + 10) * geo[0]);
		for (int s = 0; s < scroll; s++) ctx.getInput().scroll(-1);
		ctx.getInput().setCursorPos((p[2] + 10) * geo[0], (geo[2] + 3 + (i - scroll) * 14 + 7) * geo[0]);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("World Folders UI test failed: " + what);
	}

	/** Puts the cursor on row {@code index} of the current screen's world/server list. */
	private static void cursorOnRow(ClientGameTestContext ctx, int index) {
		ctx.waitTicks(6); // keep separate clicks from counting as a double-click
		double[] p = rowPos(ctx, index);
		ctx.getInput().setCursorPos(p[0], p[1]);
	}

	private static void moveToRow(ClientGameTestContext ctx, int index) {
		double[] p = rowPos(ctx, index);
		ctx.getInput().setCursorPos(p[0], p[1]);
	}

	private static double[] rowPos(ClientGameTestContext ctx, int index) {
		return ctx.computeOnClient(mc -> {
			AbstractSelectionList<?> list = ((Hooks.ListHolder) mc.screen).worldfolders$list();
			double scale = mc.getWindow().getGuiScale();
			return new double[]{(list.getRowLeft() + 80) * scale, (list.getRowTop(index) + 12) * scale};
		});
	}

	/** Clicks item {@code i} of the {@code count}-item context menu opened at the cursor (kept on screen like ContextMenu does). */
	private static void clickMenuItem(ClientGameTestContext ctx, int i, int count) {
		ctx.waitTicks(2);
		double[] p = ctx.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			double top = Math.min(mc.mouseHandler.ypos() / scale, mc.screen.height - (count * 14 + 6));
			return new double[]{mc.mouseHandler.xpos() + 10 * scale, (top + 3 + i * 14 + 7) * scale};
		});
		ctx.getInput().setCursorPos(p[0], p[1]);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
	}

	private static List<String> worldNames(Minecraft mc) throws Exception {
		var source = mc.getLevelSource();
		return source.loadLevelSummaries(source.findLevelCandidates()).join().stream().map(LevelSummary::getLevelName).toList();
	}
}
