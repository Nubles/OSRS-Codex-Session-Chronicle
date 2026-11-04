package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Follows the random event under way, from the moment its NPC turns to the player to the moment it
 * ends, and says how it ended and what it gave. It reads only what the game shows: the event NPC
 * facing the player (as RuneLite's own Random Events plugin listens for), the player's Talk-to or
 * Dismiss on it, its despawn, where the player stands and what the pack holds.
 * <p>
 * An event left alone is ignored once its NPC goes (the wiki: it "disappears after a minute"). An
 * event the player talks to either stays where they are (a genie, a certer) or takes them away to its
 * own place and back (a drill demon, a mime). Its reward is what the pack gained: for one that stays,
 * the first items gained after talking; for one that takes the player away, what the pack gained by
 * a few ticks after they are back where they were.
 */
public final class RandomWatch
{
	/** What an event is filed under when the player gained no experience before it. */
	public static final String NO_ACTIVITY = "Nothing in particular";
	/** Ticks after a click before the pack is read as it stood: an action already under way may still land. */
	static final int SETTLE_TICKS = 2;
	/** Ticks after the event NPC goes, at the player's spot, with nothing gained, before the event counts as ended. */
	static final int GONE_TICKS = 8;
	/** Ticks after coming back from an event's own place before what it gave is read. */
	static final int BACK_TICKS = 3;
	/** Tiles from where the event was taken that count as taken away to its own place, and as back. */
	static final int AWAY_TILES = 40;
	static final int BACK_TILES = 12;
	/** An event left alone this long without its NPC going is ignored (two minutes). */
	static final int UNANSWERED_TICKS = 200;
	/** An event taken part in this long is closed, whatever it gave (twenty minutes). */
	static final int LONGEST_TICKS = 2000;

	/** Where the player stands. */
	public static final class Spot
	{
		final int x;
		final int y;
		final int plane;

		public Spot(int x, int y, int plane)
		{
			this.x = x;
			this.y = y;
			this.plane = plane;
		}

		int distance(Spot o)
		{
			return o.plane != plane ? Integer.MAX_VALUE : Math.max(Math.abs(o.x - x), Math.abs(o.y - y));
		}
	}

	private static final class Open
	{
		RandomEvents.Event event;
		int npc;
		long at;
		int spawnTick;
		String activity;
		boolean accepted;
		boolean dismissed;
		int acceptTick = -1;
		int despawnTick = -1;
		Map<Integer, Long> baseline;
		Spot from;
		boolean away;
		int backTick = -1;
	}

	private Open open;

	/** Whether an event is under way. */
	public boolean isOpen()
	{
		return open != null;
	}

	/** The event under way, or null. */
	public RandomEvents.Event current()
	{
		return open == null ? null : open.event;
	}

	/**
	 * An event NPC turned to the player.
	 *
	 * @param npc the NPC's index, to know it again
	 * @return an event that was still open and is now filed, or null
	 */
	public RandomLog.Seen spawned(RandomEvents.Event event, int npc, int tick, long now, String activity)
	{
		if (event == null)
		{
			return null;
		}
		if (open != null && open.npc == npc && open.event == event)
		{
			return null;
		}
		if (open != null && open.accepted)
		{
			// One event at a time: the game does not spawn a second while one is drawing the player's attention.
			// A new NPC facing the player while one is taken part in belongs to it (inside its own place).
			return null;
		}
		RandomLog.Seen filed = open == null ? null : close(RandomLog.Outcome.IGNORED, Collections.emptyList());
		open = new Open();
		open.event = event;
		open.npc = npc;
		open.at = now;
		open.spawnTick = tick;
		open.activity = activity;
		return filed;
	}

	/**
	 * The player clicked an option on an event NPC: Talk-to (or Pick on a strange plant) takes part,
	 * Dismiss sends it away. A strange plant never faces the player, so a click on one opens its event.
	 *
	 * @param pack the pack as it stands, item id to quantity
	 */
	public RandomLog.Seen clicked(RandomEvents.Event event, int npc, String option, int tick, long now, String activity,
		Spot spot, Map<Integer, Long> pack)
	{
		if (event == null || option == null)
		{
			return null;
		}
		String o = option.toLowerCase(java.util.Locale.ROOT);
		boolean dismiss = o.equals("dismiss");
		boolean take = o.equals("talk-to") || o.equals("pick");
		if (!dismiss && !take)
		{
			return null;
		}
		RandomLog.Seen filed = null;
		if (open == null || open.npc != npc || open.event != event)
		{
			if (event != RandomEvents.Event.STRANGE_PLANT || open != null && open.accepted)
			{
				// Someone else's event, or one already under way.
				return null;
			}
			filed = spawned(event, npc, tick, now, activity);
		}
		if (dismiss)
		{
			if (!open.accepted)
			{
				open.dismissed = true;
			}
		}
		else if (!open.accepted)
		{
			open.accepted = true;
			open.acceptTick = tick;
			open.from = spot;
			open.baseline = pack == null ? new HashMap<>() : new HashMap<>(pack);
		}
		return filed;
	}

	/** An NPC left the scene. */
	public void despawned(int npc, int tick)
	{
		if (open != null && open.npc == npc && open.despawnTick < 0)
		{
			open.despawnTick = tick;
		}
	}

	/**
	 * Each game tick: closes the event under way once it has ended.
	 *
	 * @param spot where the player stands, or null when not known
	 * @param pack the pack as it stands, item id to quantity, or null when not read
	 * @param names an item's name by id, for the reward
	 * @return the event filed this tick, or null
	 */
	public RandomLog.Seen tick(int tick, Spot spot, Map<Integer, Long> pack, IntFunction<String> names)
	{
		if (open == null)
		{
			return null;
		}
		if (open.dismissed)
		{
			return close(RandomLog.Outcome.DISMISSED, Collections.emptyList());
		}
		if (!open.accepted)
		{
			if (open.despawnTick >= 0 || tick - open.spawnTick > UNANSWERED_TICKS)
			{
				return close(RandomLog.Outcome.IGNORED, Collections.emptyList());
			}
			return null;
		}
		if (pack != null && tick - open.acceptTick <= SETTLE_TICKS)
		{
			// What lands just after the click is the action the player was already doing.
			open.baseline = new HashMap<>(pack);
			return null;
		}
		if (spot != null && open.from != null)
		{
			int d = spot.distance(open.from);
			if (!open.away && d > AWAY_TILES)
			{
				open.away = true;
			}
			else if (open.away && open.backTick < 0 && d <= BACK_TILES)
			{
				open.backTick = tick;
			}
		}
		if (tick - open.acceptTick > LONGEST_TICKS)
		{
			return close(open.away ? RandomLog.Outcome.TAKEN : outcome(gained(pack, names)), gained(open.away ? null : pack, names));
		}
		if (open.away)
		{
			if (open.backTick >= 0 && tick - open.backTick >= BACK_TICKS)
			{
				List<RandomLog.Gain> gains = gained(pack, names);
				return close(outcome(gains), gains);
			}
			return null;
		}
		List<RandomLog.Gain> gains = gained(pack, names);
		if (!gains.isEmpty())
		{
			return close(RandomLog.Outcome.DONE, gains);
		}
		if (open.despawnTick >= 0 && tick - open.despawnTick >= GONE_TICKS)
		{
			return close(RandomLog.Outcome.TAKEN, Collections.emptyList());
		}
		return null;
	}

	/** Ends the event under way without its reward, after a hop or a logout; null for none. */
	public RandomLog.Seen reset()
	{
		if (open == null)
		{
			return null;
		}
		RandomLog.Outcome o = open.accepted ? RandomLog.Outcome.TAKEN : open.dismissed ? RandomLog.Outcome.DISMISSED : RandomLog.Outcome.IGNORED;
		return close(o, Collections.emptyList());
	}

	private static RandomLog.Outcome outcome(List<RandomLog.Gain> gains)
	{
		return gains.isEmpty() ? RandomLog.Outcome.TAKEN : RandomLog.Outcome.DONE;
	}

	/** What the pack holds more of than when the player took part, most first. */
	private List<RandomLog.Gain> gained(Map<Integer, Long> pack, IntFunction<String> names)
	{
		List<RandomLog.Gain> out = new ArrayList<>();
		if (pack == null || open.baseline == null)
		{
			return out;
		}
		for (Map.Entry<Integer, Long> e : pack.entrySet())
		{
			long more = e.getValue() - open.baseline.getOrDefault(e.getKey(), 0L);
			if (more > 0)
			{
				String name = names == null ? null : names.apply(e.getKey());
				out.add(new RandomLog.Gain(e.getKey(), name, more));
			}
		}
		out.sort((a, b) -> Long.compare(b.getQty(), a.getQty()));
		return out;
	}

	private RandomLog.Seen close(RandomLog.Outcome outcome, List<RandomLog.Gain> reward)
	{
		RandomLog.Seen s = new RandomLog.Seen(open.event, open.at, open.activity, outcome, reward);
		open = null;
		return s;
	}
}
