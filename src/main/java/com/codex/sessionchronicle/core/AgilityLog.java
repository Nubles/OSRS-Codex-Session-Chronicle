package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One account's rooftop agility, kept apart from its sessions: for each course the laps timed, the
 * best lap with the moment of each obstacle in it (the ghost a new lap races), recent lap times,
 * experience and marks of grace found; and the Graceful pieces and banked marks last seen, for how
 * far the outfit is. Saved as JSON under the account's RuneScape profile.
 */
public final class AgilityLog
{
	/** Recent lap times kept for each course, for its typical lap and its trend. */
	static final int RECENT = 40;

	/** Each course's record, by the course's enum name. */
	Map<String, CourseLog> courses = new LinkedHashMap<>();
	/** Graceful pieces seen in the bank, worn or carried, by enum name. */
	List<String> graceful = new ArrayList<>();
	/** Marks of grace in the bank when it was last open, or -1 if never seen. */
	int bankMarks = -1;
	/** The unboosted Agility level last read, so the coach still knows it while logged out; 0 if never read. */
	int level;

	/** One course's laps. */
	public static final class CourseLog
	{
		int laps;
		/** Ticks of the best lap, start to start; 0 until one is timed. */
		int bestTicks;
		/** When in the best lap, in ticks from its start, each obstacle's experience came. */
		int[] bestSplits;
		/** When the best lap was run, epoch ms. */
		long bestAt;
		/** Recent lap times in ticks, oldest first. */
		List<Integer> recent;
		/** Ticks of all laps timed, for the hourly rates. */
		long ticks;
		/** Agility experience in the laps timed. */
		long xp;
		/** Marks of grace picked up while running the course. */
		int marks;
		/** When the last lap was run, epoch ms. */
		long lastAt;

		public int getLaps()
		{
			return laps;
		}

		public int getBestTicks()
		{
			return bestTicks;
		}

		public int[] getBestSplits()
		{
			return bestSplits == null ? new int[0] : bestSplits.clone();
		}

		public long getBestAt()
		{
			return bestAt;
		}

		public List<Integer> getRecent()
		{
			return recent == null ? Collections.emptyList() : Collections.unmodifiableList(recent);
		}

		public long getTicks()
		{
			return ticks;
		}

		public long getXp()
		{
			return xp;
		}

		public int getMarks()
		{
			return marks;
		}

		public long getLastAt()
		{
			return lastAt;
		}

