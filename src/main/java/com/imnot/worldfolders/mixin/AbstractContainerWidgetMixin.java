package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Hooks;
import net.minecraft.client.gui.components.AbstractContainerWidget;
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
	private void worldfolders$background(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
		if (button != 1 || !((Object) this instanceof Hooks.OrganizedList list)) return;
		if (!((AbstractContainerWidget) (Object) this).isMouseOver(x, y) || list.worldfolders$rowAt(x, y) != null) return;
		list.worldfolders$backgroundMenu();
		cir.setReturnValue(true);
	}
}
