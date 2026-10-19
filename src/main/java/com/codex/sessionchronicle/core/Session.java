package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One play session as it was lived: the experience each skill gained, the loot that dropped,
 * the kills, and the moments worth telling. Plain fields so it saves to the profile as JSON.
 */
public class Session
{
	long startedAt;
	long lastSeenAt;
	long endedAt;
	long playedMs;
	String player;
	Map<String, SkillLine> skills = new LinkedHashMap<>();
	Map<Integer, Drop> loot = new LinkedHashMap<>();
	Map<String, KillLine> kills = new LinkedHashMap<>();
	List<Moment> moments = new ArrayList<>();
	int deaths;
	/** Loot trimmed from the session when it was filed in the journal, so the totals still add up. */
	long otherLoot;
	int otherDrops;
	/** What the player set out to do this session, or null. */
	Goal goal;
	/** Trips out from the bank and back, oldest first; the newest are kept while it is lived. */
	List<Trip> trips = new ArrayList<>();
	/** The trip under way: where its play time, kills, loot and experience stood as it set out; null at the bank. */
	Trip out;
	/** Trips taken and their play time in all, kept when the journal drops the list. */
	int tripCount;
	long tripMs;
	/** The quickest trip that brought something back, in play time, or 0. */
	long bestTripMs;

	/** The slayer task as last seen, or null without one. */
	SlayerTask slayer;
	/** Slayer tasks finished during the session, oldest first; null in older saves. */
	List<SlayerTask> slayerDone;

	/**
	 * What the session was mainly spent on, as Pace judges it: its main monster ("Vorkath") or else its
	 * main skill ("Mining"). Set when it is filed; Codex Ledger reads it to tell what pays.
	 */
	String activity;

	/** Net worth by Codex Ledger as the session began, or null when it is not known. */
	Long worthStart;
	/** Net worth by Codex Ledger as last read during the session, or null. */
	Long worthNow;

	Session()
	{
	}

	Session(long now, String player)
	{
		startedAt = now;
		lastSeenAt = now;
		this.player = player;
	}

	public long getStartedAt()
	{
		return startedAt;
	}

	public long getLastSeenAt()
	{
		return lastSeenAt;
	}

	public long getEndedAt()
	{
		return endedAt;
	}

	public long getPlayedMs()
	{
		return playedMs;
	}

	public String getPlayer()
	{
		return player;
	}

	public int getDeaths()
	{
		return deaths;
	}

	/** The session's moments in the order they were added. */
	public List<Moment> getMoments()
	{
		return Collections.unmodifiableList(moments);
	}

	/** The session's goal, or null when none was set (or a saved one is unusable). */
	public Goal getGoal()
	{
		return goal == null || !goal.isValid() ? null : goal;
	}

	/** What a filed session was mainly spent on, or null. */
	public String getActivity()
	{
		return activity;
	}

	/** The slayer task under way as last seen, or null. */
	public SlayerTask getSlayer()
	{
		return slayer;
	}

	/** Slayer tasks finished in the session, oldest first. */
	public List<SlayerTask> getSlayerDone()
	{
		return slayerDone == null ? Collections.emptyList() : Collections.unmodifiableList(slayerDone);
	}

	/** How much the net worth moved over the session by Codex Ledger, or null when Ledger did not say. */
	public Long worthChange()
	{
		return worthStart == null || worthNow == null ? null : worthNow - worthStart;
	}

	public long totalXp()
	{
		long total = 0;
		for (SkillLine line : skills.values())
		{
			total += line.gained();
		}
		return total;
	}

	public long lootValue()
	{
		long total = otherLoot;
		for (Drop drop : loot.values())
		{
			total += drop.value();
		}
		return total;
	}

	public int levelsGained()
	{
		int total = 0;
		for (SkillLine line : skills.values())
		{
			total += Math.max(0, line.level - line.startLevel);
		}
		return total;
	}

	/** Monsters killed and raids cleared: bird nests, crates and caskets opened are left out. */
	public int totalKills()
	{
		int total = 0;
		for (KillLine line : kills.values())
		{
			if (line.isKill())
			{
				total += line.count();
			}
		}
		return total;
	}

