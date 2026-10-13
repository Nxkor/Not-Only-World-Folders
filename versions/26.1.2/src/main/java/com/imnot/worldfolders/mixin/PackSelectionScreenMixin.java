package com.imnot.worldfolders.mixin;

import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.PackMenus;
import com.imnot.worldfolders.Space;
import com.imnot.worldfolders.Store;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
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
import java.util.function.Consumer;

import static com.imnot.worldfolders.Ui.tr;

/**
 * The resource pack screen: its "Available" list gets the folders, favorites and trash of the worlds screen
 * (arranged in TransferableSelectionListMixin). The data pack screen of a world (same class) is left alone.
 */
@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen implements Hooks.ListHolder, Hooks.PackScreen {
	@Shadow private @Nullable TransferableSelectionList availablePackList;
	@Shadow private @Nullable EditBox search;
	@Unique private boolean worldfolders$resourcePacks;
	@Unique private @Nullable Button worldfolders$newFolder, worldfolders$trash;

	@Shadow
	private void populateLists(PackSelectionModel.EntryBase focus) {
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
		populateLists(null);
	}

	@Override
	public void worldfolders$reload() {
		reload();
	}

	/** A search makes the list flat, like the worlds'. */
	@Override
	public boolean worldfolders$searching() {
		return search != null && !search.getValue().isBlank();
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

	/** "New folder" and "Trash (n)" in the corners of the header, above the lists. */
	@Inject(method = "init", at = @At("TAIL"))
	private void worldfolders$buttons(CallbackInfo ci) {
		if (!worldfolders$resourcePacks) return;
		worldfolders$newFolder = addRenderableWidget(Button.builder(tr("new_folder"), b -> PackMenus.newFolder(this)).size(90, 15).build());
		// Toggling only refreshes the list, as on the worlds screen.
		worldfolders$trash = addRenderableWidget(Button.builder(CommonComponents.EMPTY, b -> {
			Space s = Store.packs();
			s.trashView = !s.trashView;
			populateLists(null);
		}).size(100, 15).build());
		worldfolders$place();
		worldfolders$updateButtons();
	}

	/** A resize doesn't run init again. */
	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void worldfolders$reposition(CallbackInfo ci) {
		worldfolders$place();
	}

	@Unique
	private void worldfolders$place() {
		if (worldfolders$newFolder != null) worldfolders$newFolder.setPosition(Math.max(2, width / 2 - 215), 1);
		if (worldfolders$trash != null) worldfolders$trash.setPosition(Math.min(width - 102, width / 2 + 115), 1);
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
