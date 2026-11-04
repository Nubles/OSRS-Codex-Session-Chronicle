package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Times rooftop laps in game ticks. A lap runs from one lap count message to the next on the same
 * course, start to start, so the walk or teleport back to the first obstacle is in it, as it is in
 * any rate. Each Agility experience drop inside the lap is an obstacle, and its tick is kept, so the
 * lap under way can be set against the best lap obstacle by obstacle.
 *
 * Lap times are counted in ticks rather than by the wall clock, so a slow frame or a stalled client
 * never makes a lap look faster or slower than the game ran it.
 */
public final class LapClock
{
	/** A "lap" longer than this was a break or a bank trip, not a lap; it is not filed. Five minutes. */
	public static final int MAX_LAP_TICKS = 500;
	/** Shorter than any course can be run; such a lap is a message read twice. Twenty seconds. */
	public static final int MIN_LAP_TICKS = 33;
	/** Ticks off the course before the lap under way is dropped. Thirty seconds. */
	public static final int AWAY_TICKS = 50;

	private Rooftop course;
	private int startTick = -1;
	private final List<Integer> splits = new ArrayList<>();
	private long xp;
	private int awayTicks;

	/**
	 * A lap count message for a course at a tick.
	 *
	 * @return the lap it ends, when one was being timed on the same course and ran a believable time; null otherwise
	 */
	public AgilityLog.Lap lapDone(Rooftop done, int tick)
	{
		AgilityLog.Lap lap = null;
		if (done != null && done == course && startTick >= 0)
		{
			int ticks = tick - startTick;
			if (ticks >= MIN_LAP_TICKS && ticks <= MAX_LAP_TICKS)
			{
				int[] at = new int[splits.size()];
				for (int i = 0; i < at.length; i++)
				{
					at[i] = splits.get(i);
				}
				lap = new AgilityLog.Lap(done, ticks, at, xp);
			}
		}
		course = done;
		startTick = done == null ? -1 : tick;
		splits.clear();
		xp = 0;
		awayTicks = 0;
		return lap;
	}

	/**
	 * Agility experience gained at a tick. The last obstacle's experience can arrive on the tick of the
	 * lap message, after it; it belongs to the lap just ended, which already has its time, so it is not
	 * counted against the new one.
	 */
	public void xp(int gained, int tick)
	{
		if (startTick < 0 || gained <= 0)
		{
			return;
		}
		if (tick <= startTick)
		{
			return;
		}
		splits.add(tick - startTick);
		xp += gained;
	}

	/**
	 * Called each tick with the course whose map region the player stands in, or null. A lap is dropped
	 * once the player has been elsewhere for a while, and when the lap has run far too long.
	 */
	public void tick(Rooftop here, int tick)
	{
		if (startTick < 0)
		{
			return;
		}
		if (here == course)
		{
			awayTicks = 0;
		}
		else if (++awayTicks > AWAY_TICKS)
		{
			reset();
			return;
		}
		if (tick - startTick > MAX_LAP_TICKS)
		{
			reset();
		}
	}

	/** Forgets the lap under way: after a hop, a logout or a death. */
	public void reset()
	{
		course = null;
		startTick = -1;
		splits.clear();
		xp = 0;
		awayTicks = 0;
	}

	public boolean isRunning()
	{
		return startTick >= 0;
	}

	public Rooftop getCourse()
	{
		return course;
	}

	/** The lap under way at a tick, set against the best lap on its course; null when no lap is being timed. */
	public Split split(int tick, AgilityLog.CourseLog record)
	{
		if (startTick < 0 || course == null)
		{
			return null;
		}
		int[] at = new int[splits.size()];
		for (int i = 0; i < at.length; i++)
		{
			at[i] = splits.get(i);
		}
		return new Split(course, Math.max(0, tick - startTick), at, record == null ? 0 : record.getBestTicks(),
			record == null ? new int[0] : record.getBestSplits());
	}

	/** A lap under way against the ghost of the best lap. */
	public static final class Split
	{
		private final Rooftop course;
		private final int elapsed;
		private final int[] splits;
		private final int bestTicks;
		private final int[] bestSplits;

		public Split(Rooftop course, int elapsed, int[] splits, int bestTicks, int[] bestSplits)
		{
			this.course = course;
			this.elapsed = elapsed;
			this.splits = splits.clone();
			this.bestTicks = bestTicks;
			this.bestSplits = bestSplits.clone();
		}

		public Rooftop getCourse()
		{
			return course;
		}

		/** Ticks since the lap began. */
		public int getElapsed()
		{
			return elapsed;
		}

		/** Obstacles crossed so far in this lap. */
		public int getObstacles()
		{
			return splits.length;
		}

		/** Obstacles in the best lap, or 0 with no best lap yet. */
		public int getGhostObstacles()
		{
			return bestSplits.length;
		}

		public int getBestTicks()
		{
			return bestTicks;
		}

		public boolean hasGhost()
		{
			return bestTicks > 0;
		}

		/** Obstacles the ghost of the best lap had crossed at this moment of its lap. */
		public int ghostObstaclesNow()
		{
			int n = 0;
			for (int at : bestSplits)
			{
				if (at <= elapsed)
				{
					n++;
				}
			}
			return n;
		}

		/**
		 * Ticks behind the best lap at the last obstacle crossed (below nothing is ahead), or null when it
		 * cannot be told: no best lap, no obstacle yet, or more obstacles than the best lap had (a fall
		 * that sent the player round a different way).
		 */
		public Integer delta()
		{
			int n = splits.length;
			if (!hasGhost() || n == 0 || n > bestSplits.length)
			{
				return null;
			}
			return splits[n - 1] - bestSplits[n - 1];
		}

		/** Ticks behind the best lap's whole time if the rest goes as the best lap did; null when unknown. */
		public Integer projected()
		{
			Integer d = delta();
			if (d != null)
			{
				return bestTicks + d;
			}
			// Before the first obstacle, the lap is already behind once it has run longer than the best lap's first split.
			if (hasGhost() && splits.length == 0 && bestSplits.length > 0 && elapsed > bestSplits[0])
			{
				return bestTicks + elapsed - bestSplits[0];
			}
			return null;
		}
	}

	/** "43.8s" or "1:04.2" for ticks of 0.6 seconds. */
	public static String time(int ticks)
	{
		int tenths = ticks * 6;
		int seconds = tenths / 10;
		if (seconds < 60)
		{
			return seconds + "." + tenths % 10 + "s";
		}
		return seconds / 60 + ":" + String.format(java.util.Locale.ENGLISH, "%02d", seconds % 60) + "." + tenths % 10;
	}

	/** "+1.2s" or "-0.6s" or "level" for a gap in ticks. */
	public static String gap(int ticks)
	{
		if (ticks == 0)
		{
			return "level";
		}
		int tenths = Math.abs(ticks) * 6;
		return (ticks > 0 ? "+" : "-") + tenths / 10 + "." + tenths % 10 + "s";
	}
}
