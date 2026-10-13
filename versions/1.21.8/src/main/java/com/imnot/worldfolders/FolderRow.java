package com.imnot.worldfolders;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
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
	private static String lastClickPath;
	private static long lastClickTime;

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

	void render(GuiGraphics g, int top, int left, int height, boolean hovering) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int x = left + depth * Ui.INDENT;
		int mid = top + (height - 8) / 2;
		Space s = list.worldfolders$space();
		if (path != null) g.drawString(font, s.expanded.contains(path) ? "▼" : "▶", x, mid, 0xFFAAAAAA);
		g.renderItem(icon(), x + 10, top + (height - 16) / 2);
		g.drawString(font, label(), x + 32, mid, hovering ? 0xFFFFFF55 : 0xFFFFFFFF);
		if (create != null) { // a small "+" after the name: a new world, straight into this folder
			plusX = x + 32 + font.width(label()) + 6;
			g.fill(plusX, mid - 1, plusX + 9, mid + 8, hovering && overPlus() ? 0xC040A040 : 0x50FFFFFF);
			g.drawString(font, "+", plusX + 2, mid, 0xFFFFFFFF);
		}
		String description = create != null && hovering && overPlus() ? Ui.tr("new_world_here").getString()
				: path == null ? null : s.folderDescriptions.get(path);
		if (description != null && hovering && mc.screen != null) Ui.showDescription(g, description);
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
	boolean click(int button) {
		Space s = list.worldfolders$space();
		if (button == 1) {
			menu.accept(this);
			return false;
		}
		if (button != 0) return false;
		if (create != null && overPlus()) {
			create.accept(path == null ? s.current : path);
			return true;
		}
		if (path == null) {
			s.current = Space.parent(s.current);
		} else if (path.equals(lastClickPath) && Util.getMillis() - lastClickTime < 250) {
			s.current = path; // double-click: browse into the folder (undo the first click's toggle)
			if (!s.expanded.remove(path)) s.expanded.add(path);
		} else if (!s.expanded.remove(path)) {
			s.expanded.add(path);
		}
		if (path != null) Drag.press(list, Space.folderKey(path), Ui.format(Space.name(path)).getString());
		lastClickPath = path;
		lastClickTime = Util.getMillis();
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
		public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
			row.render(g, top, left, height, hovering);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return row.click(button);
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
		public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
			row.render(g, top, left, height, hovering);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return row.click(button);
		}

		@Override
		public Component getNarration() {
			return row.label();
		}
	}

	/** A folder among the available resource packs. The list only takes pack rows, so it is one, of a pack that does nothing. */
	public static final class Pack extends TransferableSelectionList.PackEntry implements Holder {
		private static final PackSelectionModel.Entry NONE = new PackSelectionModel.Entry() {
			public ResourceLocation getIconTexture() { return null; }
			public PackCompatibility getCompatibility() { return PackCompatibility.COMPATIBLE; }
			public String getId() { return ""; }
			public Component getTitle() { return Component.empty(); }
			public Component getDescription() { return Component.empty(); }
			public PackSource getPackSource() { return PackSource.BUILT_IN; }
			public boolean isFixedPosition() { return true; }
			public boolean isRequired() { return true; }
			public void select() { }
			public void unselect() { }
			public void moveUp() { }
			public void moveDown() { }
			public boolean isSelected() { return true; }
			public boolean canMoveUp() { return false; }
			public boolean canMoveDown() { return false; }
		};
		private final FolderRow row;

		public Pack(TransferableSelectionList list, @Nullable String path, int depth) {
			super(Minecraft.getInstance(), list, NONE);
			row = new FolderRow((Hooks.OrganizedList) list, path, depth, PackMenus::folder);
		}

		@Override
		public FolderRow row() {
			return row;
		}

		@Override
		public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
			row.render(g, top, left, height, hovering);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return row.click(button);
		}

		@Override
		public Component getNarration() {
			return row.label();
		}
	}
}
