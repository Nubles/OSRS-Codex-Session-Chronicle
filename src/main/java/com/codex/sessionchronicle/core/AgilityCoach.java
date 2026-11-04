package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The rooftop coach: for each course, the player's own pace there and the experience and marks of
 * grace an hour it makes, or the wiki's figures where the player has not run it enough; which course
 * is best for them now; and how far their Graceful outfit is, with an estimate of the time to finish it.
 * Built on the client thread from the agility log, then handed to the panel, which only reads it.
 */
public final class AgilityCoach
{
	/** Laps on a course before its rates are the player's own rather than the wiki's. */
	public static final int OWN_LAPS = 5;
	/** Ticks timed on a course before its marks found make an hourly rate worth showing: twenty minutes. */
	public static final long OWN_MARK_TICKS = 2000;

	/** Where a figure comes from, which the panel always says. */
	public enum Source
	{
		/** The player's own laps. */
		YOURS,
		/** The Old School wiki's rates for the course. */
		WIKI
	}

	/** The diaries that change a course's experience or mark timer. */
	public static final class Diaries
	{
		final boolean desertHard;
		final boolean fremennikHard;
		final boolean ardougneElite;

		public Diaries(boolean desertHard, boolean fremennikHard, boolean ardougneElite)
		{
			this.desertHard = desertHard;
			this.fremennikHard = fremennikHard;
			this.ardougneElite = ardougneElite;
		}

		/** Whether the diary that matters on this course is done. */
		public boolean on(Rooftop r)
		{
			switch (r)
			{
				case POLLNIVNEACH:
					return desertHard;
				case RELLEKKA:
					return fremennikHard;
				case ARDOUGNE:
					return ardougneElite;
				default:
					return false;
			}
		}
	}

	/** One course as the coach sees it. */
	public static final class Course
	{
		private final Rooftop course;
		private final boolean unlocked;
		private final AgilityLog.CourseLog log;
		private final int lapTicks;
		private final Source paceSource;
		private final long xpPerHour;
		private final double marksExpected;
		private final double marksSeen;
		private final boolean penalty;
		private final boolean diary;

		Course(Rooftop course, int level, AgilityLog.CourseLog log, Diaries diaries)
		{
			this.course = course;
			this.unlocked = level >= course.getLevel();
			this.log = log;
			this.diary = diaries.on(course);
			this.penalty = course.markPenalty(level);
			int xpLap = course.xpPerLap(diary);
			boolean own = log != null && log.getLaps() >= OWN_LAPS && log.typicalTicks() > 0;
			paceSource = own ? Source.YOURS : Source.WIKI;
			lapTicks = own ? log.typicalTicks() : wikiLapTicks(course);
			double perLap = own && log.xpPerLap() > 0 ? log.xpPerLap() : xpLap;
			xpPerHour = lapTicks <= 0 ? 0 : Math.round(MarkRate.TICKS_PER_HOUR / lapTicks * perLap);
			marksExpected = MarkRate.perHour(course, lapTicks, level, diary);
			marksSeen = log != null && log.getTicks() >= OWN_MARK_TICKS ? log.getMarks() * MarkRate.TICKS_PER_HOUR / log.getTicks() : -1;
		}

		public Rooftop getCourse()
		{
			return course;
		}

		public boolean isUnlocked()
		{
			return unlocked;
		}

		/** The player's record here, or null if never run. */
		public AgilityLog.CourseLog getLog()
		{
			return log;
		}

		/** The lap time the rates assume: the player's typical lap, or the one the wiki's rate implies. */
		public int getLapTicks()
		{
			return lapTicks;
		}

		public Source getPaceSource()
		{
			return paceSource;
		}

		public long getXpPerHour()
		{
			return xpPerHour;
		}

		/** Marks an hour the wiki's rules give at this pace. */
		public double getMarksExpected()
		{
			return marksExpected;
		}

		/** Marks an hour the player has actually found here, or below nothing until there is enough to tell. */
		public double getMarksSeen()
		{
			return marksSeen;
		}

		/** The marks an hour to plan by: those found, once there are enough, else those expected. */
		public double marksPerHour()
		{
			return marksSeen >= 0 ? marksSeen : marksExpected;
		}

		public boolean hasPenalty()
		{
			return penalty;
		}

		public boolean hasDiary()
		{
			return diary;
		}
	}

