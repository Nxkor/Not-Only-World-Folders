package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Hooks;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right-click on the empty part of the world/server lists. An injection in the parent that declares
 * mouseClicked, not a mouseClicked merged into each list: Mixin keeps one merged method per class,
 * so another mod adding its own (the Feather/Dawn client does) silently removed ours.
 */
@Mixin(AbstractContainerWidget.class)
public abstract class AbstractContainerWidgetMixin {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void worldfolders$background(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && (Object) this instanceof Hooks.RowMenu rows && rows.worldfolders$rowMenu(event.x(), event.y())) {
			cir.setReturnValue(true);
			return;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT || !((Object) this instanceof Hooks.OrganizedList list)) return;
		if (!((AbstractContainerWidget) (Object) this).isMouseOver(event.x(), event.y()) || list.worldfolders$rowAt(event.x(), event.y()) != null) return;
		list.worldfolders$backgroundMenu();
		cir.setReturnValue(true);
	}
}
