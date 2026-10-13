package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Drag;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.Presets;
import com.imnot.worldfolders.SideButton;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import com.imnot.worldfolders.Ui;
import com.imnot.worldfolders.WorldMenus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

import static com.imnot.worldfolders.Ui.tr;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen implements Hooks.ListHolder {
	@Shadow private WorldSelectionList list;
	@Shadow @Final protected Screen lastScreen;
	@Unique private @Nullable Button worldfolders$newFolder, worldfolders$sort, worldfolders$trash, worldfolders$emptyTrash, worldfolders$instance, worldfolders$openSaves;
	@Unique private long worldfolders$fingerprint;
	@Unique private int worldfolders$ticks;

	private SelectWorldScreenMixin() {
		super(null);
	}

	@Override
	public ObjectSelectionList<?> worldfolders$list() {
		return list;
	}

	@Override
	public @Nullable Button worldfolders$newFolderButton() {
		return worldfolders$newFolder;
	}

	@Override
	public @Nullable Button worldfolders$trashButton() {
		return worldfolders$trash;
	}

	/** A new screen = coming from the title screen: never open on the trash. */
	@Inject(method = "<init>", at = @At("TAIL"))
	private void worldfolders$leaveTrash(Screen lastScreen, CallbackInfo ci) {
		Store.worlds().trashView = false;
		Store.worlds().selection.clear();
	}

	/** Switch to the saves folder chosen in the config; a new folder means a fresh world list. */
	@Inject(method = "init", at = @At("HEAD"))
	private void worldfolders$savesDir(CallbackInfo ci) {
		if (Store.applySavesDir()) list = null;
	}

	/** "New folder" left of the search box, "Trash (n)" / "Leave trash" right of it. */
	@Inject(method = "init", at = @At("TAIL"))
	private void worldfolders$buttons(CallbackInfo ci) {
		// ponytail: fixed spots beside the 200px search box, they overlap it below ~410px GUI width.
		worldfolders$newFolder = addRenderableWidget(Button.builder(tr("new_folder"), b -> WorldMenus.newFolder(this)).bounds(width / 2 - 204, 22, 78, 20).build());
		worldfolders$sort = addRenderableWidget(Ui.sortButton(this, Store::worlds, ((Hooks.OrganizedList) list)::worldfolders$refresh, width / 2 - 124, 22));
		// Toggling only refreshes the list: rebuilding the screen would stack a second list on some versions.
		worldfolders$trash = addRenderableWidget(Button.builder(CommonComponents.EMPTY, b -> {
			Space s = Store.worlds();
			s.trashView = !s.trashView;
			((Hooks.OrganizedList) list).worldfolders$refresh();
		}).bounds(width / 2 + 104, 22, 100, 20).build());
		// In the trash view "Empty trash" takes the place of "New folder".
		worldfolders$emptyTrash = addRenderableWidget(Button.builder(tr("empty_trash"), b -> WorldMenus.emptyTrash(this)).bounds(width / 2 - 204, 22, 78, 20).build());
		// The instance dropdown, then the saves folder button on its left: left of "New folder" when the window is
		// wide enough for them, else above it, beside the title.
		int room = width / 2 - 208 - 2 - 24; // what is left for the dropdown, once the folder button has its 20 + 4
		boolean beside = room >= 70;
		int w = Math.min(130, room), x = beside ? width / 2 - 208 - w : width / 2 - 204;
		worldfolders$instance = addRenderableWidget(Button.builder(CommonComponents.EMPTY, b -> WorldMenus.instanceMenu(this, b, lastScreen))
				.bounds(x, beside ? 22 : 1, beside ? w : 130, beside ? 20 : 18).build());
		worldfolders$openSaves = addRenderableWidget(Button.builder(Component.literal("📂"), b -> WorldMenus.openSaves())
				.bounds(beside ? x - 24 : width / 2 - 72, beside ? 22 : 1, beside ? 20 : 18, beside ? 20 : 18).build());
		// World presets, right of "Create New World": make one, or fill the creation screen with one.
		for (GuiEventListener child : List.copyOf(children())) {
			if (!(child instanceof Button create && create.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("selectWorld.create"))) continue;
			addRenderableWidget(new SideButton(create, 4, 22, Component.literal("P+"), b -> Presets.create(this))).setTooltip(Tooltip.create(tr("new_preset")));
			addRenderableWidget(new SideButton(create, 28, 22, Component.literal("P▼"), b -> Presets.menu(this, b, false))).setTooltip(Tooltip.create(tr("load_preset")));
		}
		worldfolders$updateButtons();
	}

	@Override
	public void worldfolders$updateButtons() {
		if (worldfolders$newFolder == null || worldfolders$trash == null) return; // list filled before init added them
		Space s = Store.worlds();
		worldfolders$newFolder.visible = !s.trashView;
		if (worldfolders$emptyTrash != null) worldfolders$emptyTrash.visible = s.trashView;
		worldfolders$trash.setMessage(s.trashView ? tr("leave_trash") : tr("trash", s.trash.size()));
		worldfolders$trash.setTooltip(Tooltip.create(tr("trash_tooltip_worlds")));
		// The instance shown may have changed under us: an emptied one is left for this game's own.
		if (worldfolders$instance != null) worldfolders$instance.setMessage(Component.literal("▼ " + font.plainSubstrByWidth(WorldMenus.currentInstance(WorldMenus.instances()), worldfolders$instance.getWidth() - 12)));
		if (worldfolders$openSaves != null) worldfolders$openSaves.setTooltip(Tooltip.create(tr("open_saves", minecraft.getLevelSource().getBaseDir().toAbsolutePath().normalize().toString())));
	}

	/**
	 * Reloads the list by itself when worlds appear, vanish or get saved in the folder (another launcher,
	 * a copy in the file explorer...), checked every 2 s. Not during a drag, the rows would jump.
	 */
	@Override
	public void worldfolders$tick() {
		if (++worldfolders$ticks % 40 != 0 || Drag.active() || list == null) return;
		long now = WorldMenus.savesFingerprint();
		if (worldfolders$fingerprint != 0 && now != worldfolders$fingerprint) {
			WorldMenus.forgetSizes();
			((Hooks.OrganizedList) list).worldfolders$reload();
		}
		worldfolders$fingerprint = now;
	}

	/** Else a change the mod made itself (a rename, game rules) reloads the list up to 2 s later, under the mouse. */
	@Override
	public void worldfolders$upToDate() {
		worldfolders$fingerprint = WorldMenus.savesFingerprint();
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void worldfolders$drag(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Ui.renderSortArrows(g, worldfolders$sort, Store.worlds());
		Drag.render(g, mouseX, mouseY, this);
	}
}
