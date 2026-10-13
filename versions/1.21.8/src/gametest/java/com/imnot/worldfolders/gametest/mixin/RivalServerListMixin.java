package com.imnot.worldfolders.gametest.mixin;

import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Stands for another mod that merges its own mouse methods into the list, as the Feather/Dawn client
 * does. Mixin keeps only one such method per class, so nothing of World Folders may depend on merging
 * its own (see AbstractContainerWidgetMixin and MouseHandlerMixin).
 */
@Mixin(value = ServerSelectionList.class, priority = 1500)
public abstract class RivalServerListMixin extends ObjectSelectionList<ServerSelectionList.Entry> {
	private RivalServerListMixin() {
		super(null, 0, 0, 0, 0);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		return super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		return super.mouseDragged(x, y, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		return super.mouseReleased(x, y, button);
	}
}
