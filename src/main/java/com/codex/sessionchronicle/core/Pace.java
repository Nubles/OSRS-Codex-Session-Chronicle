package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How a session's pace compares with the player's own earlier sessions of the same kind: the
 * main skill's experience an hour against other sessions spent mainly on that skill, the main
 * monster's kills an hour against other trips to it, and loot an hour against those same trips.
 * Rates from sessions this short or shorter mean little, so they are left out.
 */
public final class Pace
{
	/** A session must be at least this long for its rates to stand as usual or as a record. */
	public static final long MIN_PLAYED_MS = 15 * 60_000L;

	public enum Kind
	{
		/** Experience an hour in one skill. */
		SKILL,
		/** Kills or clears an hour of one monster or raid. */
		KILLS,
		/** Loot an hour on trips to one monster, or sessions of one skill. */
		LOOT
	}

	/** One rate, set beside the player's usual and best for the same activity. */
	public static final class Line
	{
		private final Kind kind;
		private final String name;
		private final long rate;
		private final long usual;
		private final long best;
		private final long bestAt;
		private final int samples;

		Line(Kind kind, String name, long rate, long usual, long best, long bestAt, int samples)
		{
			this.kind = kind;
			this.name = name;
			this.rate = rate;
			this.usual = usual;
			this.best = best;
			this.bestAt = bestAt;
			this.samples = samples;
		}

		public Kind getKind()
		{
			return kind;
		}

		/** The skill or monster, such as "Mining" or "Vorkath". */
		public String getName()
		{
			return name;
		}

		/** This session's rate an hour. */
		public long getRate()
		{
			return rate;
		}

		/** The middle rate of the earlier sessions, or 0 with none. */
		public long getUsual()
		{
			return usual;
		}

		/** The best earlier rate, or 0 with none. */
		public long getBest()
		{
			return best;
		}

		/** When the session with the best rate began. */
		public long getBestAt()
		{
			return bestAt;
		}

		/** How many earlier sessions the usual and best come from. */
		public int getSamples()
		{
			return samples;
		}

		/** This rate against the usual: 0.12 is 12% above it. */
		public double vsUsual()
		{
			return usual <= 0 ? 0 : rate / (double) usual - 1;
		}

		/** Faster than every earlier session of the same kind. */
		public boolean isRecord()
		{
			return samples > 0 && rate > best;
		}
	}

	private Pace()
	{
	}

	/**
	 * The session's pace lines, main skill first, then its main monster and that monster's loot.
	 *
	 * @param journal earlier sessions; any that began at or after this one are ignored
	 */
	public static List<Line> of(Session s, List<Session> journal)
	{
		if (s == null || s.playedMs < Summary.MIN_RATE_MS)
		{
			return Collections.emptyList();
		}
		List<Line> lines = new ArrayList<>();
		Session.SkillLine skill = topSkill(s);
		Session.KillLine kill = topKill(s);
		// A slayer trip's main skill is a combat one; its monster says more about the session.
		if (skill != null && (kill == null || !isCombat(skill.getSkill())))
		{
			lines.add(line(Kind.SKILL, skill.getSkill(), s, journal));
		}
		if (kill != null)
		{
			lines.add(line(Kind.KILLS, kill.getName(), s, journal));
		}
		if (s.lootValue() > 0)
		{
			lines.add(line(Kind.LOOT, kill != null ? kill.getName() : skill != null ? skill.getSkill() : null, s, journal));
		}
		lines.removeIf(l -> l == null || l.getRate() <= 0);
		return lines;
	}

	private static Line line(Kind kind, String name, Session s, List<Session> journal)
	{
		if (name == null)
		{
			return null;
		}
		List<Long> rates = new ArrayList<>();
		long best = 0;
		long bestAt = 0;
		for (Session past : journal)
		{
			if (past == null || past.startedAt >= s.startedAt || past.playedMs < MIN_PLAYED_MS || !sameActivity(kind, name, past))
			{
				continue;
			}
			long rate = rate(kind, name, past);
			if (rate <= 0)
			{
				continue;
			}
			rates.add(rate);
			if (rate > best)
			{
				best = rate;
				bestAt = past.startedAt;
			}
		}
		Collections.sort(rates);
		long usual = rates.isEmpty() ? 0 : rates.size() % 2 == 1 ? rates.get(rates.size() / 2)
			: (rates.get(rates.size() / 2 - 1) + rates.get(rates.size() / 2)) / 2;
		return new Line(kind, name, rate(kind, name, s), usual, best, bestAt, rates.size());
	}

	/** Whether an earlier session was spent on the same thing, so its rate is a fair comparison. */
	static boolean sameActivity(Kind kind, String name, Session past)
	{
		Session.SkillLine skill = topSkill(past);
		Session.KillLine kill = topKill(past);
		switch (kind)
		{
			case SKILL:
				return skill != null && skill.getSkill().equals(name);
			case KILLS:
				return kill != null && Chronicle.key(kill.getName()).equals(Chronicle.key(name));
			default:
				return kill != null ? Chronicle.key(kill.getName()).equals(Chronicle.key(name))
					: skill != null && skill.getSkill().equals(name);
		}
	}

	static long rate(Kind kind, String name, Session s)
	{
		long amount;
		switch (kind)
		{
			case SKILL:
				Session.SkillLine line = s.skills.get(name);
				amount = line == null ? 0 : line.gained();
				break;
			case KILLS:
				Session.KillLine kills = s.kills.get(Chronicle.key(name));
				amount = kills == null ? 0 : kills.count();
				break;
			default:
				amount = s.lootValue();
		}
		return s.playedMs <= 0 ? 0 : Math.round(amount * 3_600_000.0 / s.playedMs);
	}

	/** The skill that gained the most, or null. */
	static Session.SkillLine topSkill(Session s)
	{
		Session.SkillLine top = null;
		for (Session.SkillLine line : s.skills.values())
		{
			if (line.gained() > 0 && (top == null || line.gained() > top.gained()))
			{
				top = line;
			}
		}
		return top;
	}

	/** The monster or raid with the most kills, or null. */
	static Session.KillLine topKill(Session s)
	{
		Session.KillLine top = null;
		for (Session.KillLine line : s.kills.values())
		{
			if (line.count() > 0 && (top == null || line.count() > top.count()))
			{
				top = line;
			}
		}
		return top;
	}

	static boolean isCombat(String skill)
	{
		switch (skill)
		{
			case "Attack":
			case "Strength":
			case "Defence":
			case "Ranged":
			case "Magic":
			case "Hitpoints":
			case "Prayer":
			case "Slayer":
				return true;
			default:
				return false;
		}
	}
}
