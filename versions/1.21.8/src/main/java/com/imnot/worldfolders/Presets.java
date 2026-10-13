package com.imnot.worldfolders;

import com.mojang.serialization.Dynamic;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.imnot.worldfolders.Ui.tr;

/**
 * World presets: what the world creation screen was set to, saved under the world name typed, to fill
 * that screen again later. A preset is made and edited in the creation screen itself, whose "Create"
 * button then saves the preset instead of making a world.
 * ponytail: the settings of the screen's three tabs only (name, seed, world type, game mode, difficulty,
 * commands, structures, bonus chest, game rules) and the superflat customisation. Data packs, experiments
 * and the customisation of the other world types (single biome...) are not kept; add them if presets need them.
 */
public final class Presets {
	public static final class Preset {
		public String seed = "", gameMode = "SURVIVAL", difficulty = "NORMAL", worldType = "", gameRules = "";
		/** The superflat customisation (layers, biome, structures), as SNBT; "" when the world is not a superflat one. */
		public String flat = "";
		public boolean commands, structures = true, bonusChest;
	}

	/** What the next world creation screen is opened for. */
	public record Request(@Nullable String load, boolean saving, @Nullable String editing, long time) {
	}

	private static @Nullable Request request;

	/** The request the creation screen being opened answers, once. An old one (its screen never came) is dropped. */
	public static @Nullable Request take() {
		Request r = request;
		request = null;
		return r != null && Util.getMillis() - r.time() < 120_000 ? r : null;
	}

	private static void open(Screen parent, @Nullable String load, boolean saving, @Nullable String editing) {
		request = new Request(load, saving, editing, Util.getMillis());
		WorldMenus.createWorld(parent);
	}

	/** The creation screen, to save a new preset. */
	public static void create(Screen parent) {
		open(parent, null, true, null);
	}

	public static void edit(Screen parent, String name) {
		open(parent, name, true, name);
	}

	/** The creation screen filled with the preset, to make a world. */
	public static void use(Screen parent, String name) {
		open(parent, name, false, null);
	}

	private static void delete(String name) {
		Store.presets().remove(name);
		Store.save();
		Ui.notify(tr("preset_deleted"), Component.literal(name));
	}

	/**
	 * The presets, then "+" to make one. {@code manage} (the options): each preset opens "Edit" / "Delete";
	 * else picking one opens the creation screen filled with it.
	 */
	public static ContextMenu list(Screen screen, boolean manage) {
		ContextMenu menu = ContextMenu.on(screen);
		for (String name : new ArrayList<>(Store.presets().keySet())) {
			if (manage) menu.sub(Component.literal(name), ContextMenu.on(screen).add(tr("edit_preset"), () -> edit(screen, name)).add(tr("delete_preset"), () -> delete(name)));
			else menu.add(Component.literal(name), () -> use(screen, name));
		}
		return menu.add(Component.literal("+ ").append(tr("new_preset")), () -> create(screen));
	}

	/** The preset dropdown under {@code button}. */
	public static void menu(Screen screen, Button button, boolean manage) {
		list(screen, manage).openAt(button.getX(), button.getY() + button.getHeight());
	}

	/** Title screen "+": a world, or a world from a preset. */
	public static void titleMenu(Screen screen, Button button) {
		ContextMenu.on(screen).add(tr("create_world"), () -> WorldMenus.createWorld(screen)).sub(tr("load_preset"), list(screen, false))
				.openAt(button.getX(), button.getY() + button.getHeight());
	}

	/** Fills the creation screen with preset {@code name}. What this game does not know (a removed mod's world type) is left as it is. */
	public static void apply(String name, WorldCreationUiState ui) {
		Preset p = Store.presets().get(name);
		if (p == null) return;
		ui.setName(name);
		ui.setSeed(p.seed);
		try {
			ui.setGameMode(WorldCreationUiState.SelectedGameMode.valueOf(p.gameMode));
			ui.setDifficulty(Difficulty.valueOf(p.difficulty));
		} catch (IllegalArgumentException ignored) {
		}
		ui.setAllowCommands(p.commands);
		ui.setGenerateStructures(p.structures);
		ui.setBonusChest(p.bonusChest);
		List<WorldCreationUiState.WorldTypeEntry> types = new ArrayList<>(ui.getNormalPresetList());
		types.addAll(ui.getAltPresetList());
		for (WorldCreationUiState.WorldTypeEntry type : types) if (typeId(type).equals(p.worldType)) ui.setWorldType(type);
		if (!p.flat.isEmpty()) { // after the world type: choosing it resets the customisation
			try {
				FlatLevelGeneratorSettings.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, ui.getSettings().worldgenLoadContext()), TagParser.parseCompoundFully(p.flat)).result()
						.ifPresent(flat -> ui.updateDimensions((registries, dimensions) -> dimensions.replaceOverworldGenerator(registries, new FlatLevelSource(flat))));
			} catch (Exception e) {
				Store.LOG.warn("Not Only World Folders: superflat settings of preset {} not readable", name, e);
			}
		}
		try {
			if (!p.gameRules.isEmpty()) ui.setGameRules(new GameRules(FeatureFlags.DEFAULT_FLAGS, new Dynamic<>(NbtOps.INSTANCE, TagParser.parseCompoundFully(p.gameRules))));
		} catch (Exception e) {
			Store.LOG.warn("Not Only World Folders: game rules of preset {} not readable", name, e);
		}
	}

	private static String typeId(WorldCreationUiState.WorldTypeEntry type) {
		return type.preset() == null ? "" : type.preset().unwrapKey().map(k -> k.location().toString()).orElse("");
	}

	/**
	 * Saves the creation screen as a preset named after its world name. {@code editing}: the preset being
	 * changed, null for a new one. Refused, with the reason on screen, without a name or under another preset's.
	 */
	public static boolean save(@Nullable String editing, WorldCreationUiState ui) {
		Map<String, Preset> presets = Store.presets();
		String name = ui.getName().trim();
		if (name.isEmpty() || !name.equals(editing) && presets.containsKey(name)) {
			Ui.toast(tr(name.isEmpty() ? "preset_name_empty" : "preset_name_taken"), Component.literal(name));
			return false;
		}
		Preset p = new Preset();
		p.seed = ui.getSeed();
		p.gameMode = ui.getGameMode().name();
		p.difficulty = ui.getDifficulty().name();
		p.commands = ui.isAllowCommands();
		p.structures = ui.isGenerateStructures();
		p.bonusChest = ui.isBonusChest();
		p.worldType = typeId(ui.getWorldType());
		p.gameRules = ui.getGameRules().createTag().toString();
		if (ui.getSettings().selectedDimensions().overworld() instanceof FlatLevelSource flat) {
			p.flat = FlatLevelGeneratorSettings.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, ui.getSettings().worldgenLoadContext()), flat.settings())
					.result().map(Object::toString).orElse("");
		}
		if (editing != null) presets.remove(editing); // renamed, maybe
		presets.put(name, p);
		Store.save();
		Ui.notify(tr(editing == null ? "preset_created" : "preset_edited"), Component.literal(name));
		return true;
	}
}
