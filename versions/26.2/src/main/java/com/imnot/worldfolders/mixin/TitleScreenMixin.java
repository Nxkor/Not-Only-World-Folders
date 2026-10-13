package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Presets;
import com.imnot.worldfolders.SideButton;
import com.imnot.worldfolders.WorldMenus;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * A right click on "Singleplayer" picks the instance whose worlds it shows. Beside it: on its left the screenshots folder (right click: another instance's), on its
 * right "+": create a world, or load a world preset.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	private TitleScreenMixin() {
		super(null);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void worldfolders$buttons(CallbackInfo ci) {
		for (GuiEventListener child : List.copyOf(children())) {
			// Found by its label, wherever another mod may have put it; no such button (demo, custom menu): nothing added.
			if (!(child instanceof Button solo && solo.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("menu.singleplayer"))) continue;
			WorldMenus.soloButton = solo;
			solo.setTooltip(Tooltip.create(WorldMenus.soloTooltip()));
			WorldMenus.screenshotsButton = addRenderableWidget(new SideButton(solo, -24, 20, Component.literal("📷"), b -> WorldMenus.openScreenshots()));
			WorldMenus.screenshotsButton.setTooltip(Tooltip.create(WorldMenus.screenshotsTooltip()));
			addRenderableWidget(new SideButton(solo, 4, 20, Component.literal("+"), b -> Presets.titleMenu(this, b)));
		}
	}
}
