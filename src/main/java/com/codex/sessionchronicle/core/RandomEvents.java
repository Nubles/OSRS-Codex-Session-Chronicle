package com.codex.sessionchronicle.core;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * The facts of Old School's random events, each from the Old School wiki (Random events, and each
 * event's own page) or RuneLite's gameval ids. The event NPCs are the ones RuneLite's own Random
 * Events plugin listens for. Nothing here is the player's own rate: that is learnt from the events
 * they meet (see {@link RandomLog}).
 */
public final class RandomEvents
{
	private RandomEvents()
	{
	}

	/** "the game will pick a point in time between 1 and 2 hours in the future relative to the player's last random event." */
	public static final long TIMER_MIN_MS = 60 * 60_000L;
	public static final long TIMER_MAX_MS = 120 * 60_000L;
	/** "A check is made every 5 minutes of in-game time to see if the required duration has elapsed." */
	public static final long CHECK_MS = 5 * 60_000L;
	/** "the timer will be extended by 10 to 70 minutes" when the player is offline as an event falls due. */
	public static final long OFFLINE_MIN_MS = 10 * 60_000L;
	public static final long OFFLINE_MAX_MS = 70 * 60_000L;
	/** An event left alone "disappears after a minute". */
	public static final long IGNORED_MS = 60_000L;

	/** Every random event the game can give, in the wiki's order, with the reward its table gives. */
	public enum Event
	{
		BEEKEEPER("Beekeeper", "Lamp", ItemID.BEEKEEPER_HAT, NpcID.MACRO_BEEKEEPER_INVITATION),
		CAPT_ARNAV("Capt' Arnav's Chest", "Gold ring", ItemID.GOLD_RING, NpcID.MACRO_COMBILOCK_PIRATE),
		CERTERS("Certers", "Kebab", ItemID.KEBAB, NpcID.MACRO_NILES, NpcID.MACRO_MILES, NpcID.MACRO_GILES,
			NpcID.MACRO_NILES_UNDERWATER, NpcID.MACRO_MILES_UNDERWATER, NpcID.MACRO_GILES_UNDERWATER),
		COUNT_CHECK("Count Check", "Lamp", ItemID.MACRO_GENILAMP, NpcID.MACRO_COUNTCHECK_SURFACE, NpcID.MACRO_COUNTCHECK_UNDERWATER),
		DRILL_DEMON("Drill Demon", "Lamp", ItemID.DRILL_TOP, NpcID.MACRO_DRILLDEMON_INVITATION),
		DRUNKEN_DWARF("Drunken Dwarf", "Beer", ItemID.BEER, NpcID.MACRO_DWARF),
		EVIL_BOB("Evil Bob", "650 Fishing experience", ItemID.RAW_SHRIMP, NpcID.MACRO_EVIL_BOB_OUTSIDE),
		EVIL_TWIN("Evil twin", "2-4 uncut sapphires (noted)", ItemID.UNCUT_SAPPHIRE, NpcID.GRAB_POSTMAN),
		FREAKY_FORESTER("Freaky Forester", "Lamp", ItemID.LADERHOSEN_HAT, NpcID.MACRO_FORESTER_INVITATION),
		GENIE("Genie", "Lamp", ItemID.MACRO_GENILAMP, NpcID.MACRO_GENI, NpcID.MACRO_GENI_UNDERWATER),
		GRAVEDIGGER("Gravedigger", "Lamp", ItemID.MACRO_DIGGER_MASK, NpcID.MACRO_GRAVEDIGGER_INVITATION),
		JEKYLL("Jekyll and Hyde", "One of various potions depending on the herb given", -1, NpcID.MACRO_JEKYLL, NpcID.MACRO_JEKYLL_UNDERWATER),
		KISS_THE_FROG("Kiss the frog", "Frog token", ItemID.MACRO_FROG_TOKEN, NpcID.MACRO_FROG_CRIER),
		MAZE("Maze", "Various rewards depending on time spent and chests looted", -1, NpcID.MACRO_MAZE_INVITATION),
		MIME("Mime", "Lamp", ItemID.MACRO_MIME_MASK, NpcID.MACRO_MIME_INVITATION),
		MYSTERIOUS_OLD_MAN("Mysterious Old Man", "Kebab", ItemID.KEBAB, NpcID.MACRO_MYSTERIOUS_OLD_MAN, NpcID.MACRO_MYSTERIOUS_OLD_MAN_UNDERWATER),
		PILLORY("Pillory", "Uncut sapphire", ItemID.UNCUT_SAPPHIRE, NpcID.MACRO_PILLORY_GUARD),
		PINBALL("Pinball", "5 uncut sapphires (noted)", ItemID.UNCUT_SAPPHIRE, NpcID.PINBALL_INVITATION),
		PRISON_PETE("Prison Pete", "6 grimy toadflaxes (noted)", ItemID.UNIDENTIFIED_TOADFLAX, NpcID.MACRO_EVIL_BOB_PRISON),
		QUIZ_MASTER("Quiz Master", "1000 coins", ItemID.COINS, NpcID.MACRO_MAGNESON_INVITATION),
		RICK_TURPENTINE("Rick Turpentine", "Kebab", ItemID.KEBAB, NpcID.MACRO_HIGHWAYMAN, NpcID.MACRO_HIGHWAYMAN_UNDERWATER),
		SANDWICH_LADY("Sandwich lady", "Chocolate bar", ItemID.CHOCOLATE_BAR, NpcID.MACRO_SANDWICH_LADY_NPC),
		/** The plant never turns to the player, so it is logged only when picked or dismissed. */
		STRANGE_PLANT("Strange plant", "Strange fruit", ItemID.MACRO_TRIFFIDFRUIT, NpcID.MACRO_TRIFFIDSEED),
		SURPRISE_EXAM("Surprise Exam", "Book of Knowledge", ItemID.PATTERN_REWARD, NpcID.PATTERN_INVITATION);

