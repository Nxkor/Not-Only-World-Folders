package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Presets;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.imnot.worldfolders.Ui.tr;

/** World presets: the creation screen filled with one, or saving one instead of making a world. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
	@Shadow @Final WorldCreationUiState uiState;
	@Unique private boolean worldfolders$started, worldfolders$saving;
	@Unique private @Nullable String worldfolders$editing;

	@Shadow
	public abstract void popScreen();

	private CreateWorldScreenMixin() {
		super(null);
	}

	/** The first init only: later ones (a resize) must keep what was typed since. */
	@Inject(method = "init", at = @At("HEAD"))
	private void worldfolders$preset(CallbackInfo ci) {
		if (worldfolders$started) return;
		worldfolders$started = true;
		Presets.Request request = Presets.take();
		if (request == null) return;
		worldfolders$saving = request.saving();
		worldfolders$editing = request.editing();
		if (request.load() != null) Presets.apply(request.load(), uiState);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void worldfolders$saveLabel(CallbackInfo ci) {
		if (!worldfolders$saving) return;
		for (GuiEventListener child : children()) {
			if (child instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("selectWorld.create")) b.setMessage(tr("save_preset"));
		}
	}

	@Inject(method = "onCreate", at = @At("HEAD"), cancellable = true)
	private void worldfolders$savePreset(CallbackInfo ci) {
		if (!worldfolders$saving) return;
		ci.cancel();
		if (Presets.save(worldfolders$editing, uiState)) popScreen();
	}
}
