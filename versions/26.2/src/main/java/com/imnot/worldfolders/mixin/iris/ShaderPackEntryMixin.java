package com.imnot.worldfolders.mixin.iris;

import com.imnot.worldfolders.Shaders;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A pack of another instance shows as "name (instance)", not as its full path. */
@Mixin(ShaderPackSelectionList.ShaderPackEntry.class)
public abstract class ShaderPackEntryMixin {
	@ModifyVariable(method = "extractContent", at = @At("STORE"), ordinal = 0)
	private String worldfolders$label(String name) {
		return Shaders.label(name);
	}
}
