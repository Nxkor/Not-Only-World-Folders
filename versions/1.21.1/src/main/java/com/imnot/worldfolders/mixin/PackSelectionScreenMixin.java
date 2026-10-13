package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.FolderRow;
import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.PackMenus;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.imnot.worldfolders.Ui.tr;

/**
 * The resource pack screen: its "Available" list gets the folders, favorites and trash of the worlds screen.
 * The data pack screen of a world (same class) is left alone.
 */
@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen implements Hooks.ListHolder, Hooks.PackScreen {
	@Shadow private TransferableSelectionList availablePackList;
	@Unique private boolean worldfolders$resourcePacks;
	@Unique private @Nullable Button worldfolders$newFolder, worldfolders$trash;

	@Shadow
	private void populateLists() {
		throw new AssertionError();
	}

	@Shadow
	private void reload() {
		throw new AssertionError();
	}

	private PackSelectionScreenMixin() {
		super(null);
	}

	/** Null for data packs: nothing of the mod runs on that screen. */
	@Override
	public @Nullable ObjectSelectionList<?> worldfolders$list() {
		return worldfolders$resourcePacks ? availablePackList : null;
	}

	@Override
	public @Nullable Button worldfolders$newFolderButton() {
		return worldfolders$newFolder;
	}

	@Override
	public @Nullable Button worldfolders$trashButton() {
		return worldfolders$trash;
	}

	@Override
	public void worldfolders$populate() {
		populateLists();
	}

	@Override
	public void worldfolders$reload() {
		reload();
	}

	/** A new screen never opens on the trash; another game may have organised the packs meanwhile. */
	@Inject(method = "<init>", at = @At("TAIL"))
	private void worldfolders$open(PackRepository repository, Consumer<PackRepository> output, Path packDir, Component title, CallbackInfo ci) {
		worldfolders$resourcePacks = repository == Minecraft.getInstance().getResourcePackRepository();
		if (!worldfolders$resourcePacks) return;
		Store.sync();
		Store.packs().trashView = false;
		Store.packs().selection.clear();
	}

	/** Vanilla filled both lists; re-order the available packs into the folder tree. */
	@Inject(method = "populateLists", at = @At("TAIL"))
	private void worldfolders$arrange(CallbackInfo ci) {
		if (!worldfolders$resourcePacks) return;
		TransferableSelectionList list = availablePackList;
		List<TransferableSelectionList.PackEntry> arranged = Store.packs().arrange(new ArrayList<>(list.children()), Hooks::keyOf,
				Hooks.rows((path, depth) -> new FolderRow.Pack(list, path, depth)), false);
		list.children().clear();
		list.children().addAll(arranged);
		if (list.getSelected() != null && !arranged.contains(list.getSelected())) list.setSelected(null); // e.g. sent to the trash
		list.setScrollAmount(list.getScrollAmount()); // fewer rows than before (trash view, folder closed): back inside the list
		worldfolders$updateButtons();
	}

	/** "New folder" and "Trash (n)" in the corners of the header, above the lists. */
	@Inject(method = "init", at = @At("TAIL"))
	private void worldfolders$buttons(CallbackInfo ci) {
		if (!worldfolders$resourcePacks) return;
		worldfolders$newFolder = addRenderableWidget(Button.builder(tr("new_folder"), b -> PackMenus.newFolder(this)).bounds(Math.max(2, width / 2 - 215), 1, 90, 15).build());
		// Toggling only refreshes the list, as on the worlds screen.
		worldfolders$trash = addRenderableWidget(Button.builder(CommonComponents.EMPTY, b -> {
			Space s = Store.packs();
			s.trashView = !s.trashView;
			populateLists();
		}).bounds(Math.min(width - 102, width / 2 + 115), 1, 100, 15).build());
		worldfolders$updateButtons();
	}

	@Override
	public void worldfolders$updateButtons() {
		if (worldfolders$newFolder == null || worldfolders$trash == null) return; // lists filled before init added them
		Space s = Store.packs();
		worldfolders$newFolder.visible = !s.trashView;
		worldfolders$trash.setMessage(s.trashView ? tr("leave_trash") : tr("trash", s.trash.size()));
		worldfolders$trash.setTooltip(Tooltip.create(tr("trash_tooltip_packs")));
	}
}
