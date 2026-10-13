package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Store;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts the time spent on each server (the game has no such stat on the client), and skips the pack version warning. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void worldfolders$serverTime(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		ServerData server = mc.getCurrentServer();
		if (mc.level != null && server != null && !mc.isLocalServer()) Store.tickServer(server.ip);
	}

	/** "This pack was made for another version": answered yes without showing it, if the options say so. */
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void worldfolders$skipPackWarning(Screen screen, CallbackInfo ci) {
		if (!Store.config().skipPackWarning || !(screen instanceof ConfirmScreen confirm)) return;
		if (!(confirm.getTitle().getContents() instanceof TranslatableContents title) || !title.getKey().equals("pack.incompatible.confirm.title")) return;
		ci.cancel();
		((ConfirmScreenAccessor) confirm).worldfolders$callback().accept(true);
	}
}
