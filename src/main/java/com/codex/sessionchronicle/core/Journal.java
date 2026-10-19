package com.codex.sessionchronicle.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
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
		c.goal = s.getGoal();
		c.worthStart = s.worthStart;
		c.worthNow = s.worthNow;
		c.slayer = s.slayer;
		c.activity = Pace.activity(s);
		c.slayerDone = s.slayerDone;
		// The journal keeps what the trips came to, not each trip.
		c.trips = null;
		c.tripCount = s.tripCount;
		c.tripMs = s.tripMs;
		c.bestTripMs = s.bestTripMs;
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

	/**
	 * Names the activity of filed sessions saved before it was kept, so Codex Ledger can read it.
	 *
	 * @return whether any was named
	 */
	public static boolean label(List<Session> journal)
	{
		boolean changed = false;
		for (Session s : journal)
		{
			if (s != null && s.activity == null)
			{
				String activity = Pace.activity(s);
				if (activity != null)
				{
					s.activity = activity;
					changed = true;
				}
			}
		}
		return changed;
	}

	/**
	 * Marks the personal records a finished session set against the sessions before it: its main
	 * skill's or monster's fastest rate, the most loot or experience in one session. A record
	 * needs an earlier session to beat, so the first trip anywhere is never one.
	 *
	 * @return the record moments added, at most three
	 */
	public static List<Moment> markRecords(Session s, List<Session> journal)
	{
		List<Moment> marked = new ArrayList<>();
		if (s == null || s.playedMs < Pace.MIN_PLAYED_MS)
		{
			return marked;
		}
		for (Moment m : s.moments)
		{
			if (m.getKind() == Moment.Kind.RECORD)
			{
				// Already marked when it was filed once before.
				return marked;
			}
		}
		long at = s.endedAt > 0 ? s.endedAt : s.lastSeenAt;
		for (Pace.Line line : Pace.of(s, journal))
		{
			if (line.getKind() == Pace.Kind.LOOT || !line.isRecord())
			{
				continue;
			}
			String unit = line.getKind() == Pace.Kind.SKILL ? " xp/hr" : " " + Summary.noun(line.getName()) + "/hr";
			marked.add(new Moment(Moment.Kind.RECORD, at, line.getName() + " record: " + Chronicle.amount(line.getRate()) + unit,
				"Your best was " + Chronicle.amount(line.getBest()) + unit, 0, 0));
		}
		long mostLoot = 0;
		long mostXp = 0;
		boolean earlierLoot = false;
		boolean earlierXp = false;
		for (Session past : journal)
		{
			if (past == null || past.startedAt >= s.startedAt)
			{
				continue;
			}
			earlierLoot |= past.lootValue() > 0;
			earlierXp |= past.totalXp() > 0;
			mostLoot = Math.max(mostLoot, past.lootValue());
			mostXp = Math.max(mostXp, past.totalXp());
		}
		if (earlierLoot && s.lootValue() > mostLoot)
		{
			marked.add(new Moment(Moment.Kind.RECORD, at, "Richest session yet", Chronicle.coins(s.lootValue()) + " of loot, beating "
				+ Chronicle.coins(mostLoot), 0, 0));
		}
		if (earlierXp && s.totalXp() > mostXp)
		{
			marked.add(new Moment(Moment.Kind.RECORD, at, "Most experience yet", Chronicle.amount(s.totalXp()) + " xp, beating "
				+ Chronicle.amount(mostXp), 0, 0));
		}
		while (marked.size() > 3)
		{
			marked.remove(marked.size() - 1);
		}
		s.moments.addAll(marked);
		return marked;
	}

	/**
	 * One activity across the journal, a skill trained or a monster killed: its fastest session,
	 * every session's rate in order, and what Codex Ledger saw the net worth do over them.
	 */
	public static final class Best
	{
		/** Session rates kept for the trend, the newest. */
		public static final int HISTORY = 12;
		private final Pace.Kind kind;
		private final String name;
		private long rate;
		private long at;
		private int sessions;
		private long playedMs;
		/** Each session's start and rate, oldest first; the newest {@link #HISTORY}. */
		private final List<long[]> history = new ArrayList<>();
		private long worthChange;
		private long worthMs;

		Best(Pace.Kind kind, String name, long rate, long at)
		{
			this.kind = kind;
			this.name = name;
			this.rate = rate;
			this.at = at;
		}

		void add(Session s, long sessionRate)
		{
			if (sessions == 0 || sessionRate > rate)
			{
				rate = sessionRate;
				at = s.startedAt;
			}
			sessions++;
			playedMs += s.playedMs;
			history.add(new long[]{s.startedAt, sessionRate});
			Long change = s.worthChange();
			if (change != null)
			{
				worthChange += change;
				worthMs += s.playedMs;
			}
		}

		/** Sessions of it long enough to count. */
		public int getSessions()
		{
			return sessions;
		}

		/** Play time in those sessions. */
		public long getPlayedMs()
		{
			return playedMs;
		}

		/** Each session's start and rate, oldest first, the newest {@link #HISTORY} of them. */
		public List<long[]> getHistory()
		{
			int from = Math.max(0, history.size() - HISTORY);
			return Collections.unmodifiableList(history.subList(from, history.size()));
		}

		/** The latest session's rate. */
		public long getLatest()
		{
			return history.isEmpty() ? rate : history.get(history.size() - 1)[1];
		}

		/**
		 * How the pace moved over the sessions shown: the last three against the first three, as
		 * 0.12 for 12% faster; NaN with fewer than four to go by.
		 */
		public double trend()
		{
			List<long[]> h = getHistory();
			if (h.size() < 4)
			{
				return Double.NaN;
			}
			int n = Math.min(3, h.size() / 2);
			double first = 0;
			double last = 0;
			for (int i = 0; i < n; i++)
			{
				first += h.get(i)[1];
				last += h.get(h.size() - 1 - i)[1];
			}
			return first <= 0 ? Double.NaN : last / first - 1;
		}

		/** Net worth an hour by Codex Ledger over the sessions it saw, or null without any. */
		public Long worthPerHour()
		{
			return worthMs < Pace.MIN_PLAYED_MS ? null : Math.round(worthChange * 3_600_000.0 / worthMs);
		}

		public Pace.Kind getKind()
		{
			return kind;
		}

		public String getName()
		{
			return name;
		}

		/** Experience or kills an hour. */
		public long getRate()
		{
			return rate;
		}

		/** When the session that set it began. */
		public long getAt()
		{
			return at;
		}
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
		private int longestStreak;
		private List<Best> bests = new ArrayList<>();
		private Today today = new Today();

		/** Everything played today, across every session begun since midnight and the one being lived. */
		public Today getToday()
		{
			return today;
		}

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

		/** The most days in a row ever played, as far back as the journal goes. */
		public int getLongestStreak()
		{
			return longestStreak;
		}

		/** The fastest each main skill and monster has gone, the most recently set first. */
		public List<Best> getBests()
		{
			return bests;
		}
	}

	/** The day so far, summed over its sessions; a session counts toward the day it began. */
	public static final class Today
	{
		private int sessions;
		private long playedMs;
		private long xp;
		private long loot;
		private int kills;
		private int levels;
		private int deaths;
		private final Map<String, Long> bySkill = new LinkedHashMap<>();

		void add(Session s)
		{
			sessions++;
			playedMs += s.playedMs;
			xp += s.totalXp();
			loot += s.lootValue();
			kills += s.totalKills();
			levels += s.levelsGained();
			deaths += s.getDeaths();
			for (Session.SkillLine line : s.skills.values())
			{
				if (line.gained() > 0)
				{
					bySkill.merge(line.skill, line.gained(), Long::sum);
				}
			}
		}

		public int getSessions()
		{
			return sessions;
		}

		public long getPlayedMs()
		{
			return playedMs;
		}

		public long getXp()
		{
			return xp;
		}

		public long getLoot()
		{
			return loot;
		}

		public int getKills()
		{
			return kills;
		}

		public int getLevels()
		{
			return levels;
		}

		public int getDeaths()
		{
			return deaths;
		}

		/** The skill that gained the most today, or null without experience. */
		public String getTopSkill()
		{
			String top = null;
			long most = 0;
			for (Map.Entry<String, Long> e : bySkill.entrySet())
			{
				if (e.getValue() > most)
				{
					most = e.getValue();
					top = e.getKey();
				}
			}
			return top;
		}

		/** Experience the top skill gained today. */
		public long getTopSkillXp()
		{
			String top = getTopSkill();
			return top == null ? 0 : bySkill.get(top);
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
		// Oldest first, so each activity's history runs forward in time.
		all.sort(Comparator.comparingLong(Session::getStartedAt));
		Records r = new Records();
		Set<LocalDate> days = new HashSet<>();
		Map<String, Best> bests = new LinkedHashMap<>();
		LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
		for (Session s : all)
		{
			if (Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate().equals(today))
			{
				r.today.add(s);
			}
			if (s.playedMs >= Pace.MIN_PLAYED_MS)
			{
				for (Pace.Line line : Pace.of(s, Collections.emptyList()))
				{
					if (line.getKind() == Pace.Kind.LOOT)
					{
						continue;
					}
					String key = line.getKind() + ":" + (line.getKind() == Pace.Kind.KILLS ? Chronicle.key(line.getName()) : line.getName());
					bests.computeIfAbsent(key, k -> new Best(line.getKind(), line.getName(), 0, 0)).add(s, line.getRate());
				}
			}
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
		for (LocalDate d : days)
		{
			if (days.contains(d.minusDays(1)))
			{
				continue;
			}
			int run = 0;
			while (days.contains(d.plusDays(run)))
			{
				run++;
			}
			r.longestStreak = Math.max(r.longestStreak, run);
		}
		r.bests = new ArrayList<>(bests.values());
		// What the player does now first: by the latest session of each.
		r.bests.sort(Comparator.comparingLong((Best b) -> b.history.isEmpty() ? b.at : b.history.get(b.history.size() - 1)[0]).reversed());
		return r;
	}
}
