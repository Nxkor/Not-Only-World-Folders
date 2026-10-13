package com.imnot.worldfolders;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.imnot.worldfolders.Ui.tr;

/**
 * Drag a world/server row (or the whole selection, when pressed on a selected row) or a folder row: between other rows
 * (placed live), onto a folder row or ".." (moved into it), onto "New folder" (asks the new folder's name)
 * or onto the trash. A folder hovered 0.5 s opens; near the top or bottom edge the list scrolls.
 * A right click during the drag puts the rows back.
 * Flow: press on a row, then a per-frame update from the screen's render while the button is held,
 * then the release, straight from the mouse handler (MouseHandlerMixin).
 */
public final class Drag {
	private static final long OPEN_DELAY = 500;
	private static final int EDGE = 20;
	private static @Nullable Hooks.OrganizedList list;
	/** The row pressed, and every row dragged (it, or the selection it belongs to). */
	private static String key = "", label = "", fromLevel = "";
	private static List<String> keys = List.of();
	/** Pressed on a selected row: a click without a drag then selects that row alone. */
	private static boolean pressedInSelection;
	private static double startX, startY;
	private static boolean moving, enabled, folderWasOpen;
	/** Where the rows were before the drag (a folder: its path), to undo the live placement. */
	private static Map<String, String> oldFolders = Map.of();
	private static List<String> oldOrder = List.of();
	private static @Nullable String hoveredFolder, lastAnchor;
	private static long hoveredSince, lastFrame;
	private static boolean lastAfter;

	/** A world/server row was pressed ({@code itemKey}), or a folder row (its {@link Space#folderKey}, after the click toggled it). */
	public static void press(Hooks.OrganizedList owner, String itemKey, String itemLabel) {
		Space s = owner.worldfolders$space();
		list = owner;
		enabled = Store.config().dragAndDrop && !s.trashView;
		key = itemKey;
		pressedInSelection = s.selection.size() > 1 && s.selection.contains(itemKey);
		keys = pressedInSelection ? s.selected(Hooks.keys(owner)) : List.of(itemKey);
		if (!pressedInSelection) s.selection.clear();
		label = keys.size() > 1 ? tr("n_selected", keys.size()).getString() : itemLabel;
		startX = Ui.mouseX();
		startY = Ui.mouseY();
		moving = false;
		oldFolders = new HashMap<>();
		for (String k : keys) oldFolders.put(k, Space.isFolderKey(k) ? k.substring(1) : s.folderOf(k));
		oldOrder = new ArrayList<>(s.order);
		fromLevel = Space.isFolderKey(itemKey) ? Space.parent(itemKey.substring(1)) : s.folderOf(itemKey);
		folderWasOpen = Space.isFolderKey(itemKey) && !s.expanded.contains(itemKey.substring(1)); // the press just toggled it
		hoveredFolder = lastAnchor = null;
		lastFrame = Util.getMillis();
	}

	public static boolean active() {
		return list != null && moving;
	}

	public static void cancel() {
		list = null;
		moving = false;
	}

	/** The dragged folder was shown closed while it moved: open again if it was before the press. */
	private static void reopenFolder(Space s) {
		if (Space.isFolderKey(key) && folderWasOpen && s.folders.contains(key.substring(1))) s.expanded.add(key.substring(1));
	}

	/** Right click while dragging: the rows go back where they were. Returns true if a drag was undone. */
	public static boolean undo() {
		if (!active()) return false;
		Space s = list.worldfolders$space();
		if (Space.isFolderKey(key)) {
			String was = oldFolders.get(keys.get(0));
			if (!key.substring(1).equals(was)) s.movePrefix(key.substring(1), was);
			key = Space.folderKey(was);
			reopenFolder(s);
		} else {
			oldFolders.forEach(s::move);
		}
		s.order = new ArrayList<>(oldOrder);
		list.worldfolders$refresh();
		cancel();
		return true;
	}

	/** Each frame, at the end of the screen's render: live placement, then the dragged name and a red trash under the cursor. */
	public static void render(GuiGraphics g, int mouseX, int mouseY, Hooks.ListHolder screen) {
		if (list == null) return;
		if (enabled && !moving && Math.abs(mouseX - startX) + Math.abs(mouseY - startY) > 6) {
			moving = true;
			if (Space.isFolderKey(key) && list.worldfolders$space().expanded.remove(key.substring(1))) list.worldfolders$refresh(); // a folder travels closed
		}
		if (!moving) return;
		update(mouseX, mouseY);
		if (list == null) return; // the update may have left the screen
		Button trash = screen.worldfolders$trashButton();
		int w = Minecraft.getInstance().font.width(label) + 8;
		g.nextStratum(); // above the list and its item icons
		if (trash != null && trash.isMouseOver(mouseX, mouseY) && !Space.isFolderKey(key)) { // folders don't go to the trash
			g.fill(trash.getX(), trash.getY(), trash.getX() + trash.getWidth(), trash.getY() + trash.getHeight(), 0xA0E02020);
		}
		int x = Math.min(mouseX + 6, g.guiWidth() - w); // stays readable at the right edge, over the trash
		g.fill(x, mouseY + 6, x + w, mouseY + 20, 0xC0202040);
		g.drawString(Minecraft.getInstance().font, label, x + 4, mouseY + 9, 0xFFFFFFFF);
	}