		private static final Map<Integer, Event> BY_NPC = new HashMap<>();

		static
		{
			for (Event e : values())
			{
				for (int id : e.npcs)
				{
					BY_NPC.put(id, e);
				}
			}
		}

		private final String name;
		private final String reward;
		private final int iconId;
		private final int[] npcs;

		Event(String name, String reward, int iconId, int... npcs)
		{
			this.name = name;
			this.reward = reward;
			this.iconId = iconId;
			this.npcs = npcs;
		}

		public String getName()
		{
			return name;
		}

		/** The reward as the wiki's table of events gives it. */
		public String getReward()
		{
			return reward;
		}

		/** An item to picture the event by, or -1 for none. */
		public int getIconId()
		{
			return iconId;
		}

		/** The event an NPC id starts, or null. */
		public static Event ofNpc(int npcId)
		{
			return BY_NPC.get(npcId);
		}

		/** The event by its enum name, or null; for saved logs. */
		public static Event named(String name)
		{
			if (name == null)
			{
				return null;
			}
			try
			{
				return valueOf(name);
			}
			catch (IllegalArgumentException e)
			{
				return null;
			}
		}

		/** The costume this event gives pieces of, or null. */
		public Costume getCostume()
		{
			for (Costume c : Costume.values())
			{
				if (c.event == this)
				{
					return c;
				}
			}
			return null;
		}
	}

	/** One piece of a random event costume. */
	public static final class Piece
	{
		private final String name;
		private final int itemId;

		Piece(String name, int itemId)
		{
			this.name = name;
			this.itemId = itemId;
		}

		public String getName()
		{
			return name;
		}

		public int getItemId()
		{
			return itemId;
		}
	}

