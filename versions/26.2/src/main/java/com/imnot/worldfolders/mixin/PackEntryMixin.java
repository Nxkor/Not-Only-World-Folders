package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Drag;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.PackMenus;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import com.imnot.worldfolders.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A resource pack row of the "Available" list: indent (EntryMixin), favorite star, right-click menu, drag and drop. */
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

	/** Null for the rows we leave alone: the selected packs, data packs. */
	@Override
	public String worldfolders$key() {
		return worldfolders$organized() ? pack.getId() : null;
	}

	@Override
	public int worldfolders$depth() {
		return worldfolders$depth;
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

	/** Favorite star on the corner of the icon; the description while hovered. */
	@Inject(method = "extractContent", at = @At("TAIL"))
	private void worldfolders$decorate(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovering, float partialTick, CallbackInfo ci) {
		if (!worldfolders$organized()) return;
		TransferableSelectionList.Entry self = (TransferableSelectionList.Entry) (Object) this;
		Space s = Store.packs();
		String id = pack.getId();
		if (s.selection.contains(id)) g.fill(self.getContentX() - 2, self.getContentY() - 2, self.getContentRight() - 1, self.getContentBottom() + 2, Ui.SELECTED);
		if (s.favorites.contains(id)) g.text(Minecraft.getInstance().font, "★", self.getContentX() + 24, self.getContentY(), 0xFFFFD700);
		if (hovering) Ui.showDescription(g, s.descriptions.get(id), mouseX, mouseY);
	}

	/** Right-click: pack menu. Ctrl / Shift + click: multi-selection. Left press beside the icon: may start a drag. */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void worldfolders$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (!worldfolders$organized()) return;
		TransferableSelectionList.Entry self = (TransferableSelectionList.Entry) (Object) this;
		String id = pack.getId();
		if (event.button() == 1) {
			parent.setSelected(self);
			PackMenus.pack(id, pack.getTitle(), this::keyboardSelection);
			cir.setReturnValue(true);
		} else if (event.button() == 0 && (Ui.ctrl() || Ui.shift())) {
			Store.packs().select(id, Ui.shift(), Hooks.keys(parent));
			cir.setReturnValue(true);
		} else if (event.button() == 0 && event.x() - self.getContentX() > 32) { // a click on the icon selects the pack
			Drag.press((Hooks.OrganizedList) parent, id, pack.getTitle().getString());
		}
	}
}
