package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.FolderRow;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import com.imnot.worldfolders.WorldMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(WorldSelectionList.class)
public abstract class WorldSelectionListMixin extends ObjectSelectionList<WorldSelectionList.Entry> implements Hooks.OrganizedList {
	@Shadow @Nullable private List<LevelSummary> currentlyDisplayedLevels;
	@Shadow private String filter;
	@Shadow @Final private Screen screen;
	@Shadow @Final WorldSelectionList.EntryType entryType;
	/** The search the rows shown were filled for. */
	@Unique private String worldfolders$search = "";

	@Shadow
	private void fillLevels(String filter, List<LevelSummary> levels) {
		throw new AssertionError();
	}

	@Shadow
	public abstract void reloadWorldList();

	private WorldSelectionListMixin() {
		super(null, 0, 0, 0, 0);
	}

	@Override
	public Space worldfolders$space() {
		return Store.worlds();
	}

	/** Every row vanilla made for the current worlds and search, shown or hidden: re-arranging re-uses them, icons included. */
	@Unique private @Nullable List<WorldSelectionList.Entry> worldfolders$rows;

	@Override
	public void worldfolders$refresh() {
		if (worldfolders$rows != null) worldfolders$layout();
	}

	/**
	 * Since 1.21.9 a screen isn't re-initialised when shown again, and leaving it closed the rows' icons:
	 * make the rows again. Only the list on display: an older one replaced by the screen's init is dead.
	 */
	@Override
	public void worldfolders$shown() {
		if (currentlyDisplayedLevels != null && Minecraft.getInstance().screen == screen
				&& screen instanceof Hooks.ListHolder holder && holder.worldfolders$list() == (Object) this) fillLevels(filter, currentlyDisplayedLevels);
	}

	@Override
	public void worldfolders$reload() {
		reloadWorldList();
	}

	/** The saves folder of another instance, left without a world: back to this game's own before reading it. */
	@Inject(method = "loadLevels", at = @At("HEAD"))
	private void worldfolders$leaveEmptyInstance(CallbackInfoReturnable<CompletableFuture<List<LevelSummary>>> cir) {
		WorldMenus.leaveEmptyInstance();
		if (screen instanceof Hooks.ListHolder holder) holder.worldfolders$upToDate();
	}

	/** Vanilla filled the list (search applied); re-order it into the folder tree. Realms upload lists are left alone. */
	@Inject(method = "fillLevels", at = @At("TAIL"))
	private void worldfolders$arrange(String filter, List<LevelSummary> levels, CallbackInfo ci) {
		if (entryType != WorldSelectionList.EntryType.SINGLEPLAYER) return;
		worldfolders$search = filter; // vanilla sets its own field only after this call: read there, an emptied search still looked like one
		// A world made from a folder's "+" goes into that folder. Every world counts, not only those the search keeps.
		if (Store.worlds().adopt(levels.stream().map(LevelSummary::getLevelId).toList())) Store.save();
		worldfolders$rows = new ArrayList<>(children());
		worldfolders$layout();
	}

	/**
	 * Only moves rows: no world row is made again, so no icon is read from disk again. Hidden rows keep
	 * their icon; like vanilla's own rows it is replaced when the list is next filled, never closed here
	 * (closing an old row would release the icon of the new row of the same world).
	 */
	@Unique
	private void worldfolders$layout() {
		List<WorldSelectionList.Entry> arranged = Store.worlds().arrange(worldfolders$rows, Hooks::keyOf,
				Hooks.rows((path, depth) -> new FolderRow.World(this, path, depth)), !worldfolders$search.isEmpty());
		WorldSelectionList.Entry selected = getSelected();
		removeEntries(new ArrayList<>(children())); // children() is read-only; clearEntries() would close the icons we keep
		arranged.forEach(this::addEntry); // addEntry lays the rows out one under the other
		if (arranged.contains(selected)) setSelected(selected);
		refreshScrollAmount();
		if (screen instanceof Hooks.ListHolder holder) holder.worldfolders$updateButtons(); // e.g. the trash count
	}

	@Override
	public @Nullable Object worldfolders$rowAt(double x, double y) {
		return getEntryAtPosition(x, y);
	}

	@Override
	public double worldfolders$rowFraction(Object row, double y) {
		return row instanceof net.minecraft.client.gui.components.AbstractSelectionList.Entry<?> entry ? (y - entry.getY()) / entry.getHeight() : 0.5; // rows have their own height since 1.21.9
	}

	@Override
	public boolean worldfolders$searching() {
		return !worldfolders$search.isEmpty();
	}

	// Mouse input comes through AbstractContainerWidgetMixin and MouseHandlerMixin: no mouse method is merged
	// into this class, where another mod's would replace it.

	@Override
	public void worldfolders$backgroundMenu() {
		WorldMenus.background(screen);
	}
}
