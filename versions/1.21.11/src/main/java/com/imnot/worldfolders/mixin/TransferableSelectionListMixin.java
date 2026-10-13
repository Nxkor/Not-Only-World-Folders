package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.FolderRow;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.PackMenus;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** The "Available" list of the resource pack screen is organised like the worlds: folders, favorites, trash. */
@Mixin(TransferableSelectionList.class)
public abstract class TransferableSelectionListMixin extends ObjectSelectionList<TransferableSelectionList.Entry> implements Hooks.OrganizedList {
	@Shadow @Final PackSelectionScreen screen;

	private TransferableSelectionListMixin() {
		super(null, 0, 0, 0, 0);
	}

	@Override
	public Space worldfolders$space() {
		return Store.packs();
	}

	@Override
	public boolean worldfolders$organized() {
		return screen instanceof Hooks.ListHolder holder && holder.worldfolders$list() == (Object) this;
	}

	/** The screen fills both lists; this one is then arranged below. */
	@Override
	public void worldfolders$refresh() {
		if (worldfolders$organized()) ((Hooks.PackScreen) screen).worldfolders$populate();
	}

	@Override
	public void worldfolders$reload() {
		if (worldfolders$organized()) ((Hooks.PackScreen) screen).worldfolders$reload();
	}

	@Override
	public boolean worldfolders$searching() {
		return ((Hooks.PackScreen) screen).worldfolders$searching();
	}

	/** Vanilla filled the list (title row, then the packs the search keeps); re-order the packs into the folder tree. */
	@Inject(method = "updateList", at = @At("TAIL"))
	private void worldfolders$arrange(Stream<PackSelectionModel.Entry> packs, PackSelectionModel.EntryBase focus, CallbackInfo ci) {
		if (!worldfolders$organized()) return;
		TransferableSelectionList self = (TransferableSelectionList) (Object) this;
		List<TransferableSelectionList.Entry> rows = new ArrayList<>(children());
		TransferableSelectionList.Entry title = !rows.isEmpty() && rows.get(0) instanceof TransferableSelectionList.HeaderEntry ? rows.remove(0) : null;
		int titleHeight = title == null ? 0 : title.getHeight();
		List<TransferableSelectionList.Entry> arranged = Store.packs().arrange(rows, Hooks::keyOf,
				Hooks.rows((path, depth) -> new FolderRow.Pack(self, path, depth)), worldfolders$searching());
		TransferableSelectionList.Entry selected = getSelected();
		removeEntries(new ArrayList<>(children())); // children() is read-only
		if (title != null) addEntry(title, titleHeight);
		arranged.forEach(this::addEntry); // addEntry lays the rows out one under the other
		if (arranged.contains(selected)) setSelected(selected);
		refreshScrollAmount();
		if (screen instanceof Hooks.ListHolder holder) holder.worldfolders$updateButtons(); // e.g. the trash count
	}

	@Override
	public @Nullable Object worldfolders$rowAt(double x, double y) {
		return getEntryAtPosition(x, y) instanceof TransferableSelectionList.HeaderEntry ? null : getEntryAtPosition(x, y); // the title row is background
	}

	@Override
	public double worldfolders$rowFraction(Object row, double y) {
		return row instanceof net.minecraft.client.gui.components.AbstractSelectionList.Entry<?> entry ? (y - entry.getY()) / entry.getHeight() : 0.5;
	}

	// Mouse input comes through AbstractContainerWidgetMixin and MouseHandlerMixin, as for the worlds.

	@Override
	public void worldfolders$backgroundMenu() {
		if (worldfolders$organized()) PackMenus.background(screen);
	}
}
