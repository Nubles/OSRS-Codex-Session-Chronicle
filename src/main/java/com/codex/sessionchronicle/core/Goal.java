package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What the player set out to do this session: so much experience, loot, kills or time. Kept
 * with the session, so it is saved, filed in the journal and told on the card with it.
 */
public class Goal
{
	public enum Kind
	{
		XP("Experience", "xp"),
		LOOT("Loot", "gp"),
		KILLS("Kills", "kills"),
		TIME("Time", "played");

		private final String label;
		private final String unit;

		Kind(String label, String unit)
		{
			this.label = label;
			this.unit = unit;
		}

		public String getLabel()
		{
			return label;
		}

		public String getUnit()
		{
			return unit;
		}
	}

	Kind kind;
	long target;
	long setAt;
	/** Play time into the session when it was reached, or 0 while it is still ahead. */
	long reachedMs;

	Goal()
	{
	}

	public Goal(Kind kind, long target, long setAt)
	{
		this.kind = kind;
		this.target = target;
		this.setAt = setAt;
	}

	public Kind getKind()
	{
		return kind == null ? Kind.XP : kind;
	}

	/** Experience, coins, kills, or milliseconds of play. */
	public long getTarget()
	{
		return target;
	}

	public boolean isReached()
	{
		return reachedMs > 0;
	}

	public long getReachedMs()
	{
		return reachedMs;
	}

	/** How far the session has come toward it. */
	public static long progress(Session s, Kind kind)
	{
		switch (kind)
		{
			case LOOT:
				return s.lootValue();
			case KILLS:
				return s.totalKills();
			case TIME:
				return s.playedMs;
			default:
				return s.totalXp();
		}
	}

	/** "500k xp", "5M loot", "50 kills", "2h played": the goal in a few words. */
	public String describe()
	{
		return describe(getKind(), target);
	}

	public static String describe(Kind kind, long amount)
	{
		switch (kind)
		{
			case LOOT:
				return Chronicle.mark(amount) + " loot";
			case KILLS:
				return amount + (amount == 1 ? " kill" : " kills");
			case TIME:
				long minutes = amount / 60_000;
				String hours = minutes / 60 + "h" + (minutes % 60 > 0 ? " " + minutes % 60 + "m" : "");
				return (minutes >= 60 ? hours : minutes + "m") + " played";
			default:
				return Chronicle.mark(amount) + " xp";
		}
	}

	/** A goal offered with one click, and why: "Usual", "Best", "Beat best", or none for a round number. */
	public static final class Preset
	{
		private final String label;
		private final long value;

		Preset(String label, long value)
		{
			this.label = label;
			this.value = value;
		}

		public String getLabel()
		{
			return label;
		}

		public long getValue()
		{
			return value;
		}
	}

	private static final long[][] LADDERS = {
		{50_000, 100_000, 250_000, 500_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000},
		{500_000, 1_000_000, 5_000_000, 10_000_000, 25_000_000, 50_000_000, 100_000_000, 250_000_000},
		{10, 25, 50, 100, 250, 500, 1_000},
		{30 * 60_000L, 60 * 60_000L, 120 * 60_000L, 180 * 60_000L, 240 * 60_000L, 360 * 60_000L},
	};

	/**
	 * Up to three goals worth offering, all still ahead of the session: the player's usual and
	 * best whole session from the journal and a little past their best, then round numbers.
	 */
	public static List<Preset> presets(Kind kind, Session current, List<Session> journal)
	{
		long done = current == null ? 0 : progress(current, kind);
		List<Long> totals = new ArrayList<>();
		for (Session s : journal)
		{
			long v = s == null || s.playedMs < Pace.MIN_PLAYED_MS ? 0 : progress(s, kind);
			if (v > 0)
			{
				totals.add(v);
			}
		}
		Collections.sort(totals);
		List<Preset> presets = new ArrayList<>();
		if (totals.size() >= 3)
		{
			long usual = round(kind, totals.get(totals.size() / 2));
			long best = round(kind, totals.get(totals.size() - 1));
			offer(presets, "Usual", usual, done);
			offer(presets, "Best", best, done);
			offer(presets, "Beat best", round(kind, Math.max(best + 1, Math.round(best * 1.1))), done);
		}
		for (long v : LADDERS[kind.ordinal()])
		{
			if (presets.size() >= 3)
			{
				break;
			}
			offer(presets, null, v, done);
		}
		presets.sort(Comparator.comparingLong(Preset::getValue));
		return presets;
	}

	private static void offer(List<Preset> presets, String label, long value, long done)
	{
		if (value <= done || presets.size() >= 3)
		{
			return;
		}
		for (Preset p : presets)
		{
			if (p.value == value)
			{
				return;
			}
		}
		presets.add(new Preset(label, value));
	}

	/** Two significant figures, or whole quarter hours for time, so a goal reads as a goal. */
	static long round(Kind kind, long value)
	{
		if (kind == Kind.TIME)
		{
			long quarter = 15 * 60_000L;
			return Math.max(quarter, (value + quarter - 1) / quarter * quarter);
		}
		if (value < 100)
		{
			return Math.max(1, value);
		}
		long scale = 1;
		while (value / scale >= 100)
		{
			scale *= 10;
		}
		return (value + scale - 1) / scale * scale;
	}

	/** A goal must aim at something; a saved one with nothing to reach is dropped. */
	boolean isValid()
	{
		return target > 0;
	}
}
