package com.codex.sessionchronicle.core;

import java.util.Locale;

/**
 * The rooftop agility courses, the ones that drop marks of grace. Levels, experience a lap and the
 * wiki's rates are from the Old School wiki (Rooftop Agility and each course's own page); the mark
 * chances are from its Mark of grace page; the map regions are the ones RuneLite's own Agility plugin
 * knows each course by. The game names a course in its lap count message ("Your Seers' Village
 * Rooftop lap count is: 12."), and RuneLite's chat commands read the same names.
 */
public enum Rooftop
{
	DRAYNOR("Draynor Village Rooftop", "Draynor", 1, 120, 120, 12338, 10_000, 10, 13.8, 2, 6, false),
	AL_KHARID("Al Kharid Rooftop", "Al Kharid", 20, 216, 216, 13105, 12_000, 8, 11.5, 2, 6, false),
	VARROCK("Varrock Rooftop", "Varrock", 30, 270, 270, 12853, 14_000, 8, 11.3, 2, 6, false),
	CANIFIS("Canifis Rooftop", "Canifis", 40, 240, 240, 13878, 19_700, 16, 18, 2, 3, false),
	FALADOR("Falador Rooftop", "Falador", 50, 586, 586, 12084, 35_000, 10, 12.1, 2, 6, false),
	SEERS("Seers' Village Rooftop", "Seers'", 60, 570, 570, 10806, 46_800, 11, 13.8, 2, 6, false),
	POLLNIVNEACH("Pollnivneach Rooftop", "Pollnivneach", 70, 890, 1016, 13358, 52_300, 13, 14, 2, 6, true),
	RELLEKKA("Rellekka Rooftop", "Rellekka", 80, 780, 920, 10553, 55_000, 13, 15.5, 2, 5, true),
	ARDOUGNE("Ardougne Rooftop", "Ardougne", 90, 889, 889, 10547, 70_000, 16, 18.1, 2, 3, true);

	/** How the game names the course before " lap count is". */
	private final String gameName;
	private final String shortName;
	private final int level;
	private final int xpPerLap;
	/** Experience a lap with the area's diary done: Pollnivneach with the Desert hard, Rellekka with the Fremennik hard. */
	private final int xpPerLapDiary;
	private final int region;
	/** The wiki's experience an hour without a diary, its best case with perfect laps. */
	private final int wikiXpPerHour;
	/** The wiki's marks an hour without a diary, low and high. */
	private final double wikiMarksLow;
	private final double wikiMarksHigh;
	/** The chance of a mark on a lap once the timer has run out, as a fraction. */
	private final int markChanceOf;
	private final int markChanceIn;
	/** Whether a diary of the area can shorten the mark timer to two minutes. */
	private final boolean diaryTimer;

	Rooftop(String gameName, String shortName, int level, int xpPerLap, int xpPerLapDiary, int region, int wikiXpPerHour,
		double wikiMarksLow, double wikiMarksHigh, int markChanceOf, int markChanceIn, boolean diaryTimer)
	{
		this.gameName = gameName;
		this.shortName = shortName;
		this.level = level;
		this.xpPerLap = xpPerLap;
		this.xpPerLapDiary = xpPerLapDiary;
		this.region = region;
		this.wikiXpPerHour = wikiXpPerHour;
		this.wikiMarksLow = wikiMarksLow;
		this.wikiMarksHigh = wikiMarksHigh;
		this.markChanceOf = markChanceOf;
		this.markChanceIn = markChanceIn;
		this.diaryTimer = diaryTimer;
	}

	/** The course a kill count line is for, "Seers' Village Rooftop lap" or "Seers' Village Rooftop", or null. */
	public static Rooftop byLapName(String name)
	{
		if (name == null)
		{
			return null;
		}
		String n = name.trim().toLowerCase(Locale.ROOT);
		if (n.endsWith(" lap"))
		{
			n = n.substring(0, n.length() - 4);
		}
		// The game has written "Al-Kharid" with a hyphen in places; read either.
		n = n.replace('-', ' ');
		for (Rooftop r : values())
		{
			if (r.gameName.toLowerCase(Locale.ROOT).equals(n))
			{
				return r;
			}
		}
		return null;
	}

	/** The course whose map region this is, or null. */
	public static Rooftop byRegion(int region)
	{
		for (Rooftop r : values())
		{
			if (r.region == region)
			{
				return r;
			}
		}
		return null;
	}

	public String getGameName()
	{
		return gameName;
	}

	public String getShortName()
	{
		return shortName;
	}

	public int getLevel()
	{
		return level;
	}

	public int xpPerLap(boolean diary)
	{
		return diary ? xpPerLapDiary : xpPerLap;
	}

	/** Whether a diary changes the experience of a lap here. */
	public boolean hasDiaryXp()
	{
		return xpPerLapDiary != xpPerLap;
	}

	public int getRegion()
	{
		return region;
	}

	public int getWikiXpPerHour()
	{
		return wikiXpPerHour;
	}

	public double getWikiMarksLow()
	{
		return wikiMarksLow;
	}

	public double getWikiMarksHigh()
	{
		return wikiMarksHigh;
	}

	/** The chance of a mark on a lap once the timer has run out, before any level penalty. */
	public double markChance()
	{
		return markChanceOf / (double) markChanceIn;
	}

	/** "2/6", as the wiki writes it. */
	public String markChanceText()
	{
		return markChanceOf + "/" + markChanceIn;
	}

	public boolean hasDiaryTimer()
	{
		return diaryTimer;
	}

	/**
	 * Whether marks come 80% less often here at this Agility level: an unboosted level 20 or more over
	 * the course's. Canifis is spared, by a coding error the wiki notes.
	 */
	public boolean markPenalty(int agilityLevel)
	{
		return this != CANIFIS && agilityLevel >= level + 20;
	}
}
