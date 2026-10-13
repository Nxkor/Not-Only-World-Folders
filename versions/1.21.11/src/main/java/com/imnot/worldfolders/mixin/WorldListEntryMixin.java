package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Drag;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import com.imnot.worldfolders.Ui;
import com.imnot.worldfolders.WorldMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldListEntryMixin implements Hooks.Item {
	@Shadow @Final LevelSummary summary;
	@Shadow @Final private WorldSelectionList list;
	@Unique private int worldfolders$depth;
	@Unique private long worldfolders$playSeconds = -1;

	@Override
	public String worldfolders$key() {
		return summary.getLevelId();
	}

	@Override
	public int worldfolders$depth() {
		return worldfolders$depth;
	}

	@Override
	public long worldfolders$date() {
		return summary.getLastPlayed();
	}

	@Override
	public long worldfolders$version() {
		return summary.levelVersion().minecraftVersion().version();
	}

	@Override
	public void worldfolders$setDepth(int depth) {
		worldfolders$depth = depth;
	}

	/** Favorite star; description and play time while the selected world is hovered. */
	@Inject(method = "renderContent", at = @At("TAIL"))
	private void worldfolders$decorate(GuiGraphics g, int mouseX, int mouseY, boolean hovering, float partialTick, CallbackInfo ci) {
		WorldSelectionList.Entry self = (WorldSelectionList.Entry) (Object) this;
		Space s = Store.worlds();
		String id = summary.getLevelId();
		if (s.selection.contains(id)) g.fill(self.getContentX() - 2, self.getContentY() - 2, self.getContentRight() - 1, self.getContentBottom() + 2, Ui.SELECTED);
		if (s.favorites.contains(id)) g.drawString(Minecraft.getInstance().font, "★", self.getContentRight() - 10, self.getContentY() + 1, 0xFFFFD700);
		if (!hovering || list.getSelected() != self) return;
		if (worldfolders$playSeconds < 0) worldfolders$playSeconds = WorldMenus.playSeconds(id); // read once per list load
		Ui.showDescription(g, s.descriptions.get(id), mouseX, mouseY, Ui.playTime(worldfolders$playSeconds), Ui.size(WorldMenus.sizeOf(id)));
	}

	/** Right-click: world menu. Left press: may start a drag. */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void worldfolders$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		WorldSelectionList.WorldListEntry self = (WorldSelectionList.WorldListEntry) (Object) this;
		if (event.button() == 1) {
			list.setSelected(self);
			WorldMenus.world(self, summary);
			cir.setReturnValue(true);
		} else if (event.button() == 0 && (Ui.ctrl() || Ui.shift())) { // multi-selection: no vanilla select, no double-click join
			Store.worlds().select(summary.getLevelId(), Ui.shift(), Hooks.keys(list));
			cir.setReturnValue(true);
		} else if (event.button() == 0) {
			Drag.press((Hooks.OrganizedList) list, summary.getLevelId(), summary.getLevelName());
		}
	}

	/** "Delete" sends to the trash; only deleting from the trash view really removes the world. */
	@Inject(method = "deleteWorld", at = @At("HEAD"), cancellable = true)
	private void worldfolders$trash(CallbackInfo ci) {
		Space s = Store.worlds();
		if (s.trashView) return;
		ci.cancel();
		if (s.toTrash(summary.getLevelId())) {
			Store.save();
			((Hooks.OrganizedList) list).worldfolders$refresh();
		} else {
			Ui.toast(Ui.tr("favorite_protected"), Component.literal(summary.getLevelName()));
		}
	}

	@Inject(method = "doDeleteWorld", at = @At("HEAD"))
	private void worldfolders$forget(CallbackInfo ci) {
		Store.worlds().forget(summary.getLevelId());
		Ui.notify(Ui.tr("deleted"), Component.literal(summary.getLevelName()));
		Store.save();
	}
}