	/**
	 * The costumes random events give, with the number of completions the wiki's Random events page
	 * says unlock the whole of each ("Needs to be completed 5 times to unlock all beekeeper's outfit
	 * pieces" and so on). Some of those completions unlock emotes, not pieces.
	 */
	public enum Costume
	{
		BEEKEEPER("Beekeeper's outfit", Event.BEEKEEPER, 5,
			new Piece("Beekeeper's hat", ItemID.BEEKEEPER_HAT), new Piece("Beekeeper's top", ItemID.BEEKEEPER_TOP),
			new Piece("Beekeeper's legs", ItemID.BEEKEEPER_LEGS), new Piece("Beekeeper's gloves", ItemID.BEEKEEPER_GLOVES),
			new Piece("Beekeeper's boots", ItemID.BEEKEEPER_BOOTS)),
		CAMOUFLAGE("Camouflage outfit", Event.DRILL_DEMON, 4,
			new Piece("Camo helmet", ItemID.DRILL_HELM), new Piece("Camo top", ItemID.DRILL_TOP), new Piece("Camo bottoms", ItemID.DRILL_BOTTOMS)),
		LEDERHOSEN("Lederhosen outfit", Event.FREAKY_FORESTER, 3,
			new Piece("Lederhosen hat", ItemID.LADERHOSEN_HAT), new Piece("Lederhosen top", ItemID.LADERHOSEN_TOP),
			new Piece("Lederhosen shorts", ItemID.LADERHOSEN_LEGS)),
		ZOMBIE("Zombie outfit", Event.GRAVEDIGGER, 5,
			new Piece("Zombie mask", ItemID.MACRO_DIGGER_MASK), new Piece("Zombie shirt", ItemID.MACRO_DIGGER_SHIRT),
			new Piece("Zombie trousers", ItemID.MACRO_DIGGER_LEGS), new Piece("Zombie gloves", ItemID.MACRO_DIGGER_GLOVES),
			new Piece("Zombie boots", ItemID.MACRO_DIGGER_BOOTS)),
		MIME("Mime outfit", Event.MIME, 7,
			new Piece("Mime mask", ItemID.MACRO_MIME_MASK), new Piece("Mime top", ItemID.MACRO_MIME_TOP),
			new Piece("Mime legs", ItemID.MACRO_MIME_LEGS), new Piece("Mime gloves", ItemID.MACRO_MIME_GLOVES),
			new Piece("Mime boots", ItemID.MACRO_MIME_BOOTS)),
		/** Bought from Thessalia in Varrock with frog tokens: "Needs to be completed 3 times to obtain all 5 pieces". */
		ROYAL_FROG("Royal frog costume", Event.KISS_THE_FROG, 3,
			new Piece("Frog mask", ItemID.MACRO_FROG_MASK), new Piece("Royal frog tunic", ItemID.MACRO_PRINCE_TORSO),
			new Piece("Royal frog leggings", ItemID.MACRO_PRINCE_LEGS), new Piece("Royal frog blouse", ItemID.MACRO_PRINCESS_TORSO),
			new Piece("Royal frog skirt", ItemID.MACRO_PRINCESS_LEGS));

		private static final Map<Integer, Costume> BY_ITEM = new HashMap<>();

		static
		{
			for (Costume c : values())
			{
				for (Piece p : c.pieces)
				{
					BY_ITEM.put(p.itemId, c);
				}
			}
		}

		private final String name;
		private final Event event;
		private final int completions;
		private final Piece[] pieces;

		Costume(String name, Event event, int completions, Piece... pieces)
		{
			this.name = name;
			this.event = event;
			this.completions = completions;
			this.pieces = pieces;
		}

		public String getName()
		{
			return name;
		}

		public Event getEvent()
		{
			return event;
		}

		/** Completions the wiki says unlock all of it. */
		public int getCompletions()
		{
			return completions;
		}

		public Piece[] getPieces()
		{
			return pieces.clone();
		}

		/** Whether an item id is a piece of any costume. */
		public static boolean isPiece(int itemId)
		{
			return BY_ITEM.containsKey(itemId);
		}

		/** The costume pieces among some item ids, as their item ids. */
		public static Set<Integer> among(Collection<Integer> itemIds)
		{
			Set<Integer> found = new java.util.TreeSet<>();
			for (Integer id : itemIds)
			{
				if (id != null && BY_ITEM.containsKey(id))
				{
					found.add(id);
				}
			}
			return found;
		}

		/** Costumes with a piece missing from those owned. */
		public static Set<Costume> unfinished(Collection<Integer> owned)
		{
			Set<Costume> out = EnumSet.noneOf(Costume.class);
			for (Costume c : values())
			{
				for (Piece p : c.pieces)
				{
					if (!owned.contains(p.itemId))
					{
						out.add(c);
						break;
					}
				}
			}
			return out;
		}
	}
}
