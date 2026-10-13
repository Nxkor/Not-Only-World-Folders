package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.FolderRow;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import com.imnot.worldfolders.WorldMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
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
	/** The search the rows shown were filled for. */
	@Unique private String worldfolders$search = "";
	@Shadow @Final private SelectWorldScreen screen;

	@Shadow
	abstract void reloadWorldList();

	private WorldSelectionListMixin() {
		super(null, 0, 0, 0, 0);
	}

	@Override
	public Space worldfolders$space() {
		return Store.worlds();
	}

	/** Every row vanilla made for the current worlds and search, shown or hidden: re-arranging re-uses them, icons included. */
	@Unique private @Nullable List<WorldSelectionList.Entry> worldfolders$rows;

	/**
	 * Only the list on display: while a prompt covers the screen its rows are closed, and an older
	 * list replaced by the screen's init is dead. The screen refreshes when shown again (ScreenMixin).
	 */
	@Override
	public void worldfolders$refresh() {
		if (worldfolders$rows != null && Minecraft.getInstance().screen == screen
				&& ((Hooks.ListHolder) screen).worldfolders$list() == (Object) this) worldfolders$layout();
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

	/**
	 * Vanilla is about to close the shown rows and drop them all; the hidden ones are ours to close.
	 * ponytail: rows hidden when the screen itself closes keep their 64x64 icon until the list is next filled.
	 */
	@Inject(method = "clearEntries", at = @At("HEAD"))
	private void worldfolders$closeHidden(CallbackInfo ci) {
		if (worldfolders$rows == null) return;
		worldfolders$rows.removeAll(children()); // closing a row twice throws
		worldfolders$rows.forEach(WorldSelectionList.Entry::close);
		worldfolders$rows = null;
	}

	/** Vanilla filled the list (search applied); re-order it into the folder tree. */
	@Inject(method = "fillLevels", at = @At("TAIL"))
	private void worldfolders$arrange(String filter, List<LevelSummary> levels, CallbackInfo ci) {
		worldfolders$search = filter; // vanilla sets its own field only after this call: read there, an emptied search still looked like one
		// A world made from a folder's "+" goes into that folder. Every world counts, not only those the search keeps.
		if (Store.worlds().adopt(levels.stream().map(LevelSummary::getLevelId).toList())) Store.save();
		worldfolders$rows = new ArrayList<>(children());
		worldfolders$layout();
	}

	/** Only moves rows: no world row is made again, so no icon is read from disk again. */
	@Unique
	private void worldfolders$layout() {
		List<WorldSelectionList.Entry> arranged = Store.worlds().arrange(worldfolders$rows, Hooks::keyOf,
				Hooks.rows((path, depth) -> new FolderRow.World(this, path, depth)), !worldfolders$search.isEmpty());
		children().clear();
		children().addAll(arranged);
		if (getSelected() != null && !arranged.contains(getSelected())) setSelected(null); // e.g. sent to the trash
		if (screen instanceof Hooks.ListHolder holder) holder.worldfolders$updateButtons(); // e.g. the trash count
	}

	@Override
	public @Nullable Object worldfolders$rowAt(double x, double y) {
		return getEntryAtPosition(x, y);
	}

	@Override
	public double worldfolders$rowFraction(Object row, double y) {
		return (y - getRowTop(children().indexOf(row))) / itemHeight;
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
