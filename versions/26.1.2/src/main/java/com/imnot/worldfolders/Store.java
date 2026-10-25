package com.imnot.worldfolders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imnot.worldfolders.mixin.MinecraftAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.Supplier;

/**
 * Everything Not Only World Folders remembers, in config/worldfolders.json. Nothing is written in the saves
 * folder or servers.dat that vanilla would care about, so removing the mod leaves plain worlds/servers.
 */
public final class Store {
	public static final Logger LOG = LoggerFactory.getLogger("worldfolders");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("worldfolders.json");

	public static final class Config {
		/** Saves folder to browse; "" = the instance's own saves folder. */
		public String savesDir = "";
		public boolean dragAndDrop = true;
		/** Icon of folders that have none of their own. */
		public String folderIcon = "minecraft:chest";
		/** Saves folders picked by hand, listed in the instance dropdown with the ones found by {@link Instances}. */
		public ArrayList<String> otherSavesDirs = new ArrayList<>();
		/** Screenshots folder the title screen button opens; "" = the instance's own. */
		public String screenshotsDir = "";
		/** Notifications (folder edited, world deleted or restored, preset saved): shown or not, and for how long. */
		public boolean notifications = true;
		public double notificationSeconds = 5;
		/** Resource packs: select a pack made for another game version without the confirmation screen. */
		public boolean skipPackWarning = true;
		/** options.txt the game reads and writes; "" = the instance's own. Another instance's is picked in the options screen. */
		public String optionsFile = "";
	}

	/** What we learn about a server while playing on it or pinging it. */
	public static final class ServerStats {
		public long playSeconds, lastPlayed;
		public int protocol;
	}

	private static final ServerStats NO_STATS = new ServerStats();

	private static final class Data {
		Config config = new Config();
		/** Up to 2.4: one organisation per saves folder (key = Config.savesDir). Now in the shared file; kept empty for older versions of the mod. */
		HashMap<String, Space> worlds = new HashMap<>();
		Space servers = new Space();
		/** Keyed by address: joining from "Direct connection" counts too. */
		HashMap<String, ServerStats> serverStats = new HashMap<>();
		/** World presets by name, in the order made. */
		LinkedHashMap<String, Presets.Preset> presets = new LinkedHashMap<>();
	}

	private static final Data DATA = load(FILE, Data.class, Data::new);
	/**
	 * What every game instance of this computer shares: an instance picked in the dropdown keeps its folders
	 * whichever instance the game was started from, and so do the resource packs.
	 */
	private static final Path SHARED_FILE = FabricLoader.getInstance().isDevelopmentEnvironment()
			? FILE.resolveSibling("worldfolders-shared.json") // the UI tests stay out of the real file
			: Path.of(System.getProperty("user.home"), ".notonlyworldfolders", "shared.json");

	private static final class Shared {
		/** One organisation per saves folder (key = its real path). */
		HashMap<String, Space> worlds = new HashMap<>();
		Space packs = new Space();
		/** Shader packs sent to the trash of the shaders screen (Iris), by their full path. */
		LinkedHashSet<String> shaderTrash = new LinkedHashSet<>();
	}

	private static Shared shared = loadShared();
	private static long sharedStamp = SHARED_FILE.toFile().lastModified();
	/** Config.savesDir -> key in Shared.worlds: the real path is asked from the disk once. */
	private static final HashMap<String, String> SAVES_KEYS = new HashMap<>();
	/** A server got a new id that servers.dat doesn't have yet. */
	public static boolean serverIdsDirty;

	static {
		migrate();
	}

	public static Config config() {
		return DATA.config;
	}

	public static Space worlds() {
		return shared.worlds.computeIfAbsent(SAVES_KEYS.computeIfAbsent(DATA.config.savesDir, Store::savesKey), k -> new Space());
	}

	/** The resource packs: one organisation for all of them, since every instance lists the packs of the others. */
	public static Space packs() {
		return shared.packs;
	}

