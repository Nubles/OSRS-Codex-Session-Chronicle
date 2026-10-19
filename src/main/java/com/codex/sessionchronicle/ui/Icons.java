package com.codex.sessionchronicle.ui;

import java.awt.image.BufferedImage;

/** Where the panel and the card get skill pictures; the plugin answers from RuneLite. */
public interface Icons
{
	/** @return the small icon of a skill by its name, such as "Slayer", or null */
	BufferedImage skill(String name);

	/**
	 * @param onLoaded run once if the icon arrives later than this call
	 * @return the hiscores icon of a boss by its name, such as "Vorkath", or null for a name that is
	 *     no boss and while the icon is still loading
	 */
	default BufferedImage boss(String name, Runnable onLoaded)
	{
		return null;
	}
}
