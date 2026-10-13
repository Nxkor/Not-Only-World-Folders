package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Calc;
import com.imnot.worldfolders.PromptScreen;
import com.imnot.worldfolders.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.imnot.worldfolders.Ui.tr;

/** Ctrl + click on the FOV slider asks for the exact value instead of moving the handle. */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
	@Shadow protected double value;

	@Shadow protected abstract void updateMessage();

	@Inject(method = "onClick", at = @At("HEAD"), cancellable = true)
	private void worldfolders$exactFov(double mouseX, double mouseY, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (!Ui.ctrl() || !Ui.isFov(((AbstractSliderButton) (Object) this).getMessage())) return;
		ci.cancel();
		mc.setScreen(new PromptScreen(tr("exact_fov"), String.valueOf(mc.options.fov().get()), typed -> {
			Integer fov = Calc.fov(typed);
			if (fov == null) {
				Ui.toast(tr("fov_invalid"), Component.literal(typed));
				return;
			}
			mc.options.fov().set(fov);
			mc.options.save();
			value = Calc.fovSlider(fov); // for the screens that are not rebuilt when the prompt closes
			updateMessage();
		}, mc.screen).hint(tr("fov_hint")));
	}
}
