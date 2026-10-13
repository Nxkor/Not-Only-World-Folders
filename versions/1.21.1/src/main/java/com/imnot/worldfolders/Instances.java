package com.imnot.worldfolders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The saves folders of the other game instances on this computer, for the instance dropdown of the
 * worlds screen. Pure Java, so SpaceCheck can run it on a fake home folder.
 */
public final class Instances {
	public record Instance(String label, Path saves) {
	}

	/**
	 * Where launchers keep their instances: label, then a path where "*" is one instance's folder.
	 * ponytail: the usual default locations only; anything else goes through "Other folder...".
	 */
	private static final String[][] LAYOUTS = {
			{"Minecraft", "%APPDATA%/.minecraft/saves"},
			{"Minecraft", "~/.minecraft/saves"},
			{"Minecraft", "~/Library/Application Support/minecraft/saves"},
			{"CurseForge", "~/curseforge/minecraft/Instances/*/saves"},
			{"Modrinth", "%APPDATA%/ModrinthApp/profiles/*/saves"},
			{"Modrinth", "%APPDATA%/com.modrinth.theseus/profiles/*/saves"},
			{"Modrinth", "~/.local/share/ModrinthApp/profiles/*/saves"},
			{"Prism", "%APPDATA%/PrismLauncher/instances/*/.minecraft/saves"},
			{"Prism", "%APPDATA%/PrismLauncher/instances/*/minecraft/saves"},
			{"Prism", "~/.local/share/PrismLauncher/instances/*/.minecraft/saves"},
			{"Prism", "~/.local/share/PrismLauncher/instances/*/minecraft/saves"},
			{"MultiMC", "%APPDATA%/MultiMC/instances/*/.minecraft/saves"},
			{"ATLauncher", "%APPDATA%/ATLauncher/instances/*/saves"},
			{"GDLauncher", "%APPDATA%/gdlauncher_carbon/data/instances/*/instance/saves"},
	};

	/** Every existing saves folder found for {@code home} / {@code appData} (null = none), plus {@code extra} ones. */
	public static List<Instance> find(Path home, Path appData, List<String> extra) {
		Map<Path, Instance> found = new LinkedHashMap<>(); // one entry per folder, first label wins
		for (String[] layout : LAYOUTS) {
			String pattern = layout[1];
			Path base;
			if (pattern.startsWith("%APPDATA%/")) {
				if (appData == null) continue;
				base = appData;
				pattern = pattern.substring(10);
			} else {
				base = home;
				pattern = pattern.substring(2);
			}
			int star = pattern.indexOf('*');
			if (star < 0) {
				add(found, layout[0], base.resolve(pattern));
				continue;
			}
			Path root = base.resolve(pattern.substring(0, star));
			String rest = pattern.substring(star + 2);
			try (Stream<Path> dirs = Files.list(root)) {
				dirs.filter(Files::isDirectory).sorted().forEach(d -> add(found, layout[0] + " : " + d.getFileName(), d.resolve(rest)));
			} catch (IOException ignored) { // this launcher isn't installed
			}
		}
		for (String dir : extra) {
			Path p = Path.of(dir); // a picked ".../MyPack/saves" shows as "MyPack"
			add(found, p.getParent() != null && p.getParent().getFileName() != null ? p.getParent().getFileName().toString() : dir, p);
		}
		return new ArrayList<>(found.values());
	}

	private static void add(Map<Path, Instance> found, String label, Path saves) {
		if (Files.isDirectory(saves)) found.putIfAbsent(saves.toAbsolutePath().normalize(), new Instance(label, saves));
	}

	/** Whether a saves folder holds a world: a folder with a level.dat in it. */
	public static boolean hasWorlds(Path saves) {
		try (Stream<Path> dirs = Files.list(saves)) {
			return dirs.anyMatch(d -> Files.isRegularFile(d.resolve("level.dat")));
		} catch (IOException e) { // no such folder
			return false;
		}
	}
}
