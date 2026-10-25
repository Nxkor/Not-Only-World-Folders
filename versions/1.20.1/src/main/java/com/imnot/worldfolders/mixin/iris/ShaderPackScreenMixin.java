package com.imnot.worldfolders.mixin.iris;

import com.imnot.worldfolders.Shaders;
import com.imnot.worldfolders.Store;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.imnot.worldfolders.Ui.tr;

/** "Trash (n)" / "Leave trash" top right of the shaders screen, while it shows the pack list. */
@Mixin(ShaderPackScreen.class)
public abstract class ShaderPackScreenMixin extends Screen {
	@Shadow private ShaderPackSelectionList shaderPackList;
	@Shadow private boolean optionMenuOpen;
	@Shadow private boolean guiHidden;

	private ShaderPackScreenMixin() {
		super(null);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void worldfolders$open(CallbackInfo ci) {
		Shaders.trashView = false;
		Store.sync();
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void worldfolders$trashButton(CallbackInfo ci) {
		if (guiHidden || optionMenuOpen) return;
		Button trash = addRenderableWidget(Button.builder(label(), b -> {
			Shaders.trashView = !Shaders.trashView;
			shaderPackList.refresh();
			shaderPackList.setScrollAmount(shaderPackList.getScrollAmount()); // fewer rows: back inside the list
			b.setMessage(label());
		}).bounds(width - 104, 6, 100, 20).build());
		trash.setTooltip(Tooltip.create(tr("trash_tooltip_shaders")));
	}

	private static net.minecraft.network.chat.Component label() {
		return Shaders.trashView ? tr("leave_trash") : tr("trash", Store.shaderTrash().size());
	}
}
