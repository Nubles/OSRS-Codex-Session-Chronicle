package com.codex.sessionchronicle.core;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

/**
 * The facts of Fossil Island's Underwater Agility and Thieving, each from the Old School wiki
 * (Underwater Agility and Thieving, Glistening tear, Mairin's Market) or RuneLite's gameval ids;
 * the map regions are the ones RuneLite's own Discord plugin names "Underwater". Nothing here is a
 * rate the player is told as theirs: those are learnt from their own dives (see {@link DiveWatch}).
 */
public final class Underwater
{
	private Underwater()
	{
	}

	/** The map regions RuneLite's Discord plugin calls "Underwater" (REGION_UNDERWATER). */
	static final int[] REGIONS = {15008, 15264};

	/** Experience a successful search gives in both Agility and Thieving: "4.5 Agility and Thieving experience". */
	public static final double SEARCH_XP = 4.5;

	/** "Players can only exchange a maximum of 700 glistening tears at once"; the rest are discarded. */
	public static final int EXCHANGE_CAP = 700;

	/** The wiki's "around 170–230 tears can be collected per hour", low and high. */
	public static final int WIKI_TEARS_LOW = 170;
	public static final int WIKI_TEARS_HIGH = 230;

	/** Mermaid's tears lost on reaching 0% oxygen: "between 10 and 20". */
	public static final int DROWN_LOSS_MIN = 10;
	public static final int DROWN_LOSS_MAX = 20;

	/** A clam's or chest's experience in one tick is at most a few searches; more than this is Mairin's. */
	static final int SEARCH_XP_MAX = 20;

	public static boolean isRegion(int region)
	{
		for (int r : REGIONS)
		{
			if (r == region)
			{
				return true;
			}
		}
		return false;
	}

	/** Every id the game shows a stack of mermaid's tears under. */
	public static boolean isMermaidTear(int itemId)
	{
		return itemId == ItemID.FOSSIL_MERMAID_TEAR || itemId == ItemID.FOSSIL_MERMAID_TEAR_2 || itemId == ItemID.FOSSIL_MERMAID_TEAR_3
			|| itemId == ItemID.FOSSIL_MERMAID_TEAR_4 || itemId == ItemID.FOSSIL_MERMAID_TEAR_5;
	}

	public static boolean isGlisteningTear(int itemId)
	{
		return itemId == ItemID.FOSSIL_GLISTENING_TEAR;
	}

	/** What the player searched: a clam, a chest, or something else (or nothing known). */
	public enum Spot
	{
		CLAM, CHEST, OTHER;

		/** The spot a game object id is, from the open and closed underwater clams and chests. */
		public static Spot of(int objectId)
		{
			switch (objectId)
			{
				case ObjectID.FOSSIL_CLAM_OPEN:
				case ObjectID.FOSSIL_CLAM_CLOSED:
					return CLAM;
				case ObjectID.FOSSIL_CHEST_OPEN:
				case ObjectID.FOSSIL_CHEST_CLOSED:
					return CHEST;
				default:
					return OTHER;
			}
		}
	}

	/**
	 * How Mairin pays for a glistening tear. The wiki gives, choosing both skills,
	 * E_A = 3 x A^2 x 9/10 / 150 = 0.018 x A^2 and E_T = 11 x T^2 x 9/10 / 150 = 0.066 x T^2, floored to a tenth,
	 * and "When exchanging for one skill, experience is multiplied by 1.5". The wiki's own check at 99:
	 * 176.4 and 646.8 for both, 264.6 Agility alone, 970.2 Thieving alone.
	 */
	public enum Mode
	{
		BOTH("Agility and Thieving", "Both skills"),
		AGILITY("Agility", "Agility only"),
		THIEVING("Thieving", "Thieving only");

		private final String label;
		private final String shortLabel;

		Mode(String label, String shortLabel)
		{
			this.label = label;
			this.shortLabel = shortLabel;
		}

		public String getLabel()
		{
			return label;
		}

		/** "Both skills", "Agility only", "Thieving only". */
		public String getShortLabel()
		{
			return shortLabel;
		}

		/** Agility experience for one tear at this Agility level, in tenths. */
		public int agilityTenths(int level)
		{
			long sq = (long) Math.max(1, Math.min(99, level)) * Math.max(1, Math.min(99, level));
			switch (this)
			{
				case BOTH:
					return (int) (27 * sq / 150);
				case AGILITY:
					return (int) (81 * sq / 300);
				default:
					return 0;
			}
		}

		/** Thieving experience for one tear at this Thieving level, in tenths. */
		public int thievingTenths(int level)
		{
			long sq = (long) Math.max(1, Math.min(99, level)) * Math.max(1, Math.min(99, level));
			switch (this)
			{
				case BOTH:
					return (int) (99 * sq / 150);
				case THIEVING:
					return (int) (297 * sq / 300);
				default:
					return 0;
			}
		}

		/** The mode a hand-in was, from the experience it gave; null when it gave none. */
		public static Mode of(long agilityXp, long thievingXp)
		{
			if (agilityXp > 0 && thievingXp > 0)
			{
				return BOTH;
			}
			return agilityXp > 0 ? AGILITY : thievingXp > 0 ? THIEVING : null;
		}
	}

	/** What Mairin's Market sells for mermaid's tears, in her shop's order, with its price. */
	public enum Reward
	{
		SMALL_FOSSIL("Unidentified small fossil", "Small fossil", 100, ItemID.FOSSIL_SMALL_UNID),
		MEDIUM_FOSSIL("Unidentified medium fossil", "Medium fossil", 200, ItemID.FOSSIL_MEDIUM_UNID),
		LARGE_FOSSIL("Unidentified large fossil", "Large fossil", 300, ItemID.FOSSIL_LARGE_UNID),
		MERFOLK_TRIDENT("Merfolk trident", "Merfolk trident", 400, ItemID.MERFOLK_TRIDENT),
		SEAWEED_SPORE("Seaweed spore", "Seaweed spore", 20, ItemID.SEAWEED_SEED),
		BOWL_OF_FISH("Bowl of fish", "Bowl of fish", 30, ItemID.BOWL_OF_FISH);

		private final String name;
		private final String shortName;
		private final int cost;
		private final int itemId;

		Reward(String name, String shortName, int cost, int itemId)
		{
			this.name = name;
			this.shortName = shortName;
			this.cost = cost;
			this.itemId = itemId;
		}

		public String getName()
		{
			return name;
		}

		public String getShortName()
		{
			return shortName;
		}

		/** The price in mermaid's tears. */
		public int getCost()
		{
			return cost;
		}

		public int getItemId()
		{
			return itemId;
		}

		@Override
		public String toString()
		{
			return name + " (" + cost + ")";
		}
	}
}
