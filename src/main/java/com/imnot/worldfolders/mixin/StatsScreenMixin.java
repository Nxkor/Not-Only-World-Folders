package com.imnot.worldfolders.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The statistics of a world read from its folder: no server to ask them from (the last thing init does). */
@Mixin(StatsScreen.class)
public abstract class StatsScreenMixin {
	@Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getConnection()Lnet/minecraft/client/multiplayer/ClientPacketListener;"), cancellable = true)
	private void worldfolders$offline(CallbackInfo ci) {
		if (Minecraft.getInstance().getConnection() == null) ci.cancel();
	}
}
