package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Drag;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.ServerMenus;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import com.imnot.worldfolders.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Objects;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class OnlineServerEntryMixin implements Hooks.Item {
	@Shadow @Final private JoinMultiplayerScreen screen;
	@Shadow @Final private ServerData serverData;
	@Unique private int worldfolders$depth;

	@Override
	public String worldfolders$key() {
		return ServerMenus.key(serverData);
	}

	@Override
	public void worldfolders$setDepth(int depth) {
		worldfolders$depth = depth;
	}

	@Override
	public long worldfolders$date() {
		return Store.stats(serverData.ip).lastPlayed;
	}

	/** The protocol from the last successful ping, remembered: this list is arranged before the pings answer. */
	@Override
	public long worldfolders$version() {
		return Store.stats(serverData.ip).protocol;
	}

	@Unique
	private ServerSelectionList worldfolders$list() {
		return (ServerSelectionList) ((Hooks.ListHolder) screen).worldfolders$list();
	}

	// --- indent inside expanded folders (render position and the matching click zone) ---

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int worldfolders$indentLeft(int left) {
		return left + worldfolders$depth * Ui.INDENT;
	}

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private int worldfolders$indentWidth(int width) {
		return width - worldfolders$depth * Ui.INDENT;
	}

	@ModifyVariable(method = "mouseClicked", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double worldfolders$indentClick(double x) {
		return x - worldfolders$depth * Ui.INDENT;
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void worldfolders$decorate(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick, CallbackInfo ci) {
		Space s = Store.servers();
		String key = ServerMenus.key(serverData);
		if (s.selection.contains(key)) g.fill(left - 2, top - 2, left + width - 3, top + height + 2, Ui.SELECTED);
		if (s.favorites.contains(key)) g.drawString(Minecraft.getInstance().font, "★", left + width - 12, top + 12, 0xFFFFD700);
		if (serverData.state() == ServerData.State.SUCCESSFUL || serverData.state() == ServerData.State.INCOMPATIBLE) worldfolders$learnVersion();
		if (hovering && worldfolders$list().getSelected() == (Object) this) {
			Ui.showDescription(g, s.descriptions.get(key), Ui.playTime(Store.stats(serverData.ip).playSeconds));
		}
	}

	@Unique
	private void worldfolders$learnVersion() {
		if (Store.stats(serverData.ip).protocol == serverData.protocol) return;
		Store.statsForUpdate(serverData.ip).protocol = serverData.protocol;
		Store.save();
		Space.Sort sort = Store.servers().sort;
		if (sort == Space.Sort.VERSION_NEWEST || sort == Space.Sort.VERSION_OLDEST) { // re-sort, after this frame
			Minecraft.getInstance().execute(((Hooks.OrganizedList) worldfolders$list())::worldfolders$refresh);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void worldfolders$click(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
		ServerSelectionList.OnlineServerEntry self = (ServerSelectionList.OnlineServerEntry) (Object) this;
		if (button == 1) {
			screen.setSelected(self);
			ServerMenus.server(screen, self);
			cir.setReturnValue(true);
		} else if (button == 0 && (Ui.ctrl() || Ui.shift())) { // multi-selection: no vanilla select, no double-click join
			Store.servers().select(ServerMenus.key(serverData), Ui.shift(), Hooks.keys(worldfolders$list()));
			cir.setReturnValue(true);
		} else if (button == 0) {
			Drag.press((Hooks.OrganizedList) worldfolders$list(), ServerMenus.key(serverData), serverData.name);
		}
	}

	/**
	 * Vanilla's move up/down (Shift + arrows) swaps by row index in servers.dat, which no longer matches
	 * once rows are arranged in folders. Move the shown server past its shown neighbour instead, in the
	 * same manual order as drag and drop.
	 */
	@Inject(method = "swap", at = @At("HEAD"), cancellable = true)
	private void worldfolders$swapShown(int i, int j, CallbackInfo ci) {
		ci.cancel();
		ServerSelectionList list = worldfolders$list();
		List<ServerSelectionList.Entry> rows = list.children();
		if (i < 0 || j < 0 || i >= rows.size() || j >= rows.size() || !Store.servers().sort.manual()) return;
		String a = Hooks.keyOf(rows.get(i)), b = Hooks.keyOf(rows.get(j));
		if (a == null || b == null) return;
		Store.servers().placeNear(List.of(a), b, j > i, rows.stream().map(Hooks::keyOf).filter(Objects::nonNull).toList());
		Store.save();
		list.updateOnlineServers(screen.getServers());
		for (ServerSelectionList.Entry e : list.children()) {
			if (a.equals(Hooks.keyOf(e))) screen.setSelected(e);
		}
	}
}
