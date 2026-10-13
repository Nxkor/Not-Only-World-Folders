package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Store;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "This pack was made for another version": answered yes without showing it, if the options say so. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void worldfolders$skipPackWarning(Screen screen, CallbackInfo ci) {
		if (!Store.config().skipPackWarning || !(screen instanceof ConfirmScreen confirm)) return;
		if (!(confirm.getTitle().getContents() instanceof TranslatableContents title) || !title.getKey().equals("pack.incompatible.confirm.title")) return;
		ci.cancel();
		((ConfirmScreenAccessor) confirm).worldfolders$callback().accept(true);
	}
}
