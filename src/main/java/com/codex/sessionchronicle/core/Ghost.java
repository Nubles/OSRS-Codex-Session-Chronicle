package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ghost pace: the session under way raced, minute for minute of play, against the player's own
 * best earlier session of the same main activity (the same monster, or the same skill when no
 * monster). "Best" is the one with the most experience an hour, so the ghost stays the same rival
 * all session. At each moment the session's experience is set against what the ghost had at that
 * same moment of play, read from the ghost's own minute-by-minute track; a session filed before
 * tracks were kept is raced at its average pace instead, and says so.
 */
public final class Ghost
{
	/** Play time before a ghost is worth showing: the first minute says nothing yet. */
	public static final long MIN_ELAPSED_MS = 2 * 60_000L;
	/** Sessions shorter than this are no rival, as they are no record in Pace. */
	static final long MIN_GHOST_MS = Pace.MIN_PLAYED_MS;
	/** The most points the trail of ahead and behind keeps; a long session is sampled evenly. */
	static final int TRAIL = 60;

	private final String activity;
	private final boolean kills;
	private final long ghostStartedAt;
	private final long ghostPlayedMs;
	private final long ghostTotalXp;
	private final long ghostRate;
	private final boolean estimated;
	private final long elapsed;
	private final long xpNow;
	private final long ghostXp;
	private final int killsNow;
	private final long ghostKills;
	private final int rivals;
	/** The trail of the gap: {play ms, experience ahead (or behind, below nothing)}, oldest first. */
	private final List<long[]> trail;

	private Ghost(String activity, boolean kills, Session ghost, int rivals, Session s, List<long[]> trail)
	{
		this.activity = activity;
		this.kills = kills;
		this.rivals = rivals;
		ghostStartedAt = ghost.startedAt;
		ghostPlayedMs = ghost.playedMs;
		ghostTotalXp = ghost.totalXp();
		ghostRate = rate(ghost);
		estimated = ghost.track == null;
		elapsed = s.playedMs;
		xpNow = s.totalXp();
		ghostXp = xpAt(ghost, elapsed);
		killsNow = s.totalKills();
		ghostKills = kills ? killsAt(ghost, elapsed) : 0;
		this.trail = Collections.unmodifiableList(trail);
	}

	/**
	 * The ghost for a session under way, or null: too early, nothing done yet, or no earlier session
	 * of the same activity long enough to race.
	 *
	 * @param journal finished sessions; only those that began before this one are raced
	 */
	public static Ghost of(Session s, List<Session> journal)
	{
		if (s == null || s.playedMs < MIN_ELAPSED_MS || journal == null)
		{
			return null;
		}
		String activity = Pace.activity(s);
		if (activity == null)
		{
			return null;
		}
		Session best = null;
		int rivals = 0;
		for (Session past : journal)
		{
			if (!comparable(s, activity, past))
			{
				continue;
			}
			rivals++;
			// The fastest; on a tie the latest, whose track is likelier kept.
			if (best == null || rate(past) > rate(best) || rate(past) == rate(best) && past.startedAt > best.startedAt)
			{
				best = past;
			}
		}
		if (best == null)
		{
			return null;
		}
		boolean kills = Pace.mainKill(s) != null;
		return new Ghost(activity, kills, best, rivals, s, trail(s, best));
	}

	/** Whether an earlier session is a fair rival: before this one, long enough, spent on the same thing, with experience to race. */
	static boolean comparable(Session s, String activity, Session past)
	{
		if (past == null || past.startedAt >= s.startedAt || past.playedMs < MIN_GHOST_MS || past.totalXp() <= 0)
		{
			return false;
		}
		String theirs = Pace.activity(past);
		return theirs != null && Chronicle.key(theirs).equals(Chronicle.key(activity));
	}

	/** Experience an hour over a whole session. */
	static long rate(Session s)
	{
		return s.playedMs <= 0 ? 0 : Math.round(s.totalXp() * 3_600_000.0 / s.playedMs);
	}

	/**
	 * The experience a session had at a moment of play: from its track, or, for a session filed before
	 * tracks were kept, at its average pace. Past its end it stays where it finished.
	 */
	static long xpAt(Session past, long ms)
	{
		long end = past.totalXp();
		if (ms >= past.playedMs)
		{
			return end;
		}
		if (past.track != null && past.track.size() > 0)
		{
			return Math.min(end, past.track.xpAt(ms, past.playedMs, end));
		}
		return past.playedMs <= 0 ? 0 : Math.round(end * (double) ms / past.playedMs);
	}

	/** Kills a session had at a moment of play, read as {@link #xpAt}. */
	static long killsAt(Session past, long ms)
	{
		long end = past.totalKills();
		if (ms >= past.playedMs)
		{
			return end;
		}
		if (past.track != null && past.track.size() > 0)
		{
			return Math.min(end, past.track.killsAt(ms, past.playedMs, end));
		}
		return past.playedMs <= 0 ? 0 : (long) Math.floor(end * (double) ms / past.playedMs);
	}

