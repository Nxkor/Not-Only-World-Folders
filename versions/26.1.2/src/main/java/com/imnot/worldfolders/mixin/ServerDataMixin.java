package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.Store;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Gives each saved server a stable id (name and address can both be edited). It is stored as an
 * extra tag in servers.dat, which vanilla ignores, so removing the mod is harmless.
 */
@Mixin(ServerData.class)
public abstract class ServerDataMixin implements Hooks.ServerKey {
	@Unique private static final String TAG = "worldfolders_id";
	@Unique private String worldfolders$id;

	@Override
	public String worldfolders$key() {
		if (worldfolders$id == null) {
			worldfolders$id = UUID.randomUUID().toString();
			Store.serverIdsDirty = true;
		}
		return worldfolders$id;
	}

	@Override
	public void worldfolders$setKey(String key) {
		worldfolders$id = key;
	}

	@Inject(method = "write", at = @At("RETURN"))
	private void worldfolders$writeId(CallbackInfoReturnable<CompoundTag> cir) {
		cir.getReturnValue().putString(TAG, worldfolders$key());
	}

	@Inject(method = "read", at = @At("RETURN"))
	private static void worldfolders$readId(CompoundTag tag, CallbackInfoReturnable<ServerData> cir) {
		tag.getString(TAG).ifPresent(((Hooks.ServerKey) cir.getReturnValue())::worldfolders$setKey);
	}
}
