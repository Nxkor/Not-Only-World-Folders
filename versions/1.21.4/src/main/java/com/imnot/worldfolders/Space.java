package com.imnot.worldfolders;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

/**
 * Virtual organisation of one list (the worlds of a saves folder, or the servers).
 * Pure Java, no Minecraft classes, so it can be checked on its own (see SpaceCheck).
 * Keys are world directory names, or the id Not Only World Folders gives each server.
 */
public class Space {
	/** Folder paths, "/"-separated, e.g. "Survie/Hardcore". */
	public TreeSet<String> folders = new TreeSet<>();
	/** key -> folder path ("" / missing = root). */
	public HashMap<String, String> items = new HashMap<>();
	public HashSet<String> trash = new HashSet<>();
	public HashSet<String> favorites = new HashSet<>();
	public HashMap<String, String> descriptions = new HashMap<>();
	public HashSet<String> expanded = new HashSet<>();
	/** Folder path -> item id ("minecraft:diamond", "somemod:..."), and folder path -> description. */
	public HashMap<String, String> folderIcons = new HashMap<>();
	public HashMap<String, String> folderDescriptions = new HashMap<>();
	public Sort sort = Sort.DEFAULT;
	/** Order set by drag and drop. Keys missing from it (new worlds) come first, in vanilla's order. */
	public ArrayList<String> order = new ArrayList<>();
	/**
	 * A world is being made from a folder's "+": the folder, and the keys that existed then. Saved,
	 * since the game may be closed from inside the new world, before the list is shown again.
	 */
	public String pendingFolder;
	public HashSet<String> pendingKnown;

	/** The sort/filter dropdown. DEFAULT = manual order with favorites first. */
	public enum Sort {
		DEFAULT, FAVORITES, OTHERS, NEWEST, OLDEST, VERSION_NEWEST, VERSION_OLDEST;

		/** Sorts that keep the manual order, so dropping a row between two others means something. */
		public boolean manual() {
			return this == DEFAULT || this == FAVORITES || this == OTHERS;
		}
	}

	/** Folder being browsed ("" = root) and whether the trash is shown. Not saved. */
	public transient String current = "";
	public transient boolean trashView;
	/** Ctrl / Shift + click multi-selection, and the row a Shift range starts from. Not saved. */
	public transient LinkedHashSet<String> selection = new LinkedHashSet<>();
	private transient @Nullable String selectionAnchor;

	/** How a list builds its rows; lets the same tree logic serve worlds and servers. */
	public interface Rows<E> {
		E folder(String path, int depth);

		E up();

		void setDepth(E item, int depth);

		/** Last played time and game version of an item, for sorting. */
		default long date(E item) {
			return 0;
		}

		default long version(E item) {
			return 0;
		}
	}

	// --- paths ---

	public static String parent(String path) {
		int i = path.lastIndexOf('/');
		return i < 0 ? "" : path.substring(0, i);
	}

	public static String name(String path) {
		return path.substring(path.lastIndexOf('/') + 1);
	}

	public static String child(String parent, String name) {
		return parent.isEmpty() ? name : parent + "/" + name;
	}

	/** Folder names can't contain our "/" separator and must not be blank. */
	public static String clean(String name) {
		return name.replace('/', '-').trim();
	}

	static String remap(String path, String from, String to) {
		if (path.equals(from)) return to;
		if (!path.startsWith(from + "/")) return path;
		String rest = path.substring(from.length() + 1);
		return to.isEmpty() ? rest : to + "/" + rest;
	}

	/** A folder in {@link #order} and in a drag: "/" + its path. No world folder name or server id starts with "/". */
	public static String folderKey(String path) {
		return "/" + path;
	}

	public static boolean isFolderKey(String key) {
		return key.startsWith("/");
	}

	// --- queries ---

	public String folderOf(String key) {
		String f = items.getOrDefault(key, "");
		return folders.contains(f) ? f : "";
	}

	public List<String> children(String parent) {
		return folders.stream().filter(f -> parent(f).equals(parent)).toList();
	}

	/** Every folder in tree order (parents right before their children). */
	public List<String> tree() {
		List<String> out = new ArrayList<>();
		walkFolders("", out);
		return out;
	}

	private void walkFolders(String at, List<String> out) {
		for (String f : children(at)) {
			out.add(f);
			walkFolders(f, out);
		}
	}

