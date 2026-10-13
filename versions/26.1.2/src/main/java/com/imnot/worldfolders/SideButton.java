package com.imnot.worldfolders;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A button that stays beside another one ({@code dx} pixels left of it if negative, else that far right of it), wherever that one goes:
 * other mods (Mod Menu...) move the title screen's buttons after we added ours.
 */
public class SideButton extends Button.Plain {
	private final Button anchor;
	private final int dx;

	public SideButton(Button anchor, int dx, int width, Component label, OnPress onPress) {
		super(0, 0, width, 20, label, onPress, DEFAULT_NARRATION);
		this.anchor = anchor;
		this.dx = dx;
	}

	@Override
	public int getX() {
		return dx < 0 ? anchor.getX() + dx : anchor.getX() + anchor.getWidth() + dx;
	}

	@Override
	public int getY() {
		return anchor.getY();
	}
}