	static String savesKey(String savesDir) {
		Path dir = savesDir.isEmpty() ? FabricLoader.getInstance().getGameDir().resolve("saves") : Path.of(savesDir);
		try {
			return dir.toRealPath().toString(); // one spelling per folder, whatever the launcher wrote
		} catch (IOException e) { // no such folder (yet)
			return dir.toAbsolutePath().normalize().toString();
		}
	}

	/** Up to 2.4 each instance kept the folders of the saves folders it browsed: they join the shared file, once. */
	private static void migrate() {
		if (DATA.worlds.isEmpty()) return;
		try {
			Files.copy(FILE, FILE.resolveSibling("worldfolders.json.pre-2.5"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
		}
		DATA.worlds.forEach((dir, space) -> shared.worlds.computeIfAbsent(savesKey(dir), k -> new Space()).absorb(space));
		DATA.worlds.clear();
		save();
	}

	/**
	 * Another game instance may have written the shared file since we read it: read it again. Called when the
	 * worlds or packs screen opens. ponytail: two games open at once still save over each other's changes made
	 * while both screens stay open; a merge on save if that ever matters.
	 */
	public static void sync() {
		if (SHARED_FILE.toFile().lastModified() == sharedStamp) return;
		shared = loadShared();
		sharedStamp = SHARED_FILE.toFile().lastModified();
	}

	private static Shared loadShared() {
		Shared s = load(SHARED_FILE, Shared.class, Shared::new);
		if (s.worlds == null) s.worlds = new HashMap<>();
		if (s.packs == null) s.packs = new Space();
		if (s.shaderTrash == null) s.shaderTrash = new LinkedHashSet<>();
		return s;
	}

	/** Shader packs in the trash (full paths): the shaders screen of every instance hides them. */
	public static LinkedHashSet<String> shaderTrash() {
		return shared.shaderTrash;
	}

	public static Space servers() {
		return DATA.servers;
	}

	public static LinkedHashMap<String, Presets.Preset> presets() {
		return DATA.presets;
	}

	public static ServerStats stats(String ip) {
		return DATA.serverStats.getOrDefault(ip, NO_STATS);
	}

	public static ServerStats statsForUpdate(String ip) {
		return DATA.serverStats.computeIfAbsent(ip, k -> new ServerStats());
	}

	private static int serverTicks;

	/** Every client tick spent on a server. ponytail: saved once a minute, a crash loses at most that minute. */
	public static void tickServer(String ip) {
		ServerStats s = statsForUpdate(ip);
		s.lastPlayed = System.currentTimeMillis();
		if (++serverTicks % 1200 == 0) {
			s.playSeconds += 60;
			save();
		}
	}

	/** Points Minecraft at the configured saves folder. Returns true if it changed. */
	public static boolean applySavesDir() {
		Minecraft mc = Minecraft.getInstance();
		Path wanted = DATA.config.savesDir.isEmpty() ? mc.gameDirectory.toPath().resolve("saves") : Path.of(DATA.config.savesDir);
		if (mc.getLevelSource().getBaseDir().toAbsolutePath().normalize().equals(wanted.toAbsolutePath().normalize())) return false;
		((MinecraftAccessor) mc).worldfolders$setLevelSource(LevelStorageSource.createDefault(wanted));
		return true;
	}

	/** Reads {@code file}: a missing one starts empty, an unreadable one is kept aside first. */
	private static <T> T load(Path file, Class<T> type, Supplier<T> empty) {
		if (!Files.exists(file)) return empty.get();
		try {
			T d = GSON.fromJson(Files.readString(file), type);
			if (d == null || d instanceof Data data && (data.config == null || data.worlds == null || data.servers == null)) throw new IOException("incomplete file");
			return d;
		} catch (Exception e) {
			// Keep the unreadable file rather than overwrite what was organised on the next save.
			LOG.error("Could not read {}, backing it up and starting empty", file, e);
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}
			return empty.get();
		}
	}

	public static void save() {
		write(FILE, DATA);
		write(SHARED_FILE, shared);
		sharedStamp = SHARED_FILE.toFile().lastModified();
	}

	private static void write(Path file, Object data) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(data));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOG.error("Could not save {}", file, e);
		}
	}
}