	/**
	 * Orders the list: trash view = trashed items only; search = every non-trashed match, flat;
	 * otherwise ".." then the folders of {@link #current} (expanded ones with their content, indented)
	 * then its items. Favorites come first in each group, then the manual order. Items without a key
	 * (LAN servers...) stay last. A trash view left empty (last item restored or deleted) closes itself.
	 */
	public <E> List<E> arrange(List<E> entries, Function<E, String> key, Rows<E> rows, boolean searching) {
		if (!current.isEmpty() && !folders.contains(current)) current = "";
		if (trashView && !searching && entries.stream().noneMatch(e -> trash.contains(key.apply(e)))) trashView = false;
		Map<String, List<E>> byFolder = new HashMap<>();
		List<E> out = new ArrayList<>();
		List<E> unkeyed = new ArrayList<>();
		List<E> sorted = new ArrayList<>(entries);
		Map<String, Integer> rank = new HashMap<>();
		for (int i = 0; i < order.size(); i++) rank.put(order.get(i), i);
		sorted.sort(switch (sort) { // stable: equal items keep vanilla's order
			case NEWEST -> Comparator.comparingLong(rows::date).reversed();
			case OLDEST -> Comparator.comparingLong(rows::date);
			case VERSION_NEWEST -> Comparator.comparingLong(rows::version).reversed();
			case VERSION_OLDEST -> Comparator.comparingLong(rows::version);
			default -> Comparator.<E, Boolean>comparing(e -> !favorites.contains(key.apply(e)))
					.thenComparingInt(e -> rank.getOrDefault(key.apply(e), -1));
		});
		for (E e : sorted) {
			String k = key.apply(e);
			if (k == null) {
				unkeyed.add(e);
				continue;
			}
			if (trash.contains(k) != trashView) continue;
			if (sort == Sort.FAVORITES && !favorites.contains(k) || sort == Sort.OTHERS && favorites.contains(k)) continue;
			rows.setDepth(e, 0);
			if (trashView || searching) out.add(e);
			else byFolder.computeIfAbsent(folderOf(k), f -> new ArrayList<>()).add(e);
		}
		if (!trashView && !searching) {
			if (!current.isEmpty()) out.add(rows.up());
			walk(current, 0, byFolder, rows, out, e -> rank.getOrDefault(key.apply(e), -1), rank);
		}
		if (!trashView) out.addAll(unkeyed);
		return out;
	}

	/**
	 * One level of the tree: its folders, then its items. In a manual sort a folder dragged among the
	 * items comes right after the item ranked just before it in {@link #order}; the folders with no such
	 * item (dragged to the top, or new since the last drag) come first.
	 */
	private <E> void walk(String folder, int depth, Map<String, List<E>> byFolder, Rows<E> rows, List<E> out, ToIntFunction<E> rankOf, Map<String, Integer> rank) {
		List<E> here = byFolder.getOrDefault(folder, List.of());
		List<String> subs = new ArrayList<>(children(folder));
		ToIntFunction<String> folderRank = f -> sort.manual() ? rank.getOrDefault(folderKey(f), Integer.MAX_VALUE) : Integer.MAX_VALUE;
		subs.sort(Comparator.comparingInt(folderRank)); // stable: the unranked ones (new since the last drag) stay alphabetical, after the others
		Map<E, List<String>> after = new HashMap<>();
		for (String f : subs) {
			E before = null; // the item this folder follows: a world new since the last drag has no rank, so never one of those
			int r = folderRank.applyAsInt(f);
			for (E e : here) {
				int er = rankOf.applyAsInt(e);
				if (r != Integer.MAX_VALUE && er >= 0 && er < r && (before == null || er > rankOf.applyAsInt(before))) before = e;
			}
			after.computeIfAbsent(before, k -> new ArrayList<>()).add(f);
		}
		List<E> sequence = new ArrayList<>();
		sequence.add(null); // the top
		sequence.addAll(here);
		for (E e : sequence) {
			if (e != null) {
				rows.setDepth(e, depth);
				out.add(e);
			}
			for (String f : after.getOrDefault(e, List.of())) {
				out.add(rows.folder(f, depth));
				if (expanded.contains(f)) walk(f, depth + 1, byFolder, rows, out, rankOf, rank);
			}
		}
	}

	// --- mutations (callers save) ---

