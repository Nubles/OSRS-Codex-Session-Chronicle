package com.codex.sessionchronicle.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The last seven days told as one story, for the week card: play time, experience and loot each day,
 * what the week was mostly spent on, its best moments, and how the net worth moved when Codex Ledger
 * keeps it. A session counts toward the day it began, as Today does. Pure, so it is tested offline.
 */
public final class Week implements Cloneable
{
	/** Days on the card: today and the six before it. */
	public static final int DAYS = 7;
	/** Activities and highlights shown. */
	static final int ACTIVITIES = 3;
	static final int HIGHLIGHTS = 3;
	/** Points kept for the worth line; Ledger's history is thinned to this many. */
	static final int WORTH_POINTS = 48;

	/** One day of the week. */
	public static final class Day
	{
		private final LocalDate date;
		private long playedMs;
		private long xp;
		private long loot;
		private int sessions;

		Day(LocalDate date)
		{
			this.date = date;
		}

		public LocalDate getDate()
		{
			return date;
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

		public int getSessions()
		{
			return sessions;
		}
	}

	/** Something the week was spent on, a skill or a monster, and how long. */
	public static final class Activity
	{
		private final String name;
		private long playedMs;

		Activity(String name)
		{
			this.name = name;
		}

		public String getName()
		{
			return name;
		}

		public long getPlayedMs()
		{
			return playedMs;
		}
	}

	private final List<Day> days = new ArrayList<>();
	private final List<Activity> activities = new ArrayList<>();
	private final List<Moment> highlights = new ArrayList<>();
	private List<long[]> worth = Collections.emptyList();
	private int sessions;
	private long playedMs;
	private long xp;
	private long loot;
	private int kills;
	private int levels;
	private int deaths;
	private int records;
	private String player;
	private Before before;

	/** The seven days before the week, to set it against: their play, experience and loot. */
	public static final class Before
	{
		private long playedMs;
		private long xp;
		private long loot;
		private int sessions;

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

		public int getSessions()
		{
			return sessions;
		}
	}

	private Week()
	{
	}

	/**
	 * @param journal filed sessions, in any order
	 * @param current the session being lived, or null
	 * @param ledger Codex Ledger's saved worth, or null without it
	 */
	public static Week of(List<Session> journal, Session current, long now, ZoneId zone, LedgerWorth ledger)
	{
		Week w = new Week();
		LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
		LocalDate first = today.minusDays(DAYS - 1);
		for (int i = 0; i < DAYS; i++)
		{
			w.days.add(new Day(first.plusDays(i)));
		}
		List<Session> all = new ArrayList<>(journal);
		if (current != null && !current.isEmpty())
		{
			all.add(current);
		}
		all.sort(Comparator.comparingLong(Session::getStartedAt));
		Map<String, Activity> spent = new LinkedHashMap<>();
		List<Moment> moments = new ArrayList<>();
		LocalDate before = first.minusDays(DAYS);
		LocalDate earliest = null;
		Before last = new Before();
		for (Session s : all)
		{
			if (s == null)
			{
				continue;
			}
			LocalDate day = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate();
			earliest = earliest == null || day.isBefore(earliest) ? day : earliest;
			if (!day.isBefore(before) && day.isBefore(first))
			{
				last.sessions++;
				last.playedMs += s.playedMs;
				last.xp += s.totalXp();
				last.loot += s.lootValue();
			}
			if (day.isBefore(first) || day.isAfter(today))
			{
				continue;
			}
			Day d = w.days.get((int) (day.toEpochDay() - first.toEpochDay()));
			d.sessions++;
			d.playedMs += s.playedMs;
			d.xp += s.totalXp();
			d.loot += s.lootValue();
			w.sessions++;
			w.playedMs += s.playedMs;
			w.xp += s.totalXp();
			w.loot += s.lootValue();
			w.kills += s.totalKills();
			w.levels += s.levelsGained();
			w.deaths += s.getDeaths();
			if (s.player != null)
			{
				w.player = s.player;
			}
			String activity = s.activity != null ? s.activity : Pace.activity(s);
			if (activity != null)
			{
				spent.computeIfAbsent(activity, Activity::new).playedMs += s.playedMs;
			}
			for (Moment m : s.getMoments())
			{
				if (m == null)
				{
					continue;
				}
				// Deaths and plain levels make a week's story only when nothing better happened.
				if (m.getKind() != Moment.Kind.DEATH)
				{
					moments.add(m);
				}
			}
		}
		w.activities.addAll(spent.values());
		w.activities.sort(Comparator.comparingLong(Activity::getPlayedMs).reversed());
		while (w.activities.size() > ACTIVITIES)
		{
			w.activities.remove(w.activities.size() - 1);
		}
		// The ones that matter most, the latest first among equals; a record beaten again later in the
		// week is told once, by its latest.
		moments.sort(Comparator.comparingDouble(Moment::rank).reversed().thenComparing(Comparator.comparingLong(Moment::getAt).reversed()));
		java.util.Set<String> told = new java.util.HashSet<>();
		for (Moment m : moments)
		{
			if (w.highlights.size() == HIGHLIGHTS)
			{
				break;
			}
			String title = m.getTitle();
			if (m.getKind() == Moment.Kind.RECORD && title.contains(":") && !told.add(title.substring(0, title.indexOf(':'))))
			{
				continue;
			}
			w.highlights.add(m);
		}
		// Records are counted as they are told: one beaten again later in the week is one record.
		java.util.Set<String> records = new java.util.HashSet<>();
		for (Moment m : moments)
		{
			if (m.getKind() == Moment.Kind.RECORD)
			{
				String title = m.getTitle();
				records.add(title.contains(":") ? title.substring(0, title.indexOf(':')) : title);
			}
		}
		w.records = records.size();
		// The week before is only set against this one when the journal reaches back past all of it (a
		// session older than it is kept): a journal begun midweek, or trimmed of its oldest sessions,
		// would make it look idle.
		w.before = earliest != null && earliest.isBefore(before) ? last : null;
		if (ledger != null)
		{
			w.worth = thin(ledger.since(first.atStartOfDay(zone).toInstant().toEpochMilli(), now), WORTH_POINTS);
		}
		return w;
	}

