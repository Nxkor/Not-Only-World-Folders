package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.InstanceOptions;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** At startup the game reads the options of the instance picked in the options screen, if one is. */
@Mixin(Options.class)
public abstract class OptionsMixin {
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;load()V"))
	private void worldfolders$pickedFile(CallbackInfo ci) {
		Options self = (Options) (Object) this;
		((OptionsAccessor) self).worldfolders$setFile(InstanceOptions.file(self.getFile()));
	}
}