	/** Renames/moves folder {@code from} to {@code to} ("" = dissolve into root), with subfolders and items. */
	public void movePrefix(String from, String to) {
		TreeSet<String> moved = new TreeSet<>();
		for (String f : folders) {
			String m = remap(f, from, to);
			if (!m.isEmpty()) moved.add(m);
		}
		folders = moved;
		items.replaceAll((k, f) -> remap(f, from, to));
		HashSet<String> exp = new HashSet<>();
		for (String f : expanded) exp.add(remap(f, from, to));
		expanded = exp;
		folderIcons = remapKeys(folderIcons, from, to);
		folderDescriptions = remapKeys(folderDescriptions, from, to);
		LinkedHashSet<String> ranked = new LinkedHashSet<>(); // a set: two folders may have merged
		for (String k : order) ranked.add(isFolderKey(k) ? folderKey(remap(k.substring(1), from, to)) : k);
		ranked.remove(folderKey("")); // the folder dissolved into the root
		order = new ArrayList<>(ranked);
		current = remap(current, from, to);
	}

	private static HashMap<String, String> remapKeys(Map<String, String> map, String from, String to) {
		HashMap<String, String> out = new HashMap<>();
		map.forEach((f, v) -> {
			String m = remap(f, from, to);
			if (!m.isEmpty()) out.put(m, v);
		});
		return out;
	}

	private static boolean inside(String f, String folder) {
		return f.equals(folder) || f.startsWith(folder + "/");
	}

	/** Creates {@code parent/name} and returns its path, or null if the name is blank. */
	public String createFolder(String parent, String name) {
		name = clean(name);
		if (name.isEmpty()) return null;
		String path = child(parent, name);
		folders.add(path);
		return path;
	}

	public void renameFolder(String path, String newName) {
		newName = clean(newName);
		if (!newName.isEmpty()) movePrefix(path, child(parent(path), newName));
	}

	/** Deletes the folder only: its items and subfolders move up one level. */
	public void deleteFolder(String path) {
		order.remove(folderKey(path));
		folderIcons.remove(path); // the folder itself is gone: its icon must not pass to the parent
		folderDescriptions.remove(path);
		movePrefix(path, parent(path));
	}

	/**
	 * Deletes the folder, its subfolders and their content: items go to the trash (restoring one makes its folder again),
	 * except favorites, which can't be trashed and move up one level instead.
	 */
	public void deleteFolderWithContent(String path) {
		String up = parent(path);
		for (var e : items.entrySet()) {
			String f = e.getValue();
			if (!inside(f, path)) continue;
			if (favorites.contains(e.getKey())) e.setValue(up);
			else trash.add(e.getKey());
		}
		order.removeIf(k -> isFolderKey(k) && inside(k.substring(1), path));
		folders.removeIf(f -> inside(f, path));
		expanded.removeIf(f -> inside(f, path));
		folderIcons.keySet().removeIf(f -> inside(f, path));
		folderDescriptions.keySet().removeIf(f -> inside(f, path));
		if (inside(current, path)) current = up;
	}

	public void move(String key, String folder) {
		if (folder.isEmpty()) items.remove(key);
		else items.put(key, folder);
	}

	/**
	 * Drag and drop between rows: {@code keys} (items, or a folder as its {@link #folderKey}) join {@code anchor}'s
	 * level, together, right before or after it. {@code shown} (the rows as listed now, folders included) becomes
	 * the manual order. Returns the keys as they are now: a folder moved to another level has a new path.
	 * ponytail: O(n²), fine for a world list.
	 */
	public List<String> placeNear(List<String> keys, String anchor, boolean after, List<String> shown) {
		String level = isFolderKey(anchor) ? parent(anchor.substring(1)) : folderOf(anchor);
		List<String> seq = new ArrayList<>(shown), moved = new ArrayList<>();
		for (String k : keys) {
			if (!isFolderKey(k)) {
				move(k, level);
				moved.add(k);
				continue;
			}
			String from = k.substring(1), to = child(level, name(from));
			if (!from.equals(to)) {
				if (!moveFolder(from, level)) return keys;
				seq.replaceAll(x -> isFolderKey(x) ? folderKey(remap(x.substring(1), from, to)) : x);
			}
			moved.add(folderKey(to));
		}
		seq.removeAll(moved);
		seq.addAll(seq.indexOf(anchor) + (after ? 1 : 0), moved);
		order.removeAll(seq);
		order.addAll(0, seq);
		return moved;
	}

	/** Moves folder {@code path} into {@code into} ("" = root). Refused inside itself, or where a folder of that name already is. */
	public boolean moveFolder(String path, String into) {
		String to = child(into, name(path));
		if (inside(into, path) || folders.contains(to)) return false;
		movePrefix(path, to);
		return true;
	}

	/** Drop on a folder row: an item goes into {@code folder}, a folder (its {@link #folderKey}) becomes its subfolder. Returns the key as it is now. */
	public String moveInto(String key, String folder) {
		if (!isFolderKey(key)) {
			move(key, folder);
			return key;
		}
		String path = key.substring(1);
		return moveFolder(path, folder) ? folderKey(child(folder, name(path))) : key;
	}

