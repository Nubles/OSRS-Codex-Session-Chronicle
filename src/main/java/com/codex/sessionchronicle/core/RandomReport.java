package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the panel shows of the random events: the player's own events an hour set against the wiki's
 * timer, how their events ended, when the next one could come by that timer (an estimate), recent
 * events, events by activity, and each costume's pieces owned and still to come. Worked out on the
 * client thread from a copy of the log, then only read.
 */
public final class RandomReport
{
	/** Play needed before the player's own rate is shown as theirs (two hours). */
	public static final long OWN_RATE_MS = 2 * 3_600_000L;
	/** Play an activity needs, without an event, to be listed (twenty minutes). */
	static final long ACTIVITY_MS = 20 * 60_000L;

	/** One costume: its pieces owned, and the event that still gives the rest. */
	public static final class CostumeLine
	{
		private final RandomEvents.Costume costume;
		private final List<RandomEvents.Piece> owned = new ArrayList<>();
		private final List<RandomEvents.Piece> missing = new ArrayList<>();
		private final int completed;

		CostumeLine(RandomEvents.Costume costume, Set<Integer> pieces, int completed)
		{
			this.costume = costume;
			for (RandomEvents.Piece p : costume.getPieces())
			{
				(pieces.contains(p.getItemId()) ? owned : missing).add(p);
			}
			this.completed = completed;
		}

		public RandomEvents.Costume getCostume()
		{
			return costume;
		}

		public List<RandomEvents.Piece> getOwned()
		{
			return Collections.unmodifiableList(owned);
		}

		public List<RandomEvents.Piece> getMissing()
		{
			return Collections.unmodifiableList(missing);
		}

		public boolean isOwned(RandomEvents.Piece p)
		{
			return owned.contains(p);
		}

		public boolean isDone()
		{
			return missing.isEmpty();
		}

		/** Times the event was taken part in, as logged here. */
		public int getCompleted()
		{
			return completed;
		}

		/**
		 * Completions still to go by the wiki's count for the whole costume, at least one a piece
		 * missing; 0 when every piece is owned. Some of the wiki's completions unlock emotes, which are
		 * not seen here, so this is an estimate.
		 */
		public int completionsLeft()
		{
			if (missing.isEmpty())
			{
				return 0;
			}
			int share = (int) Math.ceil(costume.getCompletions() * missing.size() / (double) costume.getPieces().length);
			return Math.max(1, share);
		}
	}

	private final RandomLog log;
	private final long now;
	private final RandomEvents.Event current;
	private final List<CostumeLine> costumes = new ArrayList<>();

	private RandomReport(RandomLog log, long now, RandomEvents.Event current)
	{
		this.log = log;
		this.now = now;
		this.current = current;
		Set<Integer> pieces = log.getPieces();
		for (RandomEvents.Costume c : RandomEvents.Costume.values())
		{
			costumes.add(new CostumeLine(c, pieces, log.kind(c.getEvent()).getAccepted()));
		}
	}

	/**
	 * @param log a copy of the account's log, not written after
	 * @param current the event under way, or null
	 */
	public static RandomReport of(RandomLog log, long now, RandomEvents.Event current)
	{
		return new RandomReport(log == null ? new RandomLog() : log, now, current);
	}

	public RandomLog getLog()
	{
		return log;
	}

	public long getNow()
	{
		return now;
	}

	public RandomEvents.Event getCurrent()
	{
		return current;
	}

	public int getEvents()
	{
		return log.getTotal().getSeen();
	}

	public long getWatchedMs()
	{
		return log.getWatchedMs();
	}

	/** Events an hour of play watched; 0 before any play. */
	public double perHour()
	{
		long ms = log.getWatchedMs();
		return ms <= 0 ? 0 : log.getTotal().getSeen() * 3_600_000.0 / ms;
	}

	/** Play between the player's events, on average; -1 before an event. */
	public long msPerEvent()
	{
		int n = log.getTotal().getSeen();
		return n == 0 ? -1 : log.getWatchedMs() / n;
	}

	/** Whether there is enough play behind the player's own rate to show it as theirs. */
	public boolean isOwnRate()
	{
		return log.getWatchedMs() >= OWN_RATE_MS && log.getTotal().getSeen() > 0;
	}

	/** The wiki's timer as events an hour, lowest and highest: one every two hours to one an hour. */
	public static double wikiLow()
	{
		return 3_600_000.0 / RandomEvents.TIMER_MAX_MS;
	}

	public static double wikiHigh()
	{
		return 3_600_000.0 / RandomEvents.TIMER_MIN_MS;
	}

	/**
	 * Where the player's rate sits against the wiki's timer: -1 under one every two hours, 0 within,
	 * 1 over one an hour. Banks, boss areas and minigames hold events back, so under is usual for
	 * those who spend time there.
	 */
	public int versusWiki()
	{
		double r = perHour();
		return r < wikiLow() ? -1 : r > wikiHigh() ? 1 : 0;
	}

	/** When the next event could first come by the wiki's timer (the last one plus an hour), or 0 before any. */
	public long nextFrom()
	{
		return log.getLastAt() <= 0 ? 0 : log.getLastAt() + RandomEvents.TIMER_MIN_MS;
	}

	/** When it falls due at the latest by the wiki's timer (the last one plus two hours), or 0 before any. */
	public long nextBy()
	{
		return log.getLastAt() <= 0 ? 0 : log.getLastAt() + RandomEvents.TIMER_MAX_MS;
	}

	public List<RandomLog.Seen> recent(int max)
	{
		List<RandomLog.Seen> r = log.recent();
		return r.subList(0, Math.min(max, r.size()));
	}

	public List<Map.Entry<String, RandomLog.Tally>> activities(int max)
	{
		List<Map.Entry<String, RandomLog.Tally>> a = log.activities(ACTIVITY_MS);
		return a.subList(0, Math.min(max, a.size()));
	}

	public List<CostumeLine> getCostumes()
	{
		return Collections.unmodifiableList(costumes);
	}

	/** Costume pieces owned, of all of them. */
	public int piecesOwned()
	{
		int n = 0;
		for (CostumeLine c : costumes)
		{
			n += c.getOwned().size();
		}
		return n;
	}

	public int piecesAll()
	{
		int n = 0;
		for (CostumeLine c : costumes)
		{
			n += c.getCostume().getPieces().length;
		}
		return n;
	}

	/** The events that still give a piece the player lacks, in the wiki's order. */
	public List<RandomEvents.Event> eventsWithPieces()
	{
		List<RandomEvents.Event> out = new ArrayList<>();
		for (CostumeLine c : costumes)
		{
			if (!c.isDone())
			{
				out.add(c.getCostume().getEvent());
			}
		}
		return out;
	}
}
