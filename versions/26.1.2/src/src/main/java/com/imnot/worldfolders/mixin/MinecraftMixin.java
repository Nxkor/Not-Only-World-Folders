package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Store;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts the time spent on each server (the game has no such stat on the client). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void worldfolders$serverTime(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		ServerData server = mc.getCurrentServer();
		if (mc.level != null && server != null && !mc.isLocalServer()) Store.tickServer(server.ip);
	}
}
