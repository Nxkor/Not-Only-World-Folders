package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.PackMenus;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The "Available" list of the resource pack screen is organised like the worlds: folders, favorites, trash. */
@Mixin(TransferableSelectionList.class)
public abstract class TransferableSelectionListMixin extends ObjectSelectionList<TransferableSelectionList.PackEntry> implements Hooks.OrganizedList {
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

	/** The screen fills both lists, then arranges this one (PackSelectionScreenMixin). */
	@Override
	public void worldfolders$refresh() {
		if (worldfolders$organized()) ((Hooks.PackScreen) screen).worldfolders$populate();
	}

	@Override
	public void worldfolders$reload() {
		if (worldfolders$organized()) ((Hooks.PackScreen) screen).worldfolders$reload();
	}

	@Override
	public @Nullable Object worldfolders$rowAt(double x, double y) {
		return getEntryAtPosition(x, y);
	}

	@Override
	public double worldfolders$rowFraction(Object row, double y) {
		return (y - getRowTop(children().indexOf(row))) / itemHeight;
	}

	// Mouse input comes through AbstractContainerWidgetMixin and MouseHandlerMixin, as for the worlds.

	@Override
	public void worldfolders$backgroundMenu() {
		if (worldfolders$organized()) PackMenus.background(screen);
	}
}