	/** A session with nothing in it is not worth a page in the journal. */
	boolean isEmpty()
	{
		return totalXp() <= 0 && loot.isEmpty() && kills.isEmpty() && moments.isEmpty() && playedMs < 5 * 60_000L;
	}

	/** Experience in one skill. */
	public static class SkillLine
	{
		String skill;
		long startXp;
		long xp;
		int startLevel;
		int level;

		SkillLine()
		{
		}

		SkillLine(String skill, long xp, int level)
		{
			this.skill = skill;
			this.startXp = xp;
			this.xp = xp;
			this.startLevel = level;
			this.level = level;
		}

		public String getSkill()
		{
			return skill;
		}

		public long gained()
		{
			return Math.max(0, xp - startXp);
		}

		public int getStartLevel()
		{
			return startLevel;
		}

		public int getLevel()
		{
			return level;
		}
	}

	/** One kind of item received, with what it was worth when it dropped. */
	public static class Drop
	{
		int id;
		String name;
		long quantity;
		long price;

		Drop()
		{
		}

		public Drop(int id, String name, long quantity, long price)
		{
			this.id = id;
			this.name = name;
			this.quantity = quantity;
			this.price = price;
		}

		public int getId()
		{
			return id;
		}

		public String getName()
		{
			return name == null ? "Item" : name;
		}

		public long getQuantity()
		{
			return quantity;
		}

		public long getPrice()
		{
			return price;
		}

		public long value()
		{
			return quantity * price;
		}
	}

	/**
	 * Kills of one monster, or clears of one raid or chest. Counted from loot and from the
	 * game's own kill count messages, whichever saw more.
	 */
	public static class KillLine
	{
		private static final Pattern NOT_KILLS = Pattern.compile(
			"(?i)\\b(?:bird nest|supply crate|reward pool|reward cart|casket|clue scroll|loot sack|seed pack|herbiboar|impling|spoils of war)");

		String name;
		int looted;
		int firstKc;
		int lastKc;
		/**
		 * The first and last count of each counter the game keeps for it, by the name it gave:
		 * entry and expert Tombs of Amascut, or normal and challenge mode Chambers, count apart.
		 * Null in sessions saved before it, which kept one counter in firstKc and lastKc.
		 */
		Map<String, int[]> counters;
		/**
		 * True when its loot came from something opened or gathered rather than killed (a bird nest,
		 * a supply crate, a clue casket), false once a monster's loot fed it. Null in older saves.
		 */
		Boolean event;
		/**
		 * Kills looted while a slayer task was assigned, by the task's name as the game writes it
		 * ("Abyssal demons"), and under "" those looted with no task. A kill of anything counts under
		 * the task of the moment, whether the task covers it or not. Null in sessions saved before
		 * it was kept; kills the game counted without loot are in neither.
		 */
		Map<String, Integer> tasks;

		KillLine()
		{
		}

		KillLine(String name)
		{
			this.name = name;
		}

		public String getName()
		{
			return name;
		}

		public int count()
		{
			int counted = 0;
			if (counters != null)
			{
				for (int[] range : counters.values())
				{
					counted += range[1] - range[0] + 1;
				}
			}
			else if (firstKc > 0)
			{
				counted = lastKc - firstKc + 1;
			}
			return Math.max(looted, counted);
		}

		/**
		 * Whether this line is real kills: a monster's loot, or a boss, raid or chest the game kept a kill
		 * count for. Loot from a bird nest, a supply crate or a clue casket is not a kill.
		 */
		public boolean isKill()
		{
			if (firstKc > 0 || counters != null && !counters.isEmpty())
			{
				return true;
			}
			if (event != null)
			{
				return !event;
			}
			// Saved before the source was kept: judge by the name the Loot Tracker gives such loot.
			return name == null || !NOT_KILLS.matcher(name).find();
		}

		/** Kills looted under a slayer task by its name, or empty in older saves. */
		public Map<String, Integer> getTasks()
		{
			return tasks == null ? Collections.emptyMap() : Collections.unmodifiableMap(tasks);
		}

		/** The game's kill count after the latest kill, or 0 if the game never said. */
		public int getKillCount()
		{
			return lastKc;
		}
	}
}
