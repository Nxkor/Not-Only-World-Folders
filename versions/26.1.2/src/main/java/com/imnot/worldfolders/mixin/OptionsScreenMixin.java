package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.InstanceOptions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Top left of the options screen: the instance whose options the game uses, a dropdown to pick another. */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
	@Shadow @Final private Screen lastScreen;

	private OptionsScreenMixin() {
		super(null);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void worldfolders$instanceButton(CallbackInfo ci) {
		Button b = addRenderableWidget(Button.builder(Component.literal("▼ " + font.plainSubstrByWidth(InstanceOptions.current(), 108)),
				button -> InstanceOptions.menu(this, button, lastScreen)).bounds(4, 4, 120, 18).build());
		b.setTooltip(Tooltip.create(InstanceOptions.tooltip()));
	}
}
