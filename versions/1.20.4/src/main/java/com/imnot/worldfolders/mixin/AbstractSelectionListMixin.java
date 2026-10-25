package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Hooks;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right-clicks on the world/server lists. An injection in their shared parent, not a mouseClicked
 * merged into each list: Mixin keeps one merged method per class, so another mod adding its own
 * (the Feather/Dawn client does) silently removed ours.
 */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void worldfolders$rightClick(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
		if (button == 1 && (Object) this instanceof Hooks.RowMenu rows && rows.worldfolders$rowMenu(x, y)) {
			cir.setReturnValue(true);
			return;
		}
		if (button != 1 || !((Object) this instanceof Hooks.OrganizedList list) || !((AbstractSelectionList<?>) (Object) this).isMouseOver(x, y)) return;
		if (list.worldfolders$rowAt(x, y) instanceof GuiEventListener row) row.mouseClicked(x, y, button);
		else list.worldfolders$backgroundMenu();
		// Always handled: a menu was added to the screen's widgets, so the screen must stop going through them.
		cir.setReturnValue(true);
	}
}
