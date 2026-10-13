package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.ContextMenu;
import com.imnot.worldfolders.Drag;
import com.imnot.worldfolders.Hooks;
import net.minecraft.client.gui.GuiGraphics;
import com.imnot.worldfolders.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Runs after the other mods (priority): what they add at the end of init is there when our widgets are put in front. */
@Mixin(value = Screen.class, priority = 1500)
public abstract class ScreenMixin implements Hooks.Overlay {
	@Shadow @Final private List<GuiEventListener> children;
	@Shadow @Final private List<Renderable> renderables;
	@Unique private @Nullable ContextMenu worldfolders$menu;

	@Override
	public void worldfolders$setMenu(@Nullable ContextMenu menu) {
		if (worldfolders$menu != null) {
			children.remove(worldfolders$menu);
			worldfolders$menu.closed();
		}
		worldfolders$menu = menu;
		if (menu != null) {
			children.add(0, menu); // first child gets clicks first
		}
	}

	@Override
	public @Nullable ContextMenu worldfolders$menu() {
		return worldfolders$menu;
	}

	/**
	 * The menu is drawn after the whole screen. Since 1.21.6 what is drawn later is above, and until 1.21.8
	 * the worlds screen draws its search box after its widgets: as a widget the menu was under that box.
	 */
	@Inject(method = "renderWithTooltip", at = @At("TAIL"))
	private void worldfolders$renderMenu(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (worldfolders$menu != null) worldfolders$menu.render(g, mouseX, mouseY, partialTick);
	}

	/** Escape closes the menu, or clears a multi-selection, instead of the screen. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void worldfolders$escapeMenu(int key, int scan, int mods, CallbackInfoReturnable<Boolean> cir) {
		if (worldfolders$menu != null && key == 256) {
			worldfolders$setMenu(null);
			cir.setReturnValue(true);
		} else if (key == 256 && this instanceof Hooks.ListHolder holder && holder.worldfolders$list() instanceof Hooks.OrganizedList list
				&& !list.worldfolders$space().selection.isEmpty()) {
			list.worldfolders$space().selection.clear();
			cir.setReturnValue(true);
		}
	}

	/**
	 * Shown again (back from a prompt, a confirmation...): re-arrange the world/server list. Since 1.21.9
	 * a screen isn't re-initialised on return, and leaving it closed the world rows' icons.
	 */
	@Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"))
	private void worldfolders$shown(CallbackInfo ci) {
		Ui.raise(children, renderables);
		if (this instanceof Hooks.ListHolder holder && holder.worldfolders$list() instanceof Hooks.OrganizedList list) list.worldfolders$refresh();
	}

	/**
	 * Notes the widgets our mixins add (their handlers keep "worldfolders$" in their name once merged into
	 * the screen), so {@link Ui#raise} can put them in front of the ones of other mods.
	 * ponytail: found by the caller's name rather than marked at each of the twenty places that add one.
	 */
	@Inject(method = "addRenderableWidget", at = @At("HEAD"))
	private void worldfolders$ours(GuiEventListener widget, CallbackInfoReturnable<GuiEventListener> cir) {
		if (StackWalker.getInstance().walk(frames -> frames.skip(1).limit(2).anyMatch(f -> f.getMethodName().contains("worldfolders$")))) Ui.ours(widget);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void worldfolders$tick(CallbackInfo ci) {
		if (this instanceof Hooks.ListHolder holder) holder.worldfolders$tick();
	}

	/** The resource pack screen has no render of its own to hook: the row dragged there is drawn from here. */
	@Inject(method = "render", at = @At("TAIL"))
	private void worldfolders$packDrag(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (this instanceof Hooks.PackScreen && this instanceof Hooks.ListHolder holder) Drag.render(g, mouseX, mouseY, holder);
	}

	@Inject(method = "clearWidgets", at = @At("HEAD"))
	private void worldfolders$forgetMenu(CallbackInfo ci) {
		worldfolders$menu = null;
		Drag.cancel(); // screen rebuilt: any drag in progress is gone
	}
}
