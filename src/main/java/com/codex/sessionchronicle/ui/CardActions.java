package com.codex.sessionchronicle.ui;

import java.awt.image.BufferedImage;

/** What the Card tab's buttons do; the plugin saves files and fills the clipboard. */
public interface CardActions
{
	/** Asks where to save the card, offering that file name. */
	void save(BufferedImage card, String fileName);

	void copy(BufferedImage card);

	/** Puts the session's recap on the clipboard as plain text. */
	void copyText(String text);
}
