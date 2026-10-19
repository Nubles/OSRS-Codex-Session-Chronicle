package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Summary;
import java.awt.image.BufferedImage;

/** What the Card tab's buttons do; the plugin saves files and fills the clipboard. */
public interface CardActions
{
	void save(BufferedImage card, Summary session);

	void copy(BufferedImage card);

	/** Puts the session's recap on the clipboard as plain text. */
	void copyText(String text);
}
