package com.imnot.worldfolders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Self-check of the folder logic, run by `gradlew check`. */
public class SpaceCheck {
	public static void main(String[] args) {
		Space s = new Space();
		s.folders.addAll(List.of("A", "A/B", "AB")); // "AB" is a prefix trap for "A"
		s.move("w1", "A/B");
		s.move("w2", "AB");

		s.renameFolder("A", "Z");
		check(s.folders.equals(new TreeSet<>(List.of("Z", "Z/B", "AB"))), "rename moves subfolders only: " + s.folders);
		check(s.folderOf("w1").equals("Z/B") && s.folderOf("w2").equals("AB"), "rename moves items");

		s.deleteFolder("Z");
		check(s.folders.equals(new TreeSet<>(List.of("B", "AB"))), "delete lifts subfolders: " + s.folders);
		check(s.folderOf("w1").equals("B"), "delete lifts items");
		check(s.tree().equals(List.of("AB", "B")), "tree order");

		// arrange: root shows folders then root items; expanded folder shows its content indented
		s.move("w3", "");
		s.favorites.add("w4");
		List<String> depths = new ArrayList<>();
		Space.Rows<String> rows = new Space.Rows<>() {
			public String folder(String path, int depth) { return "[" + path + "]" + depth; }
			public String up() { return ".."; }
			public void setDepth(String item, int depth) { depths.add(item + depth); }
		};
		List<String> all = List.of("w1", "w2", "w3", "w4", "lan");
		java.util.function.Function<String, String> key = e -> e.equals("lan") ? null : e;
		check(s.arrange(all, key, rows, false).equals(List.of("[AB]0", "[B]0", "w4", "w3", "lan")), "root view, favorite first");
		s.expanded.add("B");
		check(s.arrange(all, key, rows, false).equals(List.of("[AB]0", "[B]0", "w1", "w4", "w3", "lan")), "expanded folder");
		check(depths.contains("w11"), "expanded content is indented");
		s.current = "B";
		check(s.arrange(all, key, rows, false).equals(List.of("..", "w1", "lan")), "inside folder");

		check(!s.toTrash("w4"), "favorite can't be trashed");
		check(s.toTrash("w1"), "trash");
		s.trashView = true;
		check(s.arrange(all, key, rows, false).equals(List.of("w1")), "trash view");
		s.trashView = false;
		check(s.arrange(all, key, rows, true).equals(List.of("w4", "w2", "w3", "lan")), "search is flat, skips trash");
		s.forget("w1");
		check(!s.trash.contains("w1") && s.folderOf("w1").isEmpty(), "forget");
		// delete a folder with its content: content to trash, favorites move up, other folders untouched
		Space d = new Space();
		d.folders.addAll(List.of("X", "X/Y", "XY"));
		d.move("a", "X");
		d.move("b", "X/Y");
		d.move("fav", "X/Y");
		d.move("c", "XY");
		d.favorites.add("fav");
		d.current = "X/Y";
		d.deleteFolderWithContent("X");
		check(d.folders.equals(new TreeSet<>(List.of("XY"))), "folder and subfolders gone: " + d.folders);
		check(d.trash.containsAll(List.of("a", "b")) && !d.trash.contains("fav") && !d.trash.contains("c"), "content trashed: " + d.trash);
		check(d.folderOf("fav").isEmpty() && d.folderOf("c").equals("XY") && d.folderOf("a").isEmpty(), "favorite moved up, others kept");
		check(d.current.isEmpty(), "browsing a deleted folder goes up");

		// folder icons/descriptions follow renames; a deleted folder doesn't hand its own to the parent
		Space f = new Space();
		f.folders.addAll(List.of("P", "P/C"));
		f.folderIcons.put("P", "minecraft:diamond");
		f.folderIcons.put("P/C", "minecraft:stone");
		f.folderDescriptions.put("P/C", "hi");
		f.renameFolder("P", "Q");
		check(f.folderIcons.equals(Map.of("Q", "minecraft:diamond", "Q/C", "minecraft:stone")), "icons follow rename: " + f.folderIcons);
		f.deleteFolder("Q");
		check(f.folderIcons.equals(Map.of("C", "minecraft:stone")) && f.folderDescriptions.containsKey("C"), "icons after delete: " + f.folderIcons);
		f.deleteFolderWithContent("C");
		check(f.folderIcons.isEmpty() && f.folderDescriptions.isEmpty(), "delete with content drops icons");

		// sort and filter dropdown
		Space o = new Space();
		o.favorites.add("b");
		Map<String, Long> date = Map.of("a", 2L, "b", 1L, "c", 3L), version = Map.of("a", 5L, "b", 9L, "c", 1L);
		Space.Rows<String> dated = new Space.Rows<>() {
			public String folder(String path, int depth) { return path; }
			public String up() { return ".."; }
			public void setDepth(String item, int depth) {}
			public long date(String item) { return date.get(item); }
			public long version(String item) { return version.get(item); }
		};
		List<String> abc = List.of("a", "b", "c");
		String[][] expected = {{"b", "a", "c"}, {"b"}, {"a", "c"}, {"c", "a", "b"}, {"b", "a", "c"}, {"b", "a", "c"}, {"c", "a", "b"}};
		for (Space.Sort sort : Space.Sort.values()) {
			o.sort = sort;
			check(o.arrange(abc, e -> e, dated, false).equals(List.of(expected[sort.ordinal()])), "sort " + sort);
		}

		// v2.1 drag and drop between rows: manual order, favorites still first, move into the anchor's folder
		Space m = new Space();
		m.folders.add("F");
		m.move("x", "F");
		List<String> xyz = List.of("x", "y", "z");
		m.placeNear(List.of("z"), "y", false, List.of("y", "z"));
		check(m.arrange(xyz, e -> e, dated, false).equals(List.of("F", "z", "y")), "z dropped above y");
		m.placeNear(List.of("y"), "x", true, List.of("x", "z", "y"));
		check(m.folderOf("y").equals("F") && m.order.indexOf("y") == m.order.indexOf("x") + 1, "y dropped under x, into F");
		m.favorites.add("q");
		check(m.arrange(List.of("z", "n", "q"), e -> e, dated, false).equals(List.of("F", "q", "n", "z")), "favorite, then a new (unordered) item, first");
		m.forget("y");
		check(!m.order.contains("y"), "forget drops the order");

		// the trash view closes itself once empty (not while a search hides its content)
		m.toTrash("z");
		m.trashView = true;
		m.arrange(List.of("x"), e -> e, dated, true);
		check(m.trashView, "search keeps the trash view");
		m.arrange(List.of("x", "z"), e -> e, dated, false);
		check(m.trashView, "trash with content stays open");
		m.trash.remove("z");
		m.arrange(List.of("x", "z"), e -> e, dated, false);
		check(!m.trashView, "empty trash closes");

		// icon suggestions: namespaces first (minecraft first), then the items of the typed one
		List<String> ids = List.of("betterend:amber", "minecraft:stone", "minecraft:diamond", "ae2:cable");
		check(Space.suggest(ids, "", 10).equals(List.of("minecraft:", "ae2:", "betterend:")), "namespaces: " + Space.suggest(ids, "", 10));
		check(Space.suggest(ids, "b", 10).equals(List.of("betterend:")), "namespace prefix");
		check(Space.suggest(ids, "Minecraft:", 10).equals(List.of("minecraft:diamond", "minecraft:stone")), "items of a namespace");
		check(Space.suggest(ids, "minecraft:s", 10).equals(List.of("minecraft:stone")), "item prefix");

		// v2.2 multi-selection: Ctrl toggles, Shift selects a range from the last Ctrl-clicked row
		Space sel = new Space();
		List<String> rows5 = List.of("a", "b", "c", "d", "e");
		sel.select("b", false, rows5);
		sel.select("d", false, rows5);
		check(sel.selected(rows5).equals(List.of("b", "d")), "ctrl adds: " + sel.selection);
		sel.select("b", false, rows5);
		check(sel.selected(rows5).equals(List.of("d")), "ctrl again removes");
		sel.select("d", true, rows5); // range from b, the last Ctrl-clicked row
		check(sel.selected(rows5).equals(List.of("b", "c", "d")), "shift range: " + sel.selection);
		check(sel.selected(List.of("c", "b")).equals(List.of("c", "b", "d")), "hidden selected rows come last");
		// the selection dragged together between rows, in list order
		sel.placeNear(List.of("b", "d"), "e", true, rows5);
		check(sel.arrange(rows5, e -> e, dated, false).equals(List.of("a", "c", "e", "b", "d")), "group placed after e: " + sel.order);
		sel.forget("b");
		check(!sel.selection.contains("b"), "forget drops the selection");

		// recursive counter: direct items, then with the subfolders; the trash doesn't count
		Space cnt = new Space();
		cnt.folders.addAll(List.of("A", "A/B", "AB"));
		cnt.move("1", "A");
		cnt.move("2", "A/B");
		cnt.move("3", "A/B");
		cnt.move("4", "AB");
		cnt.move("5", "A");
		cnt.toTrash("5");
		check(java.util.Arrays.equals(cnt.count("A"), new long[]{1, 3}), "count A: " + java.util.Arrays.toString(cnt.count("A")));

		// instances: launcher layouts found on a fake home, a folder picked by hand, no duplicates
		try {
			java.nio.file.Path home = java.nio.file.Files.createTempDirectory("wf-home"), app = home.resolve("AppData");
			for (String dir : List.of("AppData/.minecraft/saves", "curseforge/minecraft/Instances/Pack A/saves",
					"AppData/PrismLauncher/instances/P1/minecraft/saves", "AppData/PrismLauncher/instances/NoSaves", "Other/My Pack/saves")) {
				java.nio.file.Files.createDirectories(home.resolve(dir));
			}
			List<String> labels = Instances.find(home, app, List.of(home.resolve("Other/My Pack/saves").toString(), home.resolve("AppData/.minecraft/saves").toString()))
					.stream().map(Instances.Instance::label).toList();
			check(labels.equals(List.of("Minecraft", "CurseForge : Pack A", "Prism : P1", "My Pack")), "instances: " + labels);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
		// a world made from a folder's "+" lands in that folder, at the next reading of the list only
		Space p = new Space();
		p.folders.add("A");
		check(!p.adopt(List.of("old")), "nothing expected");
		p.expect("A", List.of("old"));
		check(p.adopt(List.of("old", "new")) && p.folderOf("new").equals("A") && p.folderOf("old").isEmpty(), "new world adopted by the folder");
		check(!p.adopt(List.of("old", "new", "later")) && p.folderOf("later").isEmpty(), "adopted once");


		// v2.3: a folder dragged between two worlds stays there, one dropped near a row of another level moves to it
		Space fd = new Space();
		fd.folders.addAll(List.of("F", "G"));
		fd.move("c", "G");
		List<String> fabc = List.of("a", "b", "c");
		check(fd.arrange(fabc, e -> e, rows, false).equals(List.of("[F]0", "[G]0", "a", "b")), "folders first until dragged");
		fd.placeNear(List.of("/F"), "a", true, List.of("/F", "/G", "a", "b"));
		check(fd.arrange(fabc, e -> e, rows, false).equals(List.of("[G]0", "a", "[F]0", "b")), "folder between two worlds: " + fd.arrange(fabc, e -> e, rows, false));
		fd.expanded.add("G");
		List<String> moved = fd.placeNear(List.of("/F"), "c", false, List.of("/G", "c", "a", "/F", "b"));
		check(moved.equals(List.of("/G/F")) && fd.arrange(fabc, e -> e, rows, false).equals(List.of("[G]0", "[G/F]1", "c", "a", "b")), "folder dragged into another level: " + fd.arrange(fabc, e -> e, rows, false));
		check(!fd.moveFolder("G", "G/F") && fd.folders.contains("G"), "a folder can't go into itself");
		// v2.3: a world restored from the trash gets its folder back, even deleted since
		fd.deleteFolderWithContent("G");
		check(fd.trash.contains("c") && fd.folders.isEmpty(), "folder deleted with its content");
		fd.restore("c");
		check(!fd.trash.contains("c") && fd.folderOf("c").equals("G") && fd.folders.contains("G"), "restored into its old folder");

		// v2.4: the FOV typed by hand
		check(Calc.fov(" 93 ") == 93 && Calc.fov("29") == null && Calc.fov("111") == null && Calc.fov("9x") == null, "FOV typed");
		for (int v = 30; v <= 110; v++) {
			double at = Math.min(Calc.fovSlider(v), 0.99999F);
			check((int) Math.floor(30 + at * 81) == v && (int) Math.floor(30 + Calc.fovSlider(v) * 80) == v, "FOV slider position reads back as " + v); // both ways the option reads its slider
		}

		// v2.5: two instances organised the same saves folder, their files join
		Space mine = new Space(), theirs = new Space();
		mine.folders.add("A");
		mine.move("w1", "A");
		mine.favorites.add("w2");
		theirs.folders.add("B");
		theirs.move("w1", "B");
		theirs.move("w3", "B");
		theirs.trash.addAll(List.of("w2", "w4"));
		mine.absorb(theirs);
		check(mine.folderOf("w1").equals("A") && mine.folderOf("w3").equals("B"), "absorb keeps its own, takes the rest");
		check(mine.trash.equals(java.util.Set.of("w4")), "absorb never trashes a favorite: " + mine.trash);

		System.out.println("SpaceCheck OK");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
