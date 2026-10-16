package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

	public int totalKills()
	{
		int total = 0;
		for (KillLine line : kills.values())
		{
			total += line.count();
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
		String name;
		int looted;
		int firstKc;
		int lastKc;

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
			int counted = firstKc > 0 ? lastKc - firstKc + 1 : 0;
			return Math.max(looted, counted);
		}

		/** The game's kill count after the last kill, or 0 if the game never said. */
		public int getKillCount()
		{
			return lastKc;
		}
	}
}
