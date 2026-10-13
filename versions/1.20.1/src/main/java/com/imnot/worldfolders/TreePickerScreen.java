package com.imnot.worldfolders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** "Move to…": the whole folder tree; clicking a folder picks it. */
public class TreePickerScreen extends Screen {
	private final Screen parent;
	private final Space space;
	private final Consumer<String> onPick;

	public TreePickerScreen(Screen parent, Space space, Consumer<String> onPick) {
		super(Ui.tr("move"));
		this.parent = parent;
		this.space = space;
		this.onPick = onPick;
	}

	@Override
	protected void init() {
		Tree tree = new Tree(minecraft, width, height, 32, height - 40);
		tree.add(new Row("", "/", 0));
		for (String f : space.tree()) tree.add(new Row(f, Space.name(f), f.split("/").length));
		addRenderableWidget(tree);
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 - 100, height - 30, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderBackground(g); // 1.20.1 screens draw their own background
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private static class Tree extends ObjectSelectionList<Row> {
		Tree(Minecraft mc, int width, int height, int top, int bottom) {
			super(mc, width, height, top, bottom, 18);
		}

		void add(Row row) {
			addEntry(row);
		}
	}

	private class Row extends ObjectSelectionList.Entry<Row> {
		private final String path, label;
		private final int depth;

		Row(String path, String label, int depth) {
			this.path = path;
			this.label = label;
			this.depth = depth;
		}

		@Override
		public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
			g.drawString(font, Ui.format((depth == 0 ? "" : "└ ") + label), left + 4 + Math.max(0, depth - 1) * Ui.INDENT, top + 3, hovering ? 0xFFFFFF55 : 0xFFFFFFFF);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			onPick.accept(path);
			onClose();
			return true;
		}

		@Override
		public Component getNarration() {
			return Component.literal(label);
		}
	}
}
