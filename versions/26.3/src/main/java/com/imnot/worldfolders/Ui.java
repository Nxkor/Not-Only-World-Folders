package com.imnot.worldfolders;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class Ui {
	public static final int INDENT = 12;
	/** Tint over the rows of a Ctrl / Shift multi-selection. */
	public static final int SELECTED = 0x503A7AFF;

	public static MutableComponent tr(String key, Object... args) {
		return Component.translatable("worldfolders." + key, args);
	}

	public static double mouseX() {
		Minecraft mc = Minecraft.getInstance();
		return mc.mouseHandler.getScaledXPos(mc.getWindow());
	}

	public static double mouseY() {
		Minecraft mc = Minecraft.getInstance();
		return mc.mouseHandler.getScaledYPos(mc.getWindow());
	}

	public static void toast(Component title, Component text) {
		SystemToast.add(Minecraft.getInstance().gui.toastManager(), SystemToast.SystemToastId.WORLD_ACCESS_FAILURE, title, text);
	}

	/** Whether this is the label of the FOV slider, whichever screen or mod built the slider. */
	public static boolean isFov(Component label) {
		return label.getContents() instanceof TranslatableContents t && t.getArgs().length > 0 && t.getArgs()[0] instanceof Component caption
				&& caption.getContents() instanceof TranslatableContents c && c.getKey().equals("options.fov");
	}

	/** The widgets our mixins added to vanilla screens. Weak: a screen closed takes its widgets away. */
	private static final Set<Object> OURS = Collections.newSetFromMap(new WeakHashMap<>());

	public static void ours(Object widget) {
		OURS.add(widget);
	}

	/** Our widgets are drawn last and hear clicks first, whatever other mods put on the same screen. */
	public static void raise(List<GuiEventListener> children, List<Renderable> renderables) {
		List<GuiEventListener> heard = children.stream().filter(OURS::contains).toList();
		if (heard.isEmpty()) return;
		children.removeAll(heard);
		children.addAll(0, heard);
		List<Renderable> drawn = renderables.stream().filter(OURS::contains).toList();
		renderables.removeAll(drawn);
		renderables.addAll(drawn);
	}

	/** Description text: "&" colour/format codes like chat plugins, "\n" for a new line. */
	public static MutableComponent format(String raw) {
		MutableComponent out = Component.empty();
		Style style = Style.EMPTY;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			ChatFormatting f = (c == '&' || c == '§') && i + 1 < raw.length() ? ChatFormatting.getByCode(raw.charAt(i + 1)) : null;
			boolean newline = c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == 'n';
			if (f == null && !newline) {
				run.append(c);
				continue;
			}
			out.append(Component.literal(run.toString()).withStyle(style));
			run.setLength(0);
			if (newline) run.append('\n');
			else style = f == ChatFormatting.RESET ? Style.EMPTY : style.applyLegacyFormat(f);
			i++;
		}
		return out.append(Component.literal(run.toString()).withStyle(style));
	}

	/**
	 * World/server names take "&" codes too, stored as vanilla "§" codes: Minecraft draws those itself,
	 * so the colours stay even without the mod. {@link #uncolors} gives the editable form back.
	 */
	public static String colors(String name) {
		return name.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
	}

	public static String uncolors(String name) {
		return name.replace('§', '&');
	}

	private static final Map<String, ItemStack> ICONS = new HashMap<>();
	private static @Nullable List<String> itemIds;

	/**
	 * Item of a folder icon id ("minecraft:diamond", or any item of a loaded mod), EMPTY if unknown.
	 * Drawn like any item, so the active resource packs change it too.
	 */
	public static ItemStack item(String id) {
		return ICONS.computeIfAbsent(id, k -> {
			Identifier rl = Identifier.tryParse(k.trim());
			return rl == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(rl).map(ItemStack::new).orElse(ItemStack.EMPTY);
		});
	}

	/** Every item id of this game instance, mods included: the icon prompt suggests from them. */
	public static List<String> itemIds() {
		if (itemIds == null) itemIds = BuiltInRegistries.ITEM.keySet().stream().map(Identifier::toString).filter(id -> !id.equals("minecraft:air")).toList();
		return itemIds;
	}

	public static String duration(long seconds) {
		long h = seconds / 3600, m = seconds / 60 % 60;
		return h > 0 ? String.format("%dh%02d", h, m) : m + " min";
	}

	/** A value of the world/server tooltip: it replaces "{placeholder}" in the description, or else comes as a white line of its own. */
	public record Info(String placeholder, String key, String value) {
	}

	public static Info playTime(long seconds) {
		return new Info("time", "play_time", duration(seconds));
	}

	/** Size on disk; negative = still being measured. */
	public static Info size(long bytes) {
		return new Info("size", "size", bytes < 0 ? "..." : bytes(bytes));
	}

	static String bytes(long b) {
		String[] units = I18n.get("worldfolders.units").split(",");
		int i = 0;
		double v = b;
		while (v >= 1024 && i < units.length - 1) {
			v /= 1024;
			i++;
		}
		return (i == 0 ? String.valueOf(b) : String.format("%.1f", v)) + " " + units[i];
	}

	/** Shows a description (may be null) as a tooltip, one wrapped block per line, with its {@link Info}s. */
	public static void showDescription(GuiGraphicsExtractor g, @Nullable String raw, int mouseX, int mouseY, Info... infos) {
		Font font = Minecraft.getInstance().font;
		List<FormattedCharSequence> lines = new ArrayList<>();
		List<Info> ownLines = new ArrayList<>();
		for (Info info : infos) {
			String placeholder = "{" + info.placeholder() + "}";
			if (raw != null && raw.contains(placeholder)) raw = raw.replace(placeholder, info.value());
			else ownLines.add(info);
		}
		if (raw != null) lines.addAll(font.split(format(raw), 220));
		for (Info info : ownLines) lines.addAll(font.split(tr(info.key(), info.value()), 220));
		if (!lines.isEmpty()) g.setTooltipForNextFrame(lines, mouseX, mouseY);
	}

	/** Ctrl / Shift held (multi-selection). */
	public static boolean ctrl() {
		return Minecraft.getInstance().hasControlDown();
	}

	public static boolean shift() {
		return Minecraft.getInstance().hasShiftDown();
	}

	/** The sort/filter button: opens a dropdown of {@link Space.Sort} under itself. Its arrows are drawn by {@link #renderSortArrows}. */
	public static Button sortButton(Screen screen, Supplier<Space> space, Runnable refresh, int x, int y) {
		return Button.builder(Component.empty(), b -> {
			Space s = space.get();
			ContextMenu menu = ContextMenu.on(screen);
			for (Space.Sort sort : Space.Sort.values()) {
				menu.add(Component.literal(s.sort == sort ? "✔ " : "    ").append(tr("sort." + sort.name().toLowerCase(Locale.ROOT))), () -> {
					s.sort = sort;
					Store.save();
					refresh.run();
				});
			}
			menu.openAt(b.getX(), b.getY() + b.getHeight());
		}).bounds(x, y, 20, 20).build();
	}

	/**
	 * The two arrows of the sort button, side by side and as far from its left edge as from its right one
	 * (a centred label can't be: its width is odd). Yellow while a sort/filter other than the default is on,
	 * so a filtered list isn't mistaken for missing worlds. Not drawn under an open dropdown: they come
	 * after the widgets, and would show through it.
	 */
	public static void renderSortArrows(GuiGraphicsExtractor g, @Nullable Button b, Space space) {
		if (b == null || !b.visible) return;
		Minecraft mc = Minecraft.getInstance();
		ContextMenu menu = mc.gui.screen() == null ? null : ((Hooks.Overlay) mc.gui.screen()).worldfolders$menu();
		if (menu != null && menu.covers(b.getX(), b.getY(), b.getWidth(), b.getHeight())) return;
		Font font = mc.font;
		int up = font.width("↑") - 1, down = font.width("↓") - 1; // a glyph's width counts the blank pixel after it
		int gap = (b.getWidth() - up - down) % 2; // one pixel apart only when that is what centres them
		int x = b.getX() + (b.getWidth() - up - gap - down) / 2, y = b.getY() + (b.getHeight() - 8) / 2;
		int color = space.sort == Space.Sort.DEFAULT ? 0xFFFFFFFF : 0xFFFFFF55;
		g.text(font, "↑", x, y, color);
		g.text(font, "↓", x + up + gap, y, color);
	}

	/** A notification like an advancement's, top right. The options say how long it stays, or turn it off. */
	public static void notify(Component title, Component text) {
		Store.Config c = Store.config();
		if (!c.notifications) return;
		SystemToast.add(Minecraft.getInstance().gui.toastManager(), new SystemToast.SystemToastId((long) (c.notificationSeconds * 1000)), title, text);
	}
}
