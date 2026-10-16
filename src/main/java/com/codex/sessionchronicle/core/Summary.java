package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A read-only telling of one session, ready for the panel and the card: totals, rates, the
 * skills, drops and kills in order of size, its moments and a headline.
 */
public final class Summary
{
	/** Rates are only shown once a session is long enough for them to mean something. */
	static final long MIN_RATE_MS = 5 * 60_000L;

	public enum State
	{
		LIVE,
		PAUSED,
		ENDED
	}

	private final State state;
	private final String player;
	private final long startedAt;
	private final long endedAt;
	private final long playedMs;
	private final long totalXp;
	private final long lootValue;
	private final int levels;
	private final int kills;
	private final int deaths;
	private final int otherDrops;
	private final List<Session.SkillLine> skills;
	private final List<Session.Drop> drops;
	private final List<Session.KillLine> killLines;
	private final List<Moment> moments;
	private final List<Moment> highlights;

	private Summary(Session s, State state)
	{
		this.state = state;
		player = s.player;
		startedAt = s.startedAt;
		endedAt = s.endedAt > 0 ? s.endedAt : s.lastSeenAt;
		playedMs = s.playedMs;
		totalXp = s.totalXp();
		lootValue = s.lootValue();
		levels = s.levelsGained();
		kills = s.totalKills();
		deaths = s.deaths;
		otherDrops = s.otherDrops;

		List<Session.SkillLine> skillList = new ArrayList<>();
		for (Session.SkillLine line : s.skills.values())
		{
			if (line.gained() > 0)
			{
				skillList.add(line);
			}
		}
		skillList.sort(Comparator.comparingLong(Session.SkillLine::gained).reversed());
		skills = Collections.unmodifiableList(skillList);

		List<Session.Drop> dropList = new ArrayList<>(s.loot.values());
		dropList.sort(Comparator.comparingLong(Session.Drop::value).reversed()
			.thenComparing(Comparator.comparingLong(Session.Drop::getQuantity).reversed()));
		drops = Collections.unmodifiableList(dropList);

		List<Session.KillLine> killList = new ArrayList<>();
		for (Session.KillLine line : s.kills.values())
		{
			if (line.count() > 0)
			{
				killList.add(line);
			}
		}
		killList.sort(Comparator.comparingInt(Session.KillLine::count).reversed());
		killLines = Collections.unmodifiableList(killList);

		List<Moment> momentList = new ArrayList<>(s.moments);
		momentList.sort(Comparator.comparingLong(Moment::getAt));
		moments = Collections.unmodifiableList(momentList);

		List<Moment> best = new ArrayList<>(s.moments);
		best.sort(Comparator.comparingDouble(Moment::rank).reversed().thenComparingLong(Moment::getAt));
		highlights = Collections.unmodifiableList(best);
	}

	public static Summary of(Session session, State state)
	{
		return new Summary(session, state);
	}

	public State getState()
	{
		return state;
	}

	public String getPlayer()
	{
		return player;
	}

	public long getStartedAt()
	{
		return startedAt;
	}

	public long getEndedAt()
	{
		return endedAt;
	}

	public long getPlayedMs()
	{
		return playedMs;
	}

	public long getTotalXp()
	{
		return totalXp;
	}

	public long getLootValue()
	{
		return lootValue;
	}

	public int getLevels()
	{
		return levels;
	}

	public int getKills()
	{
		return kills;
	}

	public int getDeaths()
	{
		return deaths;
	}

	/** Kinds of item left out of a session filed in the journal; their worth is still in the total. */
	public int getOtherDrops()
	{
		return otherDrops;
	}

	/** Skills that gained experience, most first. */
	public List<Session.SkillLine> getSkills()
	{
		return skills;
	}

	/** Every kind of item received, most valuable first. */
	public List<Session.Drop> getDrops()
	{
		return drops;
	}

	/** What was killed or cleared, most first. */
	public List<Session.KillLine> getKillLines()
	{
		return killLines;
	}

	/** Moments in the order they happened. */
	public List<Moment> getMoments()
	{
		return moments;
	}

	/** Moments in the order they matter. */
	public List<Moment> getHighlights()
	{
		return highlights;
	}

	public long xpPerHour()
	{
		return perHour(totalXp);
	}

	public long lootPerHour()
	{
		return perHour(lootValue);
	}

	private long perHour(long value)
	{
		return playedMs < MIN_RATE_MS ? 0 : Math.round(value * 3_600_000.0 / playedMs);
	}

	/** One line that says what the session was about. */
	public String headline()
	{
		Moment max = null;
		for (Moment m : highlights)
		{
			if (m.getKind() == Moment.Kind.PET)
			{
				return "A pet followed you home";
			}
			if (m.getKind() == Moment.Kind.MAX && max == null)
			{
				max = m;
			}
		}
		if (max != null)
		{
			return max.getTitle() + "!";
		}
		Session.KillLine boss = null;
		for (Session.KillLine line : killLines)
		{
			if (line.getKillCount() > 0)
			{
				boss = line;
				break;
			}
		}
		Session.SkillLine top = skills.isEmpty() ? null : skills.get(0);
		if (boss != null && boss.count() >= 3)
		{
			return boss.count() + " " + boss.getName() + (boss.count() == 1 ? " kill" : " kills");
		}
		if (top != null)
		{
			int gained = top.getLevel() - top.getStartLevel();
			if (gained >= 2)
			{
				return gained + " " + top.getSkill() + " levels";
			}
			return "A " + top.getSkill() + " session";
		}
		if (!killLines.isEmpty())
		{
			Session.KillLine first = killLines.get(0);
			return first.count() + " " + first.getName() + (first.count() == 1 ? " kill" : " kills");
		}
		if (lootValue > 0)
		{
			return "A haul worth " + Chronicle.coins(lootValue);
		}
		return "A quiet session";
	}
}
