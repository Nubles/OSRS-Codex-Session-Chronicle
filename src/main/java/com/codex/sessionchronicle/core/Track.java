package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.List;

/**
 * How a session went, minute by minute of play: the experience and kills it had come to at each
 * mark. It is what a later session races as its ghost. A live session marks every minute; a long
 * one, and every session filed in the journal, keeps fewer marks, further apart, so a day's grind
 * stays small to save.
 */
public class Track
{
	/** Play time between the marks of a live session. */
	static final long MINUTE = 60_000L;
	/** The most marks kept; past it, every other one goes and the marks stand twice as far apart. */
	static final int MAX_MARKS = 360;
	/** Minutes between the marks of a session filed in the journal (at least). */
	static final int FILED_STEP = 4;

	/** Minutes of play between marks. */
	int s = 1;
	/** The session's experience at each mark: the first is at s minutes, the next at 2s and so on. */
	List<Long> x = new ArrayList<>();
	/** The session's kills at each mark. */
	List<Integer> k = new ArrayList<>();

	Track()
	{
	}

	/** Minutes of play between marks. */
	public int getStep()
	{
		return s;
	}

	/** Marks kept. */
	public int size()
	{
		return x.size();
	}

	/** Play time of the next mark due. */
	long nextMarkMs()
	{
		return (x.size() + 1L) * s * MINUTE;
	}

	/**
	 * Marks every mark the play time has passed with where the session stands now. Marks are made
	 * once a game tick at the most, so the totals are those of the moment, for every mark passed.
	 */
	void mark(long playedMs, long xp, int kills)
	{
		while (playedMs >= nextMarkMs())
		{
			x.add(xp);
			k.add(kills);
			if (x.size() > MAX_MARKS)
			{
				thin();
			}
		}
	}

	/** Keeps every other mark (those at 2s, 4s...), twice as far apart. */
	void thin()
	{
		List<Long> keptX = new ArrayList<>();
		List<Integer> keptK = new ArrayList<>();
		for (int i = 1; i < x.size(); i += 2)
		{
			keptX.add(x.get(i));
			keptK.add(i < k.size() ? k.get(i) : 0);
		}
		x = keptX;
		k = keptK;
		s *= 2;
	}

	/** A copy for the journal, with marks at least {@link #FILED_STEP} minutes apart. */
	Track filed()
	{
		Track t = new Track();
		t.s = s;
		t.x = new ArrayList<>(x);
		t.k = new ArrayList<>(k);
		while (t.s < FILED_STEP)
		{
			t.thin();
		}
		return t;
	}

	/** Mends a track read back from storage; null when nothing of it can be used. */
	Track repair()
	{
		if (x == null || s <= 0 || s > 24 * 60)
		{
			return null;
		}
		x.removeIf(v -> v == null);
		if (k == null || k.size() != x.size())
		{
			k = new ArrayList<>();
			for (int i = 0; i < x.size(); i++)
			{
				k.add(0);
			}
		}
		k.replaceAll(v -> v == null || v < 0 ? 0 : v);
		// The figures can only rise; one that falls is damage, and is held to the one before.
		long last = 0;
		for (int i = 0; i < x.size(); i++)
		{
			long v = Math.max(last, Math.max(0, x.get(i)));
			x.set(i, v);
			last = v;
		}
		while (x.size() > MAX_MARKS)
		{
			thin();
		}
		return this;
	}

	/**
	 * Experience at a moment of play, read between the marks in a straight line from nothing at the
	 * start; past the last mark it runs on to the session's end, where it stood at {@code endXp}.
	 */
	long xpAt(long ms, long endMs, long endXp)
	{
		return at(ms, endMs, endXp, true);
	}

	/** Kills at a moment of play, read as {@link #xpAt}. */
	long killsAt(long ms, long endMs, long endKills)
	{
		return at(ms, endMs, endKills, false);
	}

	private long at(long ms, long endMs, long end, boolean xp)
	{
		if (ms <= 0)
		{
			return 0;
		}
		long step = s * MINUTE;
		long prevT = 0;
		long prevV = 0;
		for (int i = 0; i < x.size(); i++)
		{
			long t = (i + 1) * step;
			long v = xp ? x.get(i) : k.get(i);
			if (ms <= t)
			{
				return lerp(prevT, prevV, t, v, ms);
			}
			prevT = t;
			prevV = v;
		}
		if (ms >= endMs || endMs <= prevT)
		{
			return Math.max(prevV, end);
		}
		return lerp(prevT, prevV, endMs, Math.max(prevV, end), ms);
	}

	static long lerp(long t0, long v0, long t1, long v1, long t)
	{
		if (t1 <= t0)
		{
			return v1;
		}
		return v0 + Math.round((v1 - v0) * (double) (t - t0) / (t1 - t0));
	}
}
