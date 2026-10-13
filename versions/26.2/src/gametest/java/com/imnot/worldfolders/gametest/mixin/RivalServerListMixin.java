package com.imnot.worldfolders.gametest.mixin;

import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
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
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return super.mouseReleased(event);
	}
}
