package com.codex.sessionchronicle.core;

import java.util.Locale;

/**
 * Matches a boss as the journal names it to the boss as the hiscores name it, for its icon. The
 * two seldom agree word for word: the journal keeps the name the Loot Tracker or the kill count
 * message gave ("Barrows", "The Nightmare", "Dusk", "Lunar Chest"), the hiscores their own
 * ("Barrows Chests", "Nightmare", "Grotesque Guardians", "Lunar Chests").
 */
public final class Bosses
{
	private Bosses()
	{
	}

	/** The same key for every way a boss is named; the journal's own folding, with plural chests too. */
	public static String key(String name)
	{
		if (name == null)
		{
			return "";
		}
		String key = name.toLowerCase(Locale.ROOT).trim();
		if (key.endsWith(" chests"))
		{
			key = key.substring(0, key.length() - 1);
		}
		return Chronicle.key(key);
	}
}
