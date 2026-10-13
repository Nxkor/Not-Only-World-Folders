package com.imnot.worldfolders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A folder row (or the ".." row when {@code path} is null). Click: expand/collapse.
 * Double-click: browse into it. Right-click: folder menu. Also a drop target for drag and drop.
 */
public final class FolderRow {
	private static final ItemStack FOLDER = new ItemStack(Items.CHEST);
	private static final ItemStack UP = new ItemStack(Items.ARROW);

	public final Hooks.OrganizedList list;
	public final @Nullable String path;
	private final int depth;
	/** Builds the folder's right-click menu (world or server flavour). */
	private final Consumer<FolderRow> menu;
	/** Built once: a change of the folders makes new rows. */
	private @Nullable Component label;
	/** World folders: makes a world straight into the folder given (the row's "+"). Null: no "+". */
	private @Nullable Consumer<String> create;
	/** Left edge of the "+" as last drawn. */
	private int plusX;

	FolderRow(Hooks.OrganizedList list, @Nullable String path, int depth, Consumer<FolderRow> menu) {
		this.list = list;
		this.path = path;
		this.depth = depth;
		this.menu = menu;
	}

	/** Where something dropped on this row goes. */
	public String target() {
		Space s = list.worldfolders$space();
		return path == null ? Space.parent(s.current) : path;
	}

	/** Folder names keep their "&" colour codes as typed; they are applied only here. */
	public Component label() {
		if (label != null) return label;
		Space s = list.worldfolders$space();
		if (path == null) return label = Ui.format(".. (/" + s.current + ")");
		long[] n = s.count(path); // "(2 · 7)": 2 right here, 7 with the subfolders
		return label = Ui.format(Space.name(path)).append("  §7(" + n[0] + (n[1] != n[0] ? " · " + n[1] : "") + ")");
	}

	/** The folder's own icon, else the configured default, else a chest (unknown ids: a mod was removed). */
	private ItemStack icon() {
		if (path == null) return UP;
		ItemStack own = Ui.item(list.worldfolders$space().folderIcons.getOrDefault(path, ""));
		if (!own.isEmpty()) return own;
		ItemStack fallback = Ui.item(Store.config().folderIcon);
		return fallback.isEmpty() ? FOLDER : fallback;
	}

	void render(GuiGraphicsExtractor g, int left, int top, int height, int mouseX, int mouseY, boolean hovering) {
		Font font = Minecraft.getInstance().font;
		int x = left + depth * Ui.INDENT;
		int mid = top + (height - 8) / 2;
		Space s = list.worldfolders$space();
		if (path != null) g.text(font, s.expanded.contains(path) ? "▼" : "▶", x, mid, 0xFFAAAAAA);
		g.item(icon(), x + 10, top + (height - 16) / 2);
		g.text(font, label(), x + 32, mid, hovering ? 0xFFFFFF55 : 0xFFFFFFFF);
		if (create != null) { // a small "+" after the name: a new world, straight into this folder
			plusX = x + 32 + font.width(label()) + 6;
			g.fill(plusX, mid - 1, plusX + 9, mid + 8, hovering && overPlus() ? 0xC040A040 : 0x50FFFFFF);
			g.text(font, "+", plusX + 2, mid, 0xFFFFFFFF);
		}
		String description = create != null && hovering && overPlus() ? Ui.tr("new_world_here").getString()
				: path == null ? null : s.folderDescriptions.get(path);
		if (description != null && hovering) Ui.showDescription(g, description, mouseX, mouseY);
	}

	private boolean overPlus() {
		double x = Ui.mouseX();
		return x >= plusX && x < plusX + 9;
	}

	/** Left edge of the "+", for the UI tests. */
	public int plusX() {
		return plusX;
	}

	/** Hovered for a while during a drag: opens like a click, but never closes. ".." goes up. */
	void open() {
		Space s = list.worldfolders$space();
		if (path == null) s.current = Space.parent(s.current);
		else if (!s.expanded.add(path)) return;
		Store.save();
		list.worldfolders$refresh();
	}

	/** Returns false so the list, which may just have been rebuilt, doesn't select this row. */
	boolean click(MouseButtonEvent event, boolean doubleClick) {
		Space s = list.worldfolders$space();
		if (event.button() == 1) {
			menu.accept(this);
			return false;
		}
		if (event.button() != 0) return false;
		if (create != null && overPlus()) {
			create.accept(path == null ? s.current : path);
			return true;
		}
		if (path == null) {
			s.current = Space.parent(s.current);
		} else if (doubleClick) {
			s.current = path; // browse into the folder (and undo the first click's toggle)
			if (!s.expanded.remove(path)) s.expanded.add(path);
		} else if (!s.expanded.remove(path)) {
			s.expanded.add(path);
		}
		if (path != null) Drag.press(list, Space.folderKey(path), Ui.format(Space.name(path)).getString());
		Store.save();
		list.worldfolders$refresh();
		return false;
	}

	public interface Holder {
		FolderRow row();
	}

	public static final class World extends WorldSelectionList.Entry implements Holder {
		private final FolderRow row;

		public World(Hooks.OrganizedList list, @Nullable String path, int depth) {
			row = new FolderRow(list, path, depth, WorldMenus::folder);
			row.create = WorldMenus::createIn;
		}

		@Override
		public FolderRow row() {
			return row;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovering, float partialTick) {
			row.render(g, getContentX(), getContentY(), getContentHeight(), mouseX, mouseY, hovering);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return row.click(event, doubleClick);
		}

		@Override
		public Component getNarration() {
			return row.label();
		}
	}

	public static final class Server extends ServerSelectionList.Entry implements Holder {
		private final FolderRow row;

		public Server(Hooks.OrganizedList list, @Nullable String path, int depth) {
			row = new FolderRow(list, path, depth, ServerMenus::folder);
		}

		@Override
		public FolderRow row() {
			return row;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovering, float partialTick) {
			row.render(g, getContentX(), getContentY(), getContentHeight(), mouseX, mouseY, hovering);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return row.click(event, doubleClick);
		}

		@Override
		public void join() {
		}

		@Override
		public boolean matches(ServerSelectionList.Entry other) {
			return other instanceof Server s && java.util.Objects.equals(s.row.path, row.path);
		}

		@Override
		public Component getNarration() {
			return row.label();
		}
	}

	/** A folder among the available resource packs. */
	public static final class Pack extends TransferableSelectionList.Entry implements Holder {
		private final FolderRow row;

		public Pack(TransferableSelectionList list, @Nullable String path, int depth) {
			list.super();
			row = new FolderRow((Hooks.OrganizedList) list, path, depth, PackMenus::folder);
		}

		@Override
		public FolderRow row() {
			return row;
		}

		@Override
		public String getPackId() {
			return "";
		}

		@Override
		public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovering, float partialTick) {
			row.render(g, getContentX(), getContentY(), getContentHeight(), mouseX, mouseY, hovering);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return row.click(event, doubleClick);
		}

		@Override
		public Component getNarration() {
			return row.label();
		}
	}
}
