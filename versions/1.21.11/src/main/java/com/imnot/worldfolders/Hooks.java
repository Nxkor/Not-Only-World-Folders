package com.imnot.worldfolders;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** Interfaces our mixins add to vanilla classes. */
public final class Hooks {
	/** WorldSelectionList / ServerSelectionList. */
	public interface OrganizedList {
		Space worldfolders$space();

		/** Re-arranges the rows from the current data (after a folder change). */
		void worldfolders$refresh();

		/** Re-reads the items themselves (worlds from disk). */
		default void worldfolders$reload() {
			worldfolders$refresh();
		}

		/** Row under the mouse, or null. */
		@Nullable Object worldfolders$rowAt(double x, double y);

		/** Where {@code y} is in the height of {@code row}: 0 = its top, 1 = its bottom. */
		double worldfolders$rowFraction(Object row, double y);

		/** A search makes the list flat: dropping between two rows then means nothing. */
		default boolean worldfolders$searching() {
			return false;
		}

		/** Right-click on the empty part of the list: the menu of the folder being browsed. */
		void worldfolders$backgroundMenu();

		/** A drag and drop was saved; the server list has servers.dat to save too. */
		default void worldfolders$dropped() {
		}

		/** The screen is shown again (back from a prompt, a confirmation...). */
		default void worldfolders$shown() {
			worldfolders$refresh();
		}
		/** False for the lists of the pack screen we leave alone: the selected packs, and the data packs of a world. */
		default boolean worldfolders$organized() {
			return true;
		}
	}

	/** PackSelectionScreen: its two lists are filled by the screen. */
	public interface PackScreen {
		/** Fills the lists again from the packs known. */
		void worldfolders$populate();

		/** Reads the packs again from the disk. */
		void worldfolders$reload();

		/** The search box holds something: the lists are flat. */
		boolean worldfolders$searching();
	}

	/** World / server rows: their key in the Space, and their indent inside an expanded folder. */
	public interface Item {
		String worldfolders$key();

		int worldfolders$depth();

		void worldfolders$setDepth(int depth);

		/** For the sort dropdown: last played time, and a number that grows with the game version. */
		long worldfolders$date();

		long worldfolders$version();
	}

	/** Row factory for Space.arrange; {@code folder} gets a null path for the ".." row. */
	public static <E> Space.Rows<E> rows(java.util.function.BiFunction<String, Integer, E> folder) {
		return new Space.Rows<>() {
			public E folder(String path, int depth) {
				return folder.apply(path, depth);
			}

			public E up() {
				return folder.apply(null, 0);
			}

			public void setDepth(E item, int depth) {
				((Item) item).worldfolders$setDepth(depth);
			}

			public long date(E item) {
				return item instanceof Item i ? i.worldfolders$date() : 0;
			}

			public long version(E item) {
				return item instanceof Item i ? i.worldfolders$version() : 0;
			}
		};
	}

	/** Key of a row, or null for rows we don't organise (LAN servers, headers). */
	public static String keyOf(Object row) {
		return row instanceof Item i ? i.worldfolders$key() : null;
	}

	/** Keys of the world/server rows of {@code list} (an OrganizedList), in the order shown. */
	public static List<String> keys(Object list) {
		return ((AbstractSelectionList<?>) list).children().stream().map(Hooks::keyOf).filter(Objects::nonNull).toList();
	}

	/** The rows of {@code list} as the manual order knows them: the keys, and a folder key for each folder row. */
	public static List<String> slots(Object list) {
		return ((AbstractSelectionList<?>) list).children().stream()
				.map(row -> row instanceof FolderRow.Holder h ? h.row().path == null ? null : Space.folderKey(h.row().path) : keyOf(row)).filter(Objects::nonNull).toList();
	}

	/** SelectWorldScreen / JoinMultiplayerScreen. */
	public interface ListHolder {
		ObjectSelectionList<?> worldfolders$list();

		/** Syncs our buttons (trash label, "New folder" visibility) with the current view. */
		default void worldfolders$updateButtons() {
		}

		/** Our "New folder" and "Trash" buttons, drop targets too. */
		@Nullable Button worldfolders$newFolderButton();

		@Nullable Button worldfolders$trashButton();

		/** The arrows of the sort button, drawn over it (see Ui.renderSortArrows). */
		default void worldfolders$renderSortArrows(GuiGraphics g) {
		}

		/** The saves folder as it is now is what the list shows, or is about to read: no automatic reload for it. */
		default void worldfolders$upToDate() {
		}

		/** Every client tick while shown. */
		default void worldfolders$tick() {
		}
	}

	/** ServerData gets a stable id, since name and address can both change. */
	public interface ServerKey {
		String worldfolders$key();

		void worldfolders$setKey(String key);
	}

	/** A list of another mod (the shaders of Iris) with a right-click menu on its rows. */
	public interface RowMenu {
		/** True if a row was under the mouse and got its menu. */
		boolean worldfolders$rowMenu(double x, double y);
	}

	/** Every Screen can show one context menu on top of itself. */
	public interface Overlay {
		void worldfolders$setMenu(@Nullable ContextMenu menu);

		@Nullable ContextMenu worldfolders$menu();
	}
}
