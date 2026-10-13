package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Drag;
import com.imnot.worldfolders.WorldMenus;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A drag ends where the left button is released, and a right click undoes it, wherever the mouse is.
 * A right click on the screenshots or Singleplayer button of the title screen is caught here too: buttons only hear left clicks.
 * Hooked here, not in the lists' mouse methods: those can be replaced by another mod's (see
 * AbstractContainerWidgetMixin), and a release only reaches the list while the screen has it focused.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void worldfolders$drag(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
		if (button.button() == InputConstants.MOUSE_BUTTON_LEFT && action == InputConstants.RELEASE) Drag.release();
		else if (button.button() == InputConstants.MOUSE_BUTTON_RIGHT && action == InputConstants.PRESS && (Drag.undo() || WorldMenus.screenshotsMenu() || WorldMenus.soloMenu())) ci.cancel();
	}
}
