package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.FolderRow;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.ServerMenus;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerSelectionList.class)
public abstract class ServerSelectionListMixin extends ObjectSelectionList<ServerSelectionList.Entry> implements Hooks.OrganizedList {
	@Shadow @Final private JoinMultiplayerScreen screen;

	@Shadow
	private void refreshEntries() {
		throw new AssertionError();
	}

	private ServerSelectionListMixin() {
		super(null, 0, 0, 0, 0);
	}

	@Override
	public Space worldfolders$space() {
		return Store.servers();
	}

	@Override
	public void worldfolders$refresh() {
		refreshEntries();
	}

	/** Vanilla put saved servers, the LAN header and LAN servers; re-order into the folder tree. */
	@Inject(method = "refreshEntries", at = @At("TAIL"))
	private void worldfolders$arrange(CallbackInfo ci) {
		// Hidden server rows are kept by vanilla and shown again later, so they are not closed here.
		List<ServerSelectionList.Entry> arranged = Store.servers().arrange(new ArrayList<>(children()), Hooks::keyOf,
				Hooks.rows((path, depth) -> new FolderRow.Server(this, path, depth)), false);
		children().clear();
		children().addAll(arranged);
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

	// Mouse input comes through AbstractSelectionListMixin and MouseHandlerMixin: no mouse method is merged
	// into this class, where another mod's would replace it.

	@Override
	public void worldfolders$backgroundMenu() {
		ServerMenus.background(screen);
	}

	@Override
	public void worldfolders$dropped() {
		screen.getServers().save(); // the moved server's id must reach servers.dat
	}
}
