package com.imnot.worldfolders.mixin;

import com.google.common.collect.ImmutableMap;
import com.imnot.worldfolders.WorldMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.TreeMap;

/** The resource pack screen also lists the packs of the other instances of this computer. */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
	@Inject(method = "discoverAvailable", at = @At("RETURN"), cancellable = true)
	private void worldfolders$otherInstances(CallbackInfoReturnable<Map<String, Pack>> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getResourcePackRepository() != (Object) this) return; // not the data packs of a world
		Map<String, Pack> all = new TreeMap<>();
		WorldMenus.otherPacks(pack -> all.put(pack.getId(), pack));
		if (all.isEmpty()) return;
		all.putAll(cir.getReturnValue()); // the pack of this instance wins over a file of the same name elsewhere
		cir.setReturnValue(ImmutableMap.copyOf(all));
	}
}