	/**
	 * The gap minute by minute: at each of the session's own marks, its experience less the ghost's at
	 * the same moment, and the gap now at the end. Sampled down evenly to {@link #TRAIL} points.
	 */
	static List<long[]> trail(Session s, Session ghost)
	{
		List<long[]> all = new ArrayList<>();
		all.add(new long[]{0, 0});
		Track t = s.track;
		if (t != null)
		{
			long step = t.s * Track.MINUTE;
			for (int i = 0; i < t.x.size(); i++)
			{
				long ms = (i + 1) * step;
				if (ms >= s.playedMs)
				{
					break;
				}
				all.add(new long[]{ms, t.x.get(i) - xpAt(ghost, ms)});
			}
		}
		all.add(new long[]{s.playedMs, s.totalXp() - xpAt(ghost, s.playedMs)});
		if (all.size() <= TRAIL)
		{
			return all;
		}
		List<long[]> kept = new ArrayList<>();
		for (int i = 0; i < TRAIL; i++)
		{
			kept.add(all.get((int) Math.round(i * (all.size() - 1) / (double) (TRAIL - 1))));
		}
		return kept;
	}

	/** The monster or skill raced, as the session names it: "Zulrah", "Mining". */
	public String getActivity()
	{
		return activity;
	}

	/** Whether the race is a monster's, so the kills are set side by side too. */
	public boolean hasKills()
	{
		return kills;
	}

	/** When the ghost's session began. */
	public long getGhostStartedAt()
	{
		return ghostStartedAt;
	}

	/** How long the ghost's session lasted, in play time. */
	public long getGhostPlayedMs()
	{
		return ghostPlayedMs;
	}

	public long getGhostTotalXp()
	{
		return ghostTotalXp;
	}

	/** The ghost's experience an hour over its whole session. */
	public long getGhostRate()
	{
		return ghostRate;
	}

	/** True when the ghost was filed before tracks were kept, so it is raced at its average pace. */
	public boolean isEstimated()
	{
		return estimated;
	}

	/** Earlier sessions of the same activity it was chosen from. */
	public int getRivals()
	{
		return rivals;
	}

	/** Play time so far: the moment the two are set side by side. */
	public long getElapsed()
	{
		return elapsed;
	}

	public long getXpNow()
	{
		return xpNow;
	}

	/** The ghost's experience at the same moment of play. */
	public long getGhostXp()
	{
		return ghostXp;
	}

	/** Experience ahead of the ghost; below nothing, behind it. */
	public long getLead()
	{
		return xpNow - ghostXp;
	}

	/** Kills ahead of the ghost at the same moment; below nothing, behind. 0 when the race is not a monster's. */
	public long getKillLead()
	{
		return kills ? killsNow - ghostKills : 0;
	}

	/** Whether the session has played on past the ghost's whole session. */
	public boolean isPastGhost()
	{
		return elapsed >= ghostPlayedMs;
	}

	/** The gap minute by minute: {play ms, experience ahead}, oldest first, starting at nothing. */
	public List<long[]> getTrail()
	{
		return trail;
	}

	/** The gap as a share of the ghost's experience at that moment: 0.12 is 12% ahead. 0 with nothing to share. */
	public double leadShare()
	{
		return ghostXp <= 0 ? 0 : getLead() / (double) ghostXp;
	}

	/** Within this share of the ghost, the two are level. */
	static final double LEVEL = 0.01;

	/** Ahead, behind or level, by more than a hair. */
	public int standing()
	{
		long lead = getLead();
		if (Math.abs(lead) <= Math.max(1, ghostXp * LEVEL))
		{
			return 0;
		}
		return lead > 0 ? 1 : -1;
	}

	/** A figure by the suite's rule: in full under 10,000, then whole k, M to one place, B to two. */
	static String figure(long n)
	{
		return n < 10_000 ? String.format(java.util.Locale.ENGLISH, "%,d", n) : Chronicle.coins(n);
	}

	/** "40:00" under an hour, "1:05:00" from it: the moment of play, as a race clock reads. */
	public static String clock(long ms)
	{
		long seconds = Math.max(0, ms) / 1000;
		long h = seconds / 3600;
		long m = seconds / 60 % 60;
		long sec = seconds % 60;
		return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(java.util.Locale.ROOT, "%d:%02d", m, sec);
	}

	/**
	 * The race in a line: "+12k xp ahead of your best Zulrah session at 40:00", "3,400 xp behind ...",
	 * "Level with ...", or, past the ghost's end, "Past your best Zulrah session's 1:12:00, by 30k xp".
	 */
	public String headline()
	{
		String whose = "your best " + activity + " session";
		if (isPastGhost())
		{
			long lead = getLead();
			return lead >= 0 ? "Past " + whose + ", " + figure(lead) + " xp on its whole " + clock(ghostPlayedMs)
				: figure(-lead) + " xp short of " + whose + "'s whole " + clock(ghostPlayedMs);
		}
		switch (standing())
		{
			case 1:
				return "+" + figure(getLead()) + " xp ahead of " + whose + " at " + clock(elapsed);
			case -1:
				return figure(-getLead()) + " xp behind " + whose + " at " + clock(elapsed);
			default:
				return "Level with " + whose + " at " + clock(elapsed);
		}
	}
}
