package com.imnot.worldfolders.mixin.iris;

import com.imnot.worldfolders.Shaders;
import net.irisshaders.iris.shaderpack.discovery.ShaderpackDirectoryManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.List;

/** The packs Iris lists: its own folder's, plus the other instances', minus the trash. */
@Mixin(value = ShaderpackDirectoryManager.class, remap = false)
public abstract class ShaderpackDirectoryManagerMixin {
	@Shadow @Final private Path root;

	@Inject(method = "enumerate", at = @At("RETURN"), cancellable = true)
	private void worldfolders$allInstances(CallbackInfoReturnable<List<String>> cir) {
		cir.setReturnValue(Shaders.arrange(root, cir.getReturnValue()));
	}
}
