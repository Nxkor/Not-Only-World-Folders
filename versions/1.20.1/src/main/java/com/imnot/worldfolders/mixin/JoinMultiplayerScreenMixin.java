package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Drag;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.ServerMenus;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import com.imnot.worldfolders.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.CommonComponents;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.imnot.worldfolders.Ui.tr;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen implements Hooks.ListHolder {
	@Shadow protected ServerSelectionList serverSelectionList;
	@Shadow private ServerList servers;
	@Shadow private Button deleteButton;
	@Unique private @Nullable Button worldfolders$newFolder, worldfolders$sort, worldfolders$trash, worldfolders$emptyTrash;

	@Shadow
	protected abstract void onSelectedChange();

	private JoinMultiplayerScreenMixin() {
		super(null);
	}

	@Override
	public ObjectSelectionList<?> worldfolders$list() {
		return serverSelectionList;
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
		Store.servers().trashView = false;
		Store.servers().selection.clear();
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void worldfolders$buttons(CallbackInfo ci) {
		JoinMultiplayerScreen self = (JoinMultiplayerScreen) (Object) this;
		if (Store.serverIdsDirty) { // give every server its Not Only World Folders id in servers.dat
			Store.serverIdsDirty = false;
			servers.save();
		}
		// ponytail: fixed spots beside the title, they overlap it below ~410px GUI width.
		worldfolders$newFolder = addRenderableWidget(Button.builder(tr("new_folder"), b -> ServerMenus.newFolder(self)).bounds(width / 2 - 204, 6, 78, 20).build());
		worldfolders$sort = addRenderableWidget(Ui.sortButton(this, Store::servers, ((Hooks.OrganizedList) serverSelectionList)::worldfolders$refresh, width / 2 - 124, 6));
		// Toggling only refreshes the list: rebuilding the screen would stack a second list on some versions.
		worldfolders$trash = addRenderableWidget(Button.builder(CommonComponents.EMPTY, b -> {
			Space s = Store.servers();
			s.trashView = !s.trashView;
			((Hooks.OrganizedList) serverSelectionList).worldfolders$refresh();
		}).bounds(width / 2 + 104, 6, 100, 20).build());
		worldfolders$emptyTrash = addRenderableWidget(Button.builder(tr("empty_trash"), b -> ServerMenus.emptyTrash(self)).bounds(width / 2 - 204, 6, 78, 20).build());
		worldfolders$updateButtons();

		// "Delete" goes through the trash (same place, same label).
		Button old = deleteButton;
		removeWidget(old);
		deleteButton = addRenderableWidget(Button.builder(old.getMessage(), b -> ServerMenus.deleteButton(self))
				.bounds(old.getX(), old.getY(), old.getWidth(), old.getHeight()).build());
		onSelectedChange();
	}

	@Override
	public void worldfolders$updateButtons() {
		if (worldfolders$newFolder == null || worldfolders$trash == null) return; // list filled before init added them
		Space s = Store.servers();
		worldfolders$newFolder.visible = !s.trashView;
		if (worldfolders$emptyTrash != null) worldfolders$emptyTrash.visible = s.trashView;
		worldfolders$trash.setMessage(s.trashView ? tr("leave_trash") : tr("trash", s.trash.size()));
		worldfolders$trash.setTooltip(Tooltip.create(tr("trash_tooltip_servers")));
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void worldfolders$drag(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Ui.renderSortArrows(g, worldfolders$sort, Store.servers());
		Drag.render(g, mouseX, mouseY, this);
	}
}
