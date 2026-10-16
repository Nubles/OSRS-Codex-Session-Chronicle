package com.codex.sessionchronicle.ui;

import java.awt.image.BufferedImage;

/** Where the panel and the card get skill pictures; the plugin answers from RuneLite. */
public interface Icons
{
	/** @return the small icon of a skill by its name, such as "Slayer", or null */
	BufferedImage skill(String name);
}