	private static void update(double mouseX, double mouseY) {
		autoScroll(mouseY);
		Space s = list.worldfolders$space();
		Object row = list.worldfolders$rowAt(mouseX, mouseY);
		boolean ordered = s.sort.manual() && !list.worldfolders$searching();
		String anchor;
		boolean after;
		List<String> shown = Hooks.slots(list);
		if (row instanceof FolderRow.Holder h) {
			String path = h.row().path; // null = the ".." row
			if (path != null && keys.contains(Space.folderKey(path))) return; // the dragged folder itself
			if (into(list, h, mouseY)) {
				String id = String.valueOf(path);
				if (!id.equals(hoveredFolder)) {
					hoveredFolder = id;
					hoveredSince = Util.getMillis();
				} else if (Util.getMillis() - hoveredSince > OPEN_DELAY) {
					hoveredSince = Util.getMillis(); // ".." keeps going up while hovered
					h.row().open();
				}
				return;
			}
			anchor = Space.folderKey(path);
			after = list.worldfolders$rowFraction(row, mouseY) > 0.5;
		} else {
			anchor = Hooks.keyOf(row);
			if (anchor == null || keys.contains(anchor) || !ordered) {
				hoveredFolder = null;
				return;
			}
			// Passing a row from above puts the dragged ones under it, from below above it: they stay under the cursor.
			after = shown.indexOf(anchor) > shown.indexOf(key);
		}
		hoveredFolder = null;
		if (anchor.equals(lastAnchor) && after == lastAfter) return; // e.g. above a favorite, where it can't go
		lastAnchor = anchor;
		lastAfter = after;
		keys = s.placeNear(keys, anchor, after, shown);
		if (Space.isFolderKey(key)) key = keys.get(0); // moved to another level: a new path
		list.worldfolders$refresh();
	}

	/** Near the list's top or bottom edge, scroll: the closer, the faster (up to 600 px/s). */
	private static void autoScroll(double mouseY) {
		AbstractSelectionList<?> l = (AbstractSelectionList<?>) list;
		long now = Util.getMillis();
		double seconds = Math.min(now - lastFrame, 100) / 1000.0;
		lastFrame = now;
		double depth = mouseY < l.getY() + EDGE ? mouseY - l.getY() - EDGE : mouseY > l.getBottom() - EDGE ? mouseY - l.getBottom() + EDGE : 0;
		if (depth != 0) l.setScrollAmount(l.scrollAmount() + Math.max(-1, Math.min(1, depth / EDGE)) * 600 * seconds);
	}

	/** Left button released, wherever the mouse is. Between rows the rows are already placed. */
	public static void release() {
		Hooks.OrganizedList owner = list;
		boolean drop = active();
		cancel();
		if (owner == null) return;
		Space s = owner.worldfolders$space();
		if (!drop) {
			if (pressedInSelection) s.selection.clear(); // a plain click on a selected row: that row alone
			return;
		}
		if (!(Minecraft.getInstance().screen instanceof Hooks.ListHolder screen)) return;
		double mouseX = Ui.mouseX(), mouseY = Ui.mouseY();
		List<String> ks = keys;
		boolean folder = Space.isFolderKey(key), wasOpen = folderWasOpen;
		String from = fromLevel; // dragged out of a folder: "New folder" makes a subfolder of it
		Button trash = screen.worldfolders$trashButton(), newFolder = screen.worldfolders$newFolderButton();
		if (trash != null && trash.isMouseOver(mouseX, mouseY)) {
			if (!folder) { // the trash holds worlds and servers, never folders
				long refused = ks.stream().filter(k -> !s.toTrash(k)).count();
				if (refused > 0) Ui.toast(tr("favorite_protected"), Component.literal(ks.size() == 1 ? label : tr("n_selected", refused).getString()));
				s.selection.clear();
			}
		} else if (newFolder != null && newFolder.isMouseOver(mouseX, mouseY)) {
			Store.save(); // keeps the live placement; the screen refreshes when shown again
			owner.worldfolders$dropped();
			Minecraft.getInstance().setScreen(WorldMenus.namePrompt(tr("new_folder"), "", name -> {
				String path = s.createFolder(from, name);
				for (String k : ks) {
					String now = path == null ? k : s.moveInto(k, path);
					if (folder && wasOpen) s.expanded.add(now.substring(1));
				}
				Store.save();
			}, (Screen) screen));
			return;
		} else if (owner.worldfolders$rowAt(mouseX, mouseY) instanceof FolderRow.Holder h && into(owner, h, mouseY)) {
			for (String k : ks) {
				String now = s.moveInto(k, h.row().target());
				if (folder) key = now;
			}
		}
		reopenFolder(s);
		Store.save();
		owner.worldfolders$dropped();
		owner.worldfolders$refresh();
	}

	/** A folder row takes what is dropped on its middle; its top and bottom quarters place before / after it (manual order only). */
	private static boolean into(Hooks.OrganizedList l, FolderRow.Holder h, double mouseY) {
		if (h.row().path == null || !l.worldfolders$space().sort.manual() || l.worldfolders$searching()) return true;
		double at = l.worldfolders$rowFraction(h, mouseY);
		return at >= 0.25 && at <= 0.75;
	}
}
