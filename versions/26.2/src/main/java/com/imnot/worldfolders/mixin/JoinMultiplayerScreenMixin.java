package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.ServerMenus;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import com.imnot.worldfolders.Ui;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.CommonComponents;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
		worldfolders$newFolder = addRenderableWidget(Button.builder(tr("new_folder"), b -> ServerMenus.newFolder(self)).size(78, 20).build());
		worldfolders$sort = addRenderableWidget(Ui.sortButton(this, Store::servers, ((Hooks.OrganizedList) serverSelectionList)::worldfolders$refresh, 0, 0));
		// Toggling only refreshes the list: rebuildWidgets() would stack a second list in the screen's layout.
		worldfolders$trash = addRenderableWidget(Button.builder(CommonComponents.EMPTY, b -> {
			Space s = Store.servers();
			s.trashView = !s.trashView;
			((Hooks.OrganizedList) serverSelectionList).worldfolders$refresh();
		}).size(100, 20).build());
		worldfolders$emptyTrash = addRenderableWidget(Button.builder(tr("empty_trash"), b -> ServerMenus.emptyTrash(self)).size(78, 20).build());
		worldfolders$place();
		worldfolders$updateButtons();
		// "Delete" goes through the trash.
		((ButtonAccessor) deleteButton).worldfolders$setOnPress(b -> ServerMenus.deleteButton(self));
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

	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void worldfolders$reposition(CallbackInfo ci) {
		worldfolders$place();
	}

	@Unique
	private void worldfolders$place() {
		// ponytail: fixed spots beside the title, they overlap it below ~410px GUI width.
		if (worldfolders$newFolder != null) worldfolders$newFolder.setPosition(width / 2 - 204, 6);
		if (worldfolders$emptyTrash != null) worldfolders$emptyTrash.setPosition(width / 2 - 204, 6);
		if (worldfolders$sort != null) worldfolders$sort.setPosition(width / 2 - 124, 6);
		if (worldfolders$trash != null) worldfolders$trash.setPosition(width / 2 + 104, 6);
	}

	@Override
	public void worldfolders$renderSortArrows(GuiGraphicsExtractor g) {
		Ui.renderSortArrows(g, worldfolders$sort, Store.servers());
	}
}