	private final int level;
	private final List<Course> courses;
	private final Course bestXp;
	private final Course bestMarks;
	private final Set<Graceful> owned;
	private final int marksOwned;
	private final boolean bankSeen;
	private final LapClock.Split split;
	private final int laps;

	private AgilityCoach(int level, List<Course> courses, Set<Graceful> owned, int marksOwned, boolean bankSeen, LapClock.Split split)
	{
		this.level = level;
		this.courses = Collections.unmodifiableList(courses);
		this.owned = owned;
		this.marksOwned = marksOwned;
		this.bankSeen = bankSeen;
		this.split = split;
		Course x = null;
		Course m = null;
		int total = 0;
		for (Course c : courses)
		{
			total += c.log == null ? 0 : c.log.getLaps();
			if (!c.unlocked)
			{
				continue;
			}
			if (x == null || c.xpPerHour > x.xpPerHour)
			{
				x = c;
			}
			if (m == null || c.marksPerHour() > m.marksPerHour())
			{
				m = c;
			}
		}
		bestXp = x;
		bestMarks = m;
		laps = total;
	}

	/**
	 * @param level the player's unboosted Agility level, or 0 if not known yet
	 * @param carriedMarks marks of grace in the inventory now
	 * @param split the lap under way against the best lap, or null
	 */
	public static AgilityCoach of(AgilityLog log, int level, Diaries diaries, int carriedMarks, LapClock.Split split)
	{
		List<Course> list = new ArrayList<>();
		for (Rooftop r : Rooftop.values())
		{
			list.add(new Course(r, level, log.get(r), diaries));
		}
		int bank = log.getBankMarks();
		return new AgilityCoach(level, list, log.getGraceful(), Math.max(0, bank) + Math.max(0, carriedMarks), bank >= 0, split);
	}

	/** The lap time the wiki's best-case experience rate implies: a lap's experience over the rate. */
	static int wikiLapTicks(Rooftop r)
	{
		return (int) Math.round(MarkRate.TICKS_PER_HOUR * r.xpPerLap(false) / r.getWikiXpPerHour());
	}

	public int getLevel()
	{
		return level;
	}

	public List<Course> getCourses()
	{
		return courses;
	}

	public Course course(Rooftop r)
	{
		for (Course c : courses)
		{
			if (c.course == r)
			{
				return c;
			}
		}
		return null;
	}

	/** The unlocked course with the most experience an hour for this player now, or null with none unlocked. */
	public Course getBestXp()
	{
		return bestXp;
	}

	/** The unlocked course with the most marks an hour for this player now. */
	public Course getBestMarks()
	{
		return bestMarks;
	}

	public Set<Graceful> getOwned()
	{
		return owned;
	}

	/** Marks of grace in the bank when last seen and carried now. */
	public int getMarksOwned()
	{
		return marksOwned;
	}

	/** Whether the bank has been seen, so the marks and pieces in it are counted. */
	public boolean isBankSeen()
	{
		return bankSeen;
	}

	/** Marks still to find for the pieces not yet owned, after the marks already owned. */
	public int marksNeeded()
	{
		return Math.max(0, Graceful.costOfMissing(owned) - marksOwned);
	}

	public boolean outfitDone()
	{
		return owned.size() == Graceful.values().length;
	}

	/** Hours to find the marks still needed on a course, or below nothing if it cannot be told. */
	public double hoursToOutfit(Course c)
	{
		if (c == null || c.marksPerHour() <= 0)
		{
			return -1;
		}
		return marksNeeded() / c.marksPerHour();
	}

	public LapClock.Split getSplit()
	{
		return split;
	}

	/** Laps timed on every course. */
	public int getLaps()
	{
		return laps;
	}

	/** Courses the player has run, most laps first. */
	public List<Course> run()
	{
		List<Course> out = new ArrayList<>();
		for (Course c : courses)
		{
			if (c.log != null && c.log.getLaps() > 0)
			{
				out.add(c);
			}
		}
		out.sort((a, b) -> Integer.compare(b.log.getLaps(), a.log.getLaps()));
		return out;
	}

	/** Unlocked courses, most experience an hour first. */
	public List<Course> ranked()
	{
		List<Course> out = new ArrayList<>();
		for (Course c : courses)
		{
			if (c.unlocked)
			{
				out.add(c);
			}
		}
		out.sort((a, b) -> Long.compare(b.xpPerHour, a.xpPerHour));
		return out;
	}
}
