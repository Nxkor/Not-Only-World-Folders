package com.imnot.worldfolders;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Asks for one line of text, then returns to {@code parent}. Can list suggestions: Tab walks them, a click takes one, the wheel scrolls. */
public class PromptScreen extends Screen {
	private static final int SUGGESTION_ROW = 12;
	private final Screen parent;
	private final String initial;
	private final Consumer<String> onDone;
	private int maxLength = 64;
	private Component hint = Component.empty();
	private Function<String, List<String>> suggestions = text -> List.of();
	private @Nullable Function<String, ItemStack> preview;
	/** Every suggestion for the text typed; {@code scroll} is the first one drawn, {@code cycle} the one Tab is on (-1: none). */
	private List<String> shown = List.of();
	private int scroll, cycle = -1;
	private boolean cycling;
	private EditBox box;

	public PromptScreen(Component title, String initial, Consumer<String> onDone, Screen parent) {
		super(title);
		this.parent = parent;
		this.initial = initial;
		this.onDone = onDone;
	}

	public PromptScreen maxLength(int maxLength) {
		this.maxLength = maxLength;
		return this;
	}

	/** Grey line under the buttons, e.g. which colour codes the name takes. */
	public PromptScreen hint(Component hint) {
		this.hint = hint;
		return this;
	}

	/** Suggestions for the text typed so far, listed under the hint. */
	public PromptScreen suggestions(Function<String, List<String>> suggestions) {
		this.suggestions = suggestions;
		return this;
	}

	/** Icon prompts: draws, left of the box, the item the text typed (or the suggestion under the mouse) stands for. */
	public PromptScreen preview(Function<String, ItemStack> preview) {
		this.preview = preview;
		return this;
	}

	@Override
	protected void init() {
		box = new EditBox(font, width / 2 - 150, height / 2 - 20, 300, 20, title);
		box.setMaxLength(maxLength);
		box.setResponder(this::suggest);
		box.setValue(initial); // calls suggest
		addRenderableWidget(box);
		setInitialFocus(box);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> done()).bounds(width / 2 - 150, height / 2 + 10, 148, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 + 2, height / 2 + 10, 148, 20).build());
	}

	private void suggest(String text) {
		if (cycling) return; // Tab is walking the list: it stays as it is
		shown = suggestions.apply(text);
		scroll = 0;
		cycle = -1;
		// Grey completion in the box itself, as in the chat.
		box.setSuggestion(!shown.isEmpty() && shown.get(0).startsWith(text) ? shown.get(0).substring(text.length()) : null);
	}

	/** How many suggestions fit under the hint; the mouse wheel scrolls the others in. */
	private int rows() {
		return Math.max(1, (height - suggestionsTop()) / SUGGESTION_ROW);
	}

	/** The hint, wrapped to the box width. */
	private List<FormattedCharSequence> hintLines() {
		return font.split(hint, 300);
	}

	private int suggestionsTop() {
		return height / 2 + 40 + hintLines().size() * 10 + 4;
	}

	private int suggestionAt(double mouseX, double mouseY) {
		int row = (int) Math.floor((mouseY - suggestionsTop()) / SUGGESTION_ROW), i = row + scroll;
		return mouseX >= width / 2 - 150 && mouseX < width / 2 + 150 && row >= 0 && row < rows() && i < shown.size() ? i : -1;
	}

	/**
	 * Tab walks the suggestions like the chat walks player names, Shift + Tab backwards; held, it keeps
	 * going. A lone suggestion (a namespace) is taken for good: its own items are suggested next.
	 */
	private void tab() {
		cycle = cycle < 0 ? (Ui.shift() ? shown.size() - 1 : 0) : Math.floorMod(cycle + (Ui.shift() ? -1 : 1), shown.size());
		int at = cycle;
		cycling = shown.size() > 1;
		box.setValue(shown.get(at));
		if (!cycling) return;
		cycling = false;
		box.setSuggestion(null);
		scroll = Math.max(Math.min(scroll, at), at - rows() + 1); // the one taken stays in view
	}

	private void done() {
		onDone.accept(box.getValue());
		onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 257 || event.key() == 335) { // Enter / keypad Enter
			done();
			return true;
		}
		if (event.key() == 258 && !shown.isEmpty()) {
			tab();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = suggestionAt(event.x(), event.y());
		if (i < 0) return super.mouseClicked(event, doubleClick);
		box.setValue(shown.get(i));
		setFocused(box);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		scroll = Math.max(0, Math.min(shown.size() - rows(), scroll - (int) Math.signum(dy)));
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.centeredText(font, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
		List<FormattedCharSequence> hints = hintLines();
		for (int i = 0; i < hints.size(); i++) g.centeredText(font, hints.get(i), width / 2, height / 2 + 40 + i * 10, 0xFFA0A0A0);
		int hover = suggestionAt(mouseX, mouseY);
		for (int i = scroll; i < shown.size() && i < scroll + rows(); i++) {
			if (i == cycle) g.fill(width / 2 - 150, suggestionsTop() + (i - scroll) * SUGGESTION_ROW, width / 2 + 150, suggestionsTop() + (i - scroll + 1) * SUGGESTION_ROW, 0xFF3A3A6A);
			g.text(font, shown.get(i), width / 2 - 146, suggestionsTop() + (i - scroll) * SUGGESTION_ROW + 2, i == hover ? 0xFFFFFF55 : i == cycle ? 0xFFFFFFFF : 0xFFC0C0C0);
		}
		if (scroll > 0) g.text(font, "▲", width / 2 + 154, suggestionsTop() + 2, 0xFFAAAAAA);
		if (scroll + rows() < shown.size()) g.text(font, "▼", width / 2 + 154, suggestionsTop() + (rows() - 1) * SUGGESTION_ROW + 2, 0xFFAAAAAA);
		if (preview == null) return;
		int x = width / 2 - 174, y = height / 2 - 20;
		g.fill(x, y, x + 20, y + 20, 0xFFA0A0A0);
		g.fill(x + 1, y + 1, x + 19, y + 19, 0xFF000000);
		ItemStack stack = preview.apply(hover >= 0 ? shown.get(hover) : box.getValue());
		if (stack.isEmpty() && !shown.isEmpty()) stack = preview.apply(shown.get(0)); // what Tab would complete to
		if (stack.isEmpty()) g.centeredText(font, "?", x + 10, y + 6, 0xFFFF5555); // no such item
		else g.item(stack, x + 2, y + 2);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
