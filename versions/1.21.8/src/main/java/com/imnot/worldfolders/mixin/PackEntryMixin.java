package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Drag;
import com.imnot.worldfolders.FolderRow;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.PackMenus;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import com.imnot.worldfolders.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A resource pack row of the "Available" list: indent, favorite star, right-click menu, drag and drop. */
@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class PackEntryMixin implements Hooks.Item {
	@Shadow @Final private PackSelectionModel.Entry pack;
	@Shadow @Final private TransferableSelectionList parent;
	@Unique private int worldfolders$depth;

	@Shadow
	public abstract void keyboardSelection();

	@Unique
	private boolean worldfolders$organized() {
		return ((Hooks.OrganizedList) parent).worldfolders$organized();
	}

	/** Null for the rows we leave alone: our folder rows (pack rows too, for the list), the selected packs, data packs. */
	@Override
	public String worldfolders$key() {
		return this instanceof FolderRow.Holder || !worldfolders$organized() ? null : pack.getId();
	}

	@Override
	public void worldfolders$setDepth(int depth) {
		worldfolders$depth = depth;
	}

	@Override
	public long worldfolders$date() {
		return 0;
	}

	@Override
	public long worldfolders$version() {
		return 0;
	}

	// --- indent inside expanded folders (render position and the matching click zone) ---

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int worldfolders$indentLeft(int left) {
		return left + worldfolders$depth * Ui.INDENT;
	}

	@ModifyVariable(method = "mouseClicked", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double worldfolders$indentClick(double x) {
		return x - worldfolders$depth * Ui.INDENT;
	}

	/** Favorite star on the corner of the icon; the description while hovered. */
	@Inject(method = "render", at = @At("TAIL"))
	private void worldfolders$decorate(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick, CallbackInfo ci) {
		if (!worldfolders$organized()) return;
		Space s = Store.packs();
		String id = pack.getId();
		if (s.selection.contains(id)) g.fill(left - 2, top - 2, left + width - 3, top + height + 2, Ui.SELECTED);
		if (s.favorites.contains(id)) g.drawString(Minecraft.getInstance().font, "★", left + 24, top, 0xFFFFD700);
		if (hovering) Ui.showDescription(g, s.descriptions.get(id));
	}

	/** Right-click: pack menu. Ctrl / Shift + click: multi-selection. Left press beside the icon: may start a drag. */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void worldfolders$click(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
		if (!worldfolders$organized()) return;
		TransferableSelectionList.PackEntry self = (TransferableSelectionList.PackEntry) (Object) this;
		String id = pack.getId();
		if (button == 1) {
			parent.setSelected(self);
			PackMenus.pack(id, pack.getTitle(), this::keyboardSelection);
			cir.setReturnValue(true);
		} else if (button == 0 && (Ui.ctrl() || Ui.shift())) {
			Store.packs().select(id, Ui.shift(), Hooks.keys(parent));
			cir.setReturnValue(true);
		} else if (button == 0 && Ui.mouseX() - parent.getRowLeft() - worldfolders$depth * Ui.INDENT > 32) { // a click on the icon selects the pack
			Drag.press((Hooks.OrganizedList) parent, id, pack.getTitle().getString());
		}
	}
}
