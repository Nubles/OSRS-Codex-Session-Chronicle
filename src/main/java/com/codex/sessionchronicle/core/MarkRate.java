package com.codex.sessionchronicle.core;

/**
 * Marks of grace an hour expected at a given lap time, from the rules the wiki's Mark of grace page
 * sets out: after a mark spawns, no other can until a timer runs out (three minutes on most courses,
 * sometimes two with a diary); the timer counts in whole game minutes, so a mark spawned at any tick
 * of minute 0 can be followed from the first tick of minute 3, which makes the wait somewhere between
 * two and three minutes; once it has run out, each lap finished has the course's chance of a mark,
 * cut by 80% when the player's level is 20 or more over the course's.
 *
 * It is an estimate from published rules, not a measurement, and the panel says so; the player's own
 * marks found, once there are enough laps, are shown beside it.
 */
public final class MarkRate
{
	/** Game ticks in an hour. */
	public static final double TICKS_PER_HOUR = 6000;
	/** Steps the wait inside its minute is split into when averaging over it. */
	private static final int STEPS = 60;

	private MarkRate()
	{
	}

	/** The chance the timer is two minutes rather than three on this course with its diary done. */
	static double shortTimerChance(Rooftop course, boolean diary)
	{
		if (!diary || !course.hasDiaryTimer())
		{
			return 0;
		}
		return course == Rooftop.ARDOUGNE ? 0.5 : 1 / 3.0;
	}

	/** The chance of a mark on a lap once the timer has run out, with the level penalty. */
	public static double lapChance(Rooftop course, int agilityLevel)
	{
		double p = course.markChance();
		return course.markPenalty(agilityLevel) ? p * 0.2 : p;
	}

	/**
	 * Marks an hour expected when laps take this many ticks each, start to start.
	 *
	 * @param diary whether the diary that can shorten this course's timer is done
	 */
	public static double perHour(Rooftop course, int lapTicks, int agilityLevel, boolean diary)
	{
		if (lapTicks <= 0)
		{
			return 0;
		}
		double p = lapChance(course, agilityLevel);
		double shortChance = shortTimerChance(course, diary);
		double ticks = shortChance * meanTicksBetween(lapTicks, 2, p) + (1 - shortChance) * meanTicksBetween(lapTicks, 3, p);
		return ticks <= 0 ? 0 : TICKS_PER_HOUR / ticks;
	}

	/**
	 * The mean time between marks: the laps until the first lap ending once the timer is out, then a
	 * fresh chance on each lap after. Averaged over where in its game minute the last mark fell.
	 */
	static double meanTicksBetween(int lapTicks, int timerMinutes, double p)
	{
		double total = 0;
		for (int i = 0; i < STEPS; i++)
		{
			// The wait lies between (timer - 1) and timer minutes; a minute is 100 ticks.
			double wait = (timerMinutes - 1) * 100 + 100.0 * (i + 0.5) / STEPS;
			int first = (int) Math.ceil(wait / lapTicks);
			total += (first - 1 + 1 / p) * lapTicks;
		}
		return total / STEPS;
	}
}
