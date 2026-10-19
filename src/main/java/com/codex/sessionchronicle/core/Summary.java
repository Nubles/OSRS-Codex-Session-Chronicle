package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A read-only telling of one session, ready for the panel and the card: totals, rates, the
 * skills, drops and kills in order of size, its moments and a headline.
 */
public final class Summary
{
	/** Rates are only shown once a session is long enough for them to mean something. */
	static final long MIN_RATE_MS = 5 * 60_000L;
	private static final Pattern CLUE = Pattern.compile("(?i)clue scroll \\((\\w+)\\)");
	/** Raids and other runs that are finished rather than killed. */
	private static final String[] CLEARS = {"chambers of xeric", "theatre of blood", "tombs of amascut", "gauntlet", "corrupted gauntlet",
		"fortis colosseum", "tzhaar-ket-rak", "hallowed sepulchre"};

	private static final List<String> COMBAT = Arrays.asList("Attack", "Strength", "Defence", "Ranged", "Magic",
		"Hitpoints", "Prayer", "Slayer");

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
	private final Goal goal;
	private final long goalProgress;
	private List<Pace.Line> pace = Collections.emptyList();
	private Map<Goal.Kind, List<Goal.Preset>> presets = Collections.emptyMap();

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
		goal = s.getGoal();
		goalProgress = goal == null ? 0 : Goal.progress(s, goal.getKind());
	}

	public static Summary of(Session session, State state)
	{
		return new Summary(session, state);
	}

	/**
	 * A telling that also sets the session's pace beside the player's own earlier sessions and,
	 * for a live session without a goal, offers goals to set.
	 *
	 * @param journal finished sessions; only those that began before this one are compared
	 */
	public static Summary of(Session session, State state, List<Session> journal)
	{
		Summary summary = new Summary(session, state);
		summary.pace = Collections.unmodifiableList(Pace.of(session, journal));
		if (state != State.ENDED && session.getGoal() == null)
		{
			Map<Goal.Kind, List<Goal.Preset>> offered = new EnumMap<>(Goal.Kind.class);
			for (Goal.Kind kind : Goal.Kind.values())
			{
				offered.put(kind, Goal.presets(kind, session, journal));
			}
			summary.presets = Collections.unmodifiableMap(offered);
		}
		return summary;
	}

	/** A short fingerprint of everything the panel draws, to tell when a summary has really changed. */
	public static String signature(Summary s)
	{
		if (s == null)
		{
			return "none";
		}
		return s.state + "/" + s.startedAt + "/" + s.playedMs / 60_000 + "/" + s.totalXp + "/" + s.lootValue + "/" + s.kills
			+ "/" + s.levels + "/" + s.deaths + "/" + s.moments.size() + "/" + s.drops.size() + "/" + s.player
			+ "/" + (s.goal == null ? "-" : s.goal.getKind() + ":" + s.goal.getTarget() + ":" + s.goal.isReached())
			+ "/" + s.pace.size() + "/" + s.presets.size();
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

	/** The session's goal, or null. */
	public Goal getGoal()
	{
		return goal;
	}

	/** How far the session has come toward its goal, in the goal's own unit. */
	public long getGoalProgress()
	{
		return goalProgress;
	}

	/** Its pace beside the player's earlier sessions of the same kind; empty when too short or not asked for. */
	public List<Pace.Line> getPace()
	{
		return pace;
	}

	/** Goals offered for each kind, for a live session that has none. */
	public Map<Goal.Kind, List<Goal.Preset>> getPresets()
	{
		return presets;
	}

	/** The goal's rate of progress an hour, from the session so far; 0 before rates mean anything. */
	public long goalPerHour()
	{
		if (goal == null)
		{
			return 0;
		}
		return goal.getKind() == Goal.Kind.TIME ? 3_600_000L : perHour(goalProgress);
	}

	/** Play time still needed for the goal at the session's pace so far, or -1 when it cannot be told. */
	public long goalEtaMs()
	{
		long rate = goalPerHour();
		if (goal == null || goal.isReached())
		{
			return 0;
		}
		if (rate <= 0)
		{
			return -1;
		}
		return Math.round((goal.getTarget() - goalProgress) * 3_600_000.0 / rate);
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

	/**
	 * One line that says what the session was about, the rarest thing first: a pet, a 99, a new
	 * collection log slot, a boss trip, a run of levels, a slayer grind, then the main skill.
	 */
	public String headline()
	{
		Moment max = null;
		List<Moment> logged = new ArrayList<>();
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
			if (m.getKind() == Moment.Kind.COLLECTION)
			{
				logged.add(m);
			}
		}
		if (max != null)
		{
			return max.getTitle() + "!";
		}
		if (logged.size() == 1)
		{
			return logged.get(0).getTitle() + "!";
		}
		if (logged.size() > 1)
		{
			return logged.size() + " new collection log slots";
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
		if (boss != null && boss.count() >= 3)
		{
			return kills(boss);
		}
		Session.SkillLine top = skills.isEmpty() ? null : skills.get(0);
		int gained = top == null ? 0 : top.getLevel() - top.getStartLevel();
		if (gained >= 2)
		{
			return gained + " " + top.getSkill() + " levels";
		}
		// A slayer grind outranks its combat levels; bird nests, crates and reward pools are loot of
		// a skilling session and never outrank the skill.
		if (!killLines.isEmpty() && killLines.get(0).count() >= 10 && (top == null || COMBAT.contains(top.getSkill())))
		{
			return kills(killLines.get(0));
		}
		if (top != null)
		{
			if (gained == 1)
			{
				return top.getSkill() + " level " + top.getLevel();
			}
			long rate = perHour(top.gained());
			return rate > 0 ? top.getSkill() + " at " + Chronicle.amount(rate) + "/hr" : "+" + Chronicle.amount(top.gained()) + " " + top.getSkill() + " xp";
		}
		if (!killLines.isEmpty())
		{
			return kills(killLines.get(0));
		}
		if (lootValue > 0)
		{
			return "A haul worth " + Chronicle.coins(lootValue);
		}
		return "A quiet session";
	}

	/** What one of a kill line counts, in the plural: "kills", "clears" for raids and runs, "clues" for caskets. */
	public static String noun(String name)
	{
		if (CLUE.matcher(name).matches())
		{
			return "clues";
		}
		String key = Chronicle.key(name);
		for (String raid : CLEARS)
		{
			if (key.startsWith(raid))
			{
				return "clears";
			}
		}
		return "kills";
	}

	/** "24 Vorkath kills", "3 Chambers of Xeric clears", "12 medium clues": what the count stands for. */
	static String kills(Session.KillLine line)
	{
		int n = line.count();
		String name = line.getName();
		Matcher clue = CLUE.matcher(name);
		if (clue.matches())
		{
			return n + " " + clue.group(1).toLowerCase(Locale.ROOT) + (n == 1 ? " clue" : " clues");
		}
		String key = Chronicle.key(name);
		for (String raid : CLEARS)
		{
			if (key.startsWith(raid))
			{
				return n + " " + name + (n == 1 ? " clear" : " clears");
			}
		}
		return n + " " + name + (n == 1 ? " kill" : " kills");
	}
}