	/** Out of the trash, back in the folder it was trashed from: made again, with its parents, if it was deleted since. */
	public void restore(String key) {
		trash.remove(key);
		for (String f = items.getOrDefault(key, ""); !f.isEmpty(); f = parent(f)) folders.add(f);
	}

	/**
	 * Takes in what {@code other} knows and this doesn't (up to 2.4 two instances could each organise the same
	 * saves folder in their own file): where both say something, this one wins.
	 */
	public void absorb(Space other) {
		folders.addAll(other.folders);
		other.items.forEach(items::putIfAbsent);
		favorites.addAll(other.favorites);
		trash.addAll(other.trash);
		trash.removeAll(favorites); // a favorite is never in the trash
		other.descriptions.forEach(descriptions::putIfAbsent);
		expanded.addAll(other.expanded);
		other.folderIcons.forEach(folderIcons::putIfAbsent);
		other.folderDescriptions.forEach(folderDescriptions::putIfAbsent);
		for (String k : other.order) if (!order.contains(k)) order.add(k);
		if (sort == Sort.DEFAULT) sort = other.sort;
	}

	/** Ctrl + click adds or removes one row; Shift + click selects the rows from the last one clicked, in {@code shown} order. */
	public void select(String key, boolean range, List<String> shown) {
		int from = selectionAnchor == null ? -1 : shown.indexOf(selectionAnchor), to = shown.indexOf(key);
		if (range && from >= 0 && to >= 0) {
			selection.clear();
			selection.addAll(shown.subList(Math.min(from, to), Math.max(from, to) + 1));
			return;
		}
		if (!selection.remove(key)) selection.add(key);
		selectionAnchor = key;
	}

	/** The selected keys in {@code shown} order (the ones hidden by a folder or a filter too, at the end). */
	public List<String> selected(List<String> shown) {
		List<String> out = new ArrayList<>(shown);
		out.retainAll(selection);
		for (String k : selection) if (!out.contains(k)) out.add(k);
		return out;
	}

	/**
	 * Icon prompt suggestions among {@code ids} ("namespace:path"): the namespaces first ("minecraft:"
	 * then the mods), and once one is typed, its items.
	 */
	public static List<String> suggest(Collection<String> ids, String typed, int max) {
		String t = typed.trim().toLowerCase(Locale.ROOT);
		Stream<String> found = t.contains(":")
				? ids.stream().filter(id -> id.startsWith(t)).sorted()
				: ids.stream().map(id -> id.substring(0, id.indexOf(':') + 1)).distinct().filter(ns -> ns.startsWith(t))
				.sorted(Comparator.comparing((String ns) -> !ns.equals("minecraft:")).thenComparing(Comparator.naturalOrder()));
		return found.limit(max).toList();
	}

	/** A world will be made for {@code folder}: {@link #adopt} puts it there. {@code known} = the keys of today. */
	public void expect(String folder, Collection<String> known) {
		pendingFolder = folder;
		pendingKnown = new HashSet<>(known);
	}

	/**
	 * Called with every key each time the list is read: the keys that appeared since {@link #expect} go
	 * into its folder. One reading only, so a world made later the usual way stays at the root.
	 * Returns true if there was something to forget (the caller saves).
	 */
	public boolean adopt(Collection<String> keys) {
		if (pendingFolder == null) return false;
		if (folders.contains(pendingFolder) && pendingKnown != null) {
			for (String k : keys) if (!pendingKnown.contains(k)) items.put(k, pendingFolder);
		}
		pendingFolder = null;
		pendingKnown = null;
		return true;
	}

	/** Favorites can't be trashed. Returns false when refused. */
	public boolean toTrash(String key) {
		if (favorites.contains(key)) return false;
		trash.add(key);
		return true;
	}

	/** The item is really gone: drop everything about it so a future item reusing the key starts clean. */
	public void forget(String key) {
		items.remove(key);
		trash.remove(key);
		favorites.remove(key);
		descriptions.remove(key);
		order.remove(key);
		selection.remove(key);
	}

	/** Items directly in {@code folder}, and in it or its subfolders (the trash doesn't count). */
	public long[] count(String folder) {
		long direct = 0, all = 0;
		for (var e : items.entrySet()) {
			if (trash.contains(e.getKey())) continue;
			if (e.getValue().equals(folder)) direct++;
			if (inside(e.getValue(), folder)) all++;
		}
		return new long[]{direct, all};
	}
}