		/** The middle of the recent laps, which a stumble or a bank trip does not drag; 0 with none. */
		public int typicalTicks()
		{
			if (recent == null || recent.isEmpty())
			{
				return 0;
			}
			List<Integer> sorted = new ArrayList<>(recent);
			Collections.sort(sorted);
			int n = sorted.size();
			return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2) + 1) / 2;
		}

		/** Experience a lap as measured, or 0 before any. */
		public double xpPerLap()
		{
			return laps <= 0 ? 0 : xp / (double) laps;
		}
	}

	/** A lap the clock timed. */
	public static final class Lap
	{
		private final Rooftop course;
		private final int ticks;
		private final int[] splits;
		private final long xp;

		public Lap(Rooftop course, int ticks, int[] splits, long xp)
		{
			this.course = course;
			this.ticks = ticks;
			this.splits = splits == null ? new int[0] : splits.clone();
			this.xp = xp;
		}

		public Rooftop getCourse()
		{
			return course;
		}

		public int getTicks()
		{
			return ticks;
		}

		public int[] getSplits()
		{
			return splits.clone();
		}

		public long getXp()
		{
			return xp;
		}
	}

	/** Reads a saved log; an empty one for nothing saved or a save that cannot be read. */
	public static AgilityLog read(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return new AgilityLog();
		}
		try
		{
			AgilityLog log = gson.fromJson(json, AgilityLog.class);
			return log == null ? new AgilityLog() : log.repair();
		}
		catch (JsonParseException | IllegalStateException | ClassCastException e)
		{
			return new AgilityLog();
		}
	}

	/** Mends what an old or hand-edited save may leave null. */
	private AgilityLog repair()
	{
		if (courses == null)
		{
			courses = new LinkedHashMap<>();
		}
		courses.values().removeIf(c -> c == null);
		courses.keySet().removeIf(k -> k == null || course(k) == null);
		if (graceful == null)
		{
			graceful = new ArrayList<>();
		}
		return this;
	}

	private static Rooftop course(String key)
	{
		try
		{
			return Rooftop.valueOf(key);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	/** The record of one course, or null if it has never been run. */
	public CourseLog get(Rooftop course)
	{
		return courses.get(course.name());
	}

	/** Every course run, in the courses' own order. */
	public Map<Rooftop, CourseLog> all()
	{
		Map<Rooftop, CourseLog> out = new LinkedHashMap<>();
		for (Rooftop r : Rooftop.values())
		{
			CourseLog c = courses.get(r.name());
			if (c != null)
			{
				out.put(r, c);
			}
		}
		return out;
	}

	private CourseLog getOrMake(Rooftop course)
	{
		return courses.computeIfAbsent(course.name(), k -> new CourseLog());
	}

	/**
	 * Files a timed lap.
	 *
	 * @return true when it is a new best lap on the course
	 */
	public boolean lap(Lap lap, long now)
	{
		CourseLog c = getOrMake(lap.getCourse());
		c.laps++;
		c.ticks += lap.getTicks();
		c.xp += Math.max(0, lap.getXp());
		c.lastAt = now;
		if (c.recent == null)
		{
			c.recent = new ArrayList<>();
		}
		c.recent.add(lap.getTicks());
		while (c.recent.size() > RECENT)
		{
			c.recent.remove(0);
		}
		if (c.bestTicks <= 0 || lap.getTicks() < c.bestTicks)
		{
			boolean had = c.bestTicks > 0;
			c.bestTicks = lap.getTicks();
			c.bestSplits = lap.getSplits();
			c.bestAt = now;
			return had;
		}
		return false;
	}

	/** A mark of grace picked up on a course. */
	public void mark(Rooftop course, int count)
	{
		if (count > 0)
		{
			getOrMake(course).marks += count;
		}
	}

	public Set<Graceful> getGraceful()
	{
		Set<Graceful> out = EnumSet.noneOf(Graceful.class);
		for (String name : graceful)
		{
			for (Graceful g : Graceful.values())
			{
				if (g.name().equals(name))
				{
					out.add(g);
				}
			}
		}
		return out;
	}

	/**
	 * Notes Graceful pieces seen; a piece once seen stays owned, since one put in a costume room or
	 * lent out has not been lost. Returns true when a new one was seen.
	 */
	public boolean sawGraceful(Set<Graceful> seen)
	{
		boolean added = false;
		for (Graceful g : seen)
		{
			if (!graceful.contains(g.name()))
			{
				graceful.add(g.name());
				added = true;
			}
		}
		return added;
	}

	public int getBankMarks()
	{
		return bankMarks;
	}

	/** Returns true when the count changed. */
	public boolean bankMarks(int count)
	{
		int c = Math.max(0, count);
		boolean changed = c != bankMarks;
		bankMarks = c;
		return changed;
	}

	public int getLevel()
	{
		return level;
	}

	/** Returns true when the level changed. */
	public boolean level(int value)
	{
		if (value <= 0 || value == level)
		{
			return false;
		}
		level = value;
		return true;
	}

	/** A copy, for the panel to read while the client thread writes the original. */
	public AgilityLog copy(Gson gson)
	{
		return read(gson, gson.toJson(this));
	}

	@Override
	public String toString()
	{
		return "AgilityLog" + courses.keySet() + Arrays.toString(graceful.toArray());
	}
}
