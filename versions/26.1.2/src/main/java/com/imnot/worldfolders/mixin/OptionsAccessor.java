package com.imnot.worldfolders.mixin;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

@Mixin(Options.class)
public interface OptionsAccessor {
	@Mutable
	@Accessor("optionsFile")
	void worldfolders$setFile(File file);
}
