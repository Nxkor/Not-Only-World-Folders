package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.Ui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Indents world/server rows shown inside an expanded folder; vanilla draws and hit-tests from these. */
@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList$Entry")
public abstract class EntryMixin {
	@Inject(method = "getContentX", at = @At("RETURN"), cancellable = true)
	private void worldfolders$indentX(CallbackInfoReturnable<Integer> cir) {
		if (this instanceof Hooks.Item item) cir.setReturnValue(cir.getReturnValue() + item.worldfolders$depth() * Ui.INDENT);
	}

	@Inject(method = "getContentWidth", at = @At("RETURN"), cancellable = true)
	private void worldfolders$indentWidth(CallbackInfoReturnable<Integer> cir) {
		if (this instanceof Hooks.Item item) cir.setReturnValue(cir.getReturnValue() - item.worldfolders$depth() * Ui.INDENT);
	}
}
