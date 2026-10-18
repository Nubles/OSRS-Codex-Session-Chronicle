package com.codex.sessionchronicle.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The sessions already lived, newest first, and the records they hold. Sessions are trimmed
 * as they are filed so a long journal still fits comfortably in the RuneLite profile.
 */
public final class Journal
{
	static final int KEEP_DROPS = 24;
	static final int KEEP_KILLS = 12;
	static final int KEEP_MOMENTS = 30;
	static final long WEEK_MS = 7 * 24 * 3_600_000L;

	private Journal()
	{
	}

	/** Files a finished session at the front of the journal, keeping at most cap sessions. */
	public static List<Session> file(List<Session> journal, Session session, int cap)
	{
		List<Session> next = new ArrayList<>();
		next.add(compact(session));
		for (Session s : journal)
		{
			if (s.startedAt != session.startedAt)
			{
				next.add(s);
			}
		}
		next.sort(Comparator.comparingLong(Session::getStartedAt).reversed());
		while (next.size() > Math.max(1, cap))
		{
			next.remove(next.size() - 1);
		}
		return next;
	}

	/** The journal without the session that started at that moment. */
	public static List<Session> remove(List<Session> journal, long startedAt)
	{
		List<Session> next = new ArrayList<>(journal);
		next.removeIf(s -> s.startedAt == startedAt);
		return next;
	}

	/** A copy of the session with only what the journal needs to tell it again. */
	static Session compact(Session s)
	{
		Session c = new Session();
		c.startedAt = s.startedAt;
		c.lastSeenAt = s.lastSeenAt;
		c.endedAt = s.endedAt > 0 ? s.endedAt : s.lastSeenAt;
		c.playedMs = s.playedMs;
		c.player = s.player;
		c.deaths = s.deaths;
		for (Map.Entry<String, Session.SkillLine> e : s.skills.entrySet())
		{
			if (e.getValue().gained() > 0)
			{
				c.skills.put(e.getKey(), e.getValue());
			}
		}
		List<Session.Drop> drops = new ArrayList<>(s.loot.values());
		drops.sort(Comparator.comparingLong(Session.Drop::value).reversed());
		c.otherLoot = s.otherLoot;
		c.otherDrops = s.otherDrops;
		for (int i = 0; i < drops.size(); i++)
		{
			Session.Drop d = drops.get(i);
			if (i < KEEP_DROPS)
			{
				c.loot.put(d.id, d);
			}
			else
			{
				c.otherLoot += d.value();
				c.otherDrops++;
			}
		}
		List<Map.Entry<String, Session.KillLine>> kills = new ArrayList<>(s.kills.entrySet());
		kills.sort(Comparator.comparingInt((Map.Entry<String, Session.KillLine> e) -> e.getValue().count()).reversed());
		Map<String, Session.KillLine> keptKills = new LinkedHashMap<>();
		for (int i = 0; i < kills.size() && i < KEEP_KILLS; i++)
		{
			keptKills.put(kills.get(i).getKey(), kills.get(i).getValue());
		}
		c.kills = keptKills;
		List<Moment> moments = new ArrayList<>(s.moments);
		moments.sort(Comparator.comparingDouble(Moment::rank).reversed());
		if (moments.size() > KEEP_MOMENTS)
		{
			moments = new ArrayList<>(moments.subList(0, KEEP_MOMENTS));
		}
		moments.sort(Comparator.comparingLong(Moment::getAt));
		c.moments = moments;
		return c;
	}

	/** The journal's records and recent rhythm. */
	public static final class Records
	{
		private Session mostXp;
		private Session mostLoot;
		private Session longest;
		private long weekPlayedMs;
		private int weekSessions;
		private int streakDays;

		public Session getMostXp()
		{
			return mostXp;
		}

		public Session getMostLoot()
		{
			return mostLoot;
		}

		public Session getLongest()
		{
			return longest;
		}

		public long getWeekPlayedMs()
		{
			return weekPlayedMs;
		}

		public int getWeekSessions()
		{
			return weekSessions;
		}

		/** Days in a row, up to today or yesterday, with at least one session. */
		public int getStreakDays()
		{
			return streakDays;
		}
	}

	/**
	 * Records across the journal and the session being lived, if any.
	 */
	public static Records records(List<Session> journal, Session current, long now, ZoneId zone)
	{
		List<Session> all = new ArrayList<>(journal);
		if (current != null && !current.isEmpty())
		{
			all.add(current);
		}
		Records r = new Records();
		Set<LocalDate> days = new HashSet<>();
		for (Session s : all)
		{
			if (r.mostXp == null || s.totalXp() > r.mostXp.totalXp())
			{
				r.mostXp = s;
			}
			if (r.mostLoot == null || s.lootValue() > r.mostLoot.lootValue())
			{
				r.mostLoot = s;
			}
			if (r.longest == null || s.playedMs > r.longest.playedMs)
			{
				r.longest = s;
			}
			if (now - s.startedAt <= WEEK_MS)
			{
				r.weekPlayedMs += s.playedMs;
				r.weekSessions++;
			}
			days.add(Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate());
		}
		if (r.mostXp != null && r.mostXp.totalXp() <= 0)
		{
			r.mostXp = null;
		}
		if (r.mostLoot != null && r.mostLoot.lootValue() <= 0)
		{
			r.mostLoot = null;
		}
		LocalDate day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
		if (!days.contains(day))
		{
			day = day.minusDays(1);
		}
		while (days.contains(day))
		{
			r.streakDays++;
			day = day.minusDays(1);
		}
		return r;
	}
}