	/** Keeps at most a number of points, evenly through the list, always keeping the first and the last. */
	static List<long[]> thin(List<long[]> points, int max)
	{
		if (points.size() <= max)
		{
			return points;
		}
		List<long[]> out = new ArrayList<>(max);
		for (int i = 0; i < max; i++)
		{
			out.add(points.get((int) Math.round(i * (points.size() - 1) / (double) (max - 1))));
		}
		return out;
	}

	public List<Day> getDays()
	{
		return days;
	}

	/** The seven days before, or null when the journal does not reach back through them. */
	public Before getBefore()
	{
		return before;
	}

	/** The day played longest, or -1 in a week without play. */
	public int getBestDay()
	{
		int best = -1;
		for (int i = 0; i < days.size(); i++)
		{
			if (days.get(i).playedMs > 0 && (best < 0 || days.get(i).playedMs > days.get(best).playedMs))
			{
				best = i;
			}
		}
		return best;
	}

	/** Days with any play. */
	public int getDaysPlayed()
	{
		int n = 0;
		for (Day d : days)
		{
			n += d.sessions > 0 ? 1 : 0;
		}
		return n;
	}

	/** What the week was mostly spent on, longest first. */
	public List<Activity> getActivities()
	{
		return activities;
	}

	/** The week's best moments, the ones that matter most first. */
	public List<Moment> getHighlights()
	{
		return highlights;
	}

	/** Net worth through the week by Codex Ledger, oldest first as {epoch ms, worth}; empty without it. */
	public List<long[]> getWorth()
	{
		return worth;
	}

	/**
	 * The same week without Codex Ledger's worth, for a card shared without it: no worth line, no
	 * change. The week itself is left as it is.
	 */
	public Week withoutWorth()
	{
		if (worth.isEmpty())
		{
			return this;
		}
		try
		{
			Week w = (Week) super.clone();
			w.worth = Collections.emptyList();
			return w;
		}
		catch (CloneNotSupportedException e)
		{
			throw new AssertionError(e);
		}
	}

	/** How the worth moved over the points kept, or null with fewer than two. */
	public Long worthChange()
	{
		return worth.size() < 2 ? null : worth.get(worth.size() - 1)[1] - worth.get(0)[1];
	}

	/** When the worth line begins: the week's first midnight, or later when Ledger began within the week. */
	public long worthFrom()
	{
		return worth.isEmpty() ? 0 : worth.get(0)[0];
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

	/**
	 * What the Highlights header says beside its lines: the records, never more than are listed ("1
	 * record", or "3 of 4 records" when only three fit), or null when none is listed.
	 */
	public String highlightsNote()
	{
		if (records == 0)
		{
			return null;
		}
		int shown = 0;
		for (Moment m : highlights)
		{
			shown += m.getKind() == Moment.Kind.RECORD ? 1 : 0;
		}
		if (shown == 0)
		{
			// Better moments filled the lines: a count of records none of them are would only puzzle.
			return null;
		}
		String noun = records == 1 ? " record" : " records";
		return shown >= records ? records + noun : shown + " of " + records + noun;
	}

	/** Personal records set during the week, each counted once however often it was beaten. */
	public int getRecords()
	{
		return records;
	}

	/** The player of the week's latest session, or null. */
	public String getPlayer()
	{
		return player;
	}

	/** The first day on the card. */
	public LocalDate getFirst()
	{
		return days.get(0).date;
	}

	public LocalDate getLast()
	{
		return days.get(DAYS - 1).date;
	}

	/** True when there is nothing in the week to tell. */
	public boolean isEmpty()
	{
		return sessions == 0;
	}
}
