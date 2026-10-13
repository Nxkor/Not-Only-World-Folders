package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Drag;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import com.imnot.worldfolders.Ui;
import com.imnot.worldfolders.WorldMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldListEntryMixin implements Hooks.Item {
	@Shadow @Final LevelSummary summary;
	@Shadow @Final private SelectWorldScreen screen;
	@Unique private int worldfolders$depth;
	@Unique private long worldfolders$playSeconds = -1;

	@Override
	public String worldfolders$key() {
		return summary.getLevelId();
	}

	@Override
	public void worldfolders$setDepth(int depth) {
		worldfolders$depth = depth;
	}

	@Override
	public long worldfolders$date() {
		return summary.getLastPlayed();
	}

	@Override
	public long worldfolders$version() {
		return summary.levelVersion().minecraftVersion().version();
	}

	@Unique
	private WorldSelectionList worldfolders$list() {
		return (WorldSelectionList) ((Hooks.ListHolder) screen).worldfolders$list();
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

	/** Favorite star; description and play time while the selected world is hovered. */
	@Inject(method = "render", at = @At("TAIL"))
	private void worldfolders$decorate(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick, CallbackInfo ci) {
		Space s = Store.worlds();
		String id = summary.getLevelId();
		if (s.selection.contains(id)) g.fill(left - 2, top - 2, left + width - 3, top + height + 2, Ui.SELECTED);
		if (s.favorites.contains(id)) g.drawString(Minecraft.getInstance().font, "★", left + width - 12, top + 1, 0xFFFFD700);
		if (!hovering || worldfolders$list().getSelected() != (Object) this) return;
		if (worldfolders$playSeconds < 0) worldfolders$playSeconds = WorldMenus.playSeconds(id); // read once per list load
		Ui.showDescription(g, s.descriptions.get(id), Ui.playTime(worldfolders$playSeconds), Ui.size(WorldMenus.sizeOf(id)));
	}

	/** Right-click: world menu. Left press: may start a drag. */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void worldfolders$click(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
		WorldSelectionList.WorldListEntry self = (WorldSelectionList.WorldListEntry) (Object) this;
		if (button == 1) {
			worldfolders$list().setSelected(self);
			WorldMenus.world(self, summary);
			cir.setReturnValue(true);
		} else if (button == 0 && (Ui.ctrl() || Ui.shift())) { // multi-selection: no vanilla select, no double-click join
			Store.worlds().select(summary.getLevelId(), Ui.shift(), Hooks.keys(worldfolders$list()));
			cir.setReturnValue(true);
		} else if (button == 0) {
			Drag.press((Hooks.OrganizedList) worldfolders$list(), summary.getLevelId(), summary.getLevelName());
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
			((Hooks.OrganizedList) worldfolders$list()).worldfolders$refresh();
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
