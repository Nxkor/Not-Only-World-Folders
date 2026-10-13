package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.WorldMenus;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.imnot.worldfolders.Ui.tr;

/** "Save world" under "Save and Quit to Title", in singleplayer: saves without leaving the world. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
	@Shadow private @Nullable Button disconnectButton;

	private PauseScreenMixin() {
		super(null);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void worldfolders$saveButton(CallbackInfo ci) {
		if (disconnectButton == null || minecraft.getSingleplayerServer() == null) return; // pause menu hidden (F3+Esc), or multiplayer
		addRenderableWidget(Button.builder(tr("save_world"), WorldMenus::quickSave)
				.bounds(disconnectButton.getX(), disconnectButton.getY() + 24, disconnectButton.getWidth(), 20).build());
	}
}
