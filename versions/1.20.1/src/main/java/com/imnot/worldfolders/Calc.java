package com.imnot.worldfolders;

/** Arithmetic of the exact FOV prompt. Pure Java, so SpaceCheck can run it. */
public final class Calc {
	/** The FOV typed, or null when it isn't a whole number the option takes (30 to 110). */
	public static Integer fov(String typed) {
		try {
			int v = Integer.parseInt(typed.trim());
			return v >= 30 && v <= 110 ? v : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Where the FOV slider stands for a value. The option reads its slider back as floor(30 + 80 x) up to
	 * 1.20.x and floor(30 + 81 x) since: the middle of what both read as {@code v}.
	 */
	public static double fovSlider(int v) {
		return v == 110 ? 1 : ((v - 30) / 80.0 + (v - 29) / 81.0) / 2;
	}
}
