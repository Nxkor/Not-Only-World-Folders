package com.imnot.worldfolders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * Right-click popup drawn on top of the current screen. It is an overlay, not a Screen, so the
 * world/server list underneath is not closed (no icon reload, no server re-ping).
 * Any click closes it; a click on an item runs that item. Taller than the screen, it scrolls (mouse wheel).
 * A row can open a submenu beside it.
 */
public class ContextMenu implements Renderable, GuiEventListener {
	private static final int ROW = 14, PAD = 3;
	private final Screen screen;
	private final List<Component> labels = new ArrayList<>();
	private final List<Runnable> actions = new ArrayList<>();
	private int x, y, w, h, rows, scroll;
	/** Rows that open another menu beside this one while hovered, and the one open now. */
	private final Map<Integer, ContextMenu> subs = new HashMap<>();
	private @Nullable ContextMenu sub;
	private Runnable onClose = () -> {
	};

	private ContextMenu(Screen screen) {
		this.screen = screen;
	}

	public static ContextMenu on(Screen screen) {
		return new ContextMenu(screen);
	}

	public ContextMenu add(Component label, Runnable action) {
		labels.add(label);
		actions.add(action);
		return this;
	}

	/** A row that opens {@code menu} beside it instead of running something. */
	public ContextMenu sub(Component label, ContextMenu menu) {
		subs.put(labels.size(), menu);
		return add(label.copy().append("  ▶"), () -> {
		});
	}

	public ContextMenu addIf(boolean condition, Component label, Runnable action) {
		return condition ? add(label, action) : this;
	}

	/** Runs when the menu goes away, whatever closed it (a click, Escape). */
	public ContextMenu onClose(Runnable onClose) {
		this.onClose = onClose;
		return this;
	}

	public void closed() {
		onClose.run();
	}

	/** Whether the menu is over some part of this rectangle. */
	public boolean covers(int rx, int ry, int rw, int rh) {
		return rx < x + w + 1 && rx + rw > x - 1 && ry < y + h + 1 && ry + rh > y - 1 || sub != null && sub.covers(rx, ry, rw, rh);
	}

	/** Opens at the mouse, kept inside the screen. */
	public void open() {
		openAt((int) Ui.mouseX(), (int) Ui.mouseY());
	}

	/** Opens with its top-left corner at x, y (a dropdown under its button), kept inside the screen. */
	public void openAt(int atX, int atY) {
		place(atX, atY);
		((Hooks.Overlay) screen).worldfolders$setMenu(this);
	}

	private void place(int atX, int atY) {
		Font font = Minecraft.getInstance().font;
		w = 0;
		for (Component l : labels) w = Math.max(w, font.width(l));
		w += PAD * 2 + 8;
		rows = Math.max(1, Math.min(labels.size(), (screen.height - PAD * 2) / ROW));
		h = rows * ROW + PAD * 2;
		x = Math.max(0, Math.min(atX, screen.width - w));
		y = Math.max(0, Math.min(atY, screen.height - h));
		scroll = 0;
		sub = null;
	}

	/** The row under the mouse opens its submenu, right of this menu, or left of it at the screen edge. */
	private void hoverSub(int hover) {
		if (hover < 0 || subs.get(hover) == sub) return;
		sub = subs.get(hover);
		if (sub == null) return;
		int top = y + (hover - scroll) * ROW;
		sub.place(x + w + 1, top);
		if (sub.x < x + w) sub.place(x - sub.w - 1, top);
	}

	/** A click runs the row under it (this menu or its open submenu) and closes the menu; anywhere else it just closes. */
	private void click(double mx, double my) {
		ContextMenu in = sub != null && sub.rowAt(mx, my) >= 0 ? sub : this;
		int row = in.rowAt(mx, my);
		if (in == this && subs.containsKey(row)) return; // its submenu is open beside it
		((Hooks.Overlay) screen).worldfolders$setMenu(null);
		if (row >= 0 && row < in.actions.size()) in.actions.get(row).run();
	}

	private int rowAt(double mx, double my) {
		if (mx < x || mx >= x + w || my < y + PAD || my >= y + h - PAD) return -1;
		return (int) (my - y - PAD) / ROW + scroll;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		Font font = Minecraft.getInstance().font;
		g.nextStratum(); // above the list and its item icons
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF808080);
		g.fill(x, y, x + w, y + h, 0xFF101010); // opaque: the buttons under it must not show through
		int hover = rowAt(mouseX, mouseY);
		for (int i = scroll; i < scroll + rows && i < labels.size(); i++) {
			int ry = y + PAD + (i - scroll) * ROW;
			if (i == hover) g.fill(x + 1, ry, x + w - 1, ry + ROW, 0xFF3A3A6A);
			g.drawString(font, labels.get(i), x + PAD + 4, ry + 3, 0xFFFFFFFF);
		}
		if (scroll > 0) g.drawString(font, "▲", x + w - 9, y + PAD + 3, 0xFFAAAAAA);
		if (scroll + rows < labels.size()) g.drawString(font, "▼", x + w - 9, y + h - PAD - ROW + 3, 0xFFAAAAAA);
		hoverSub(hover);
		if (sub != null) sub.render(g, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isMouseOver(double mx, double my) {
		return true; // swallow every click while open
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		scroll = Math.max(0, Math.min(labels.size() - rows, scroll - (int) Math.signum(dy)));
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		click(event.x(), event.y());
		return true;
	}

	@Override
	public void setFocused(boolean focused) {
	}

	@Override
	public boolean isFocused() {
		return false;
	}
}
