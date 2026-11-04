package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Watches the pack tick by tick for make-X actions (fletching, smithing, cooking, herblore, crafting
 * and the like) and keeps the batch under way: how many actions are done, how many the pack still
 * holds, and, at the player's own pace from {@link BenchLog}, how long that takes and how long the
 * pack and bank together last.
 *
 * An action is a tick on which some items in the pack go down and others come up, with no bank, shop
 * or trade open and nothing eaten or drunk. A new kind of action is learnt when experience in a
 * skilling skill comes with it, or, with no experience (unfinished potions), when the very same items
 * go down three times in a row. No item, animation or tick count is written in: the recipe, its pace
 * and the bank trips are all read from what the player does.
 */
public final class Bench
{
	/** A game tick in milliseconds (RuneLite's Constants.GAME_TICK_LENGTH; the wiki's "0.6 seconds"). */
	public static final int TICK_MS = 600;
	/** Ticks with no action after which the batch is over, unless the player's own pace is slower. */
	static final int MIN_GAP = 20;
	/** A batch is over after this many of the player's own actions pass with none. */
	static final double GAP_PACES = 3;
	/** Ticks from one load's end to the next load's first action still counted as a bank trip, five minutes. */
	static final int TRIP_TICKS = 500;
	/** The same items going down this many times in a row, with no experience, make a recipe. */
	static final int REPEATS = 3;

	/** The pack as last read, or null until read afresh. */
	private Map<Integer, Integer> last;
	/** The full bank as last seen this run, for a recipe learnt after the bank closed. */
	private Map<Integer, Integer> bankNow;

	/** The recipe of the batch under way or just ended, by key; null for none. */
	private String current;
	private int done;
	private int lastTick = -1;
	private int leftAtLast;
	/** A bank, shop or trade was open since the last action: the next batch is a fresh load. */
	private boolean restocked;

	/** Items going down with no experience, waiting to repeat before they are taken as a recipe. */
	private String candidate;
	private int candidateCount;
	private int candidateTick;
	private final List<Integer> candidateIntervals = new ArrayList<>();

	/** Forgets the pack and the batch: after a hop, a logout or a new account. */
	public void reset()
	{
		last = null;
		current = null;
		done = 0;
		lastTick = -1;
		restocked = false;
		candidate = null;
		candidateIntervals.clear();
	}

	/** The pack will change for reasons other than an action this tick (a bank closing, a death). */
	public void forget()
	{
		last = null;
	}

	/** The bank was open: keeps its counts for the supplies and marks the next load as fresh. */
	public boolean sawBank(Map<Integer, Integer> items, long now, BenchLog log)
	{
		bankNow = new HashMap<>(items);
		restocked = true;
		return log.sawBank(items, now);
	}

	/**
	 * Reads the pack for this tick.
	 *
	 * @param pack the inventory now, item id to count
	 * @param busy a bank, shop, trade or the exchange is open, or the player ate or drank: not an action
	 * @param skill a skilling skill that gained experience this tick or the last, or null
	 * @param names an item's name, for a newly learnt recipe
	 * @return whether the log changed and should be saved
	 */
	public boolean tick(int tick, Map<Integer, Integer> pack, boolean busy, String skill, long now, BenchLog log, IntFunction<String> names)
	{
		Map<Integer, Integer> prev = last;
		last = new HashMap<>(pack);
		if (busy)
		{
			restocked = true;
		}
		if (prev == null || busy)
		{
			return false;
		}
		Map<Integer, Integer> down = new HashMap<>();
		Map<Integer, Integer> up = new HashMap<>();
		for (Map.Entry<Integer, Integer> e : prev.entrySet())
		{
			int now0 = pack.getOrDefault(e.getKey(), 0);
			if (now0 < e.getValue())
			{
				down.put(e.getKey(), e.getValue() - now0);
			}
		}
		for (Map.Entry<Integer, Integer> e : pack.entrySet())
		{
			int was = prev.getOrDefault(e.getKey(), 0);
			if (e.getValue() > was)
			{
				up.put(e.getKey(), e.getValue() - was);
			}
		}
		if (down.isEmpty() || up.isEmpty())
		{
			return false;
		}
		BenchLog.Recipe r = log.byItems(down.keySet());
		int k;
		if (r != null)
		{
			k = multiple(down, r.inputs);
			if (k == 0 && within(down, r.inputs))
			{
				// The last, short action: fewer than one action's worth was left, and it was used.
				r.partial = true;
				k = 1;
			}
			if (k == 0)
			{
				return false;
			}
		}
		else
		{
			int product = most(up);
			if (skill != null)
			{
				r = log.learn(down, product, names.apply(product), skill, bankNow);
				candidate = null;
				k = 1;
			}
			else
			{
				String key = BenchLog.key(down);
				if (key.equals(candidate) && tick - candidateTick <= MIN_GAP)
				{
					candidateIntervals.add(tick - candidateTick);
					candidateCount++;
				}
				else
				{
					candidate = key;
					candidateCount = 1;
					candidateIntervals.clear();
				}
				int before = candidateTick;
				candidateTick = tick;
				if (candidateCount < REPEATS)
				{
					return false;
				}
				r = log.learn(down, product, names.apply(product), null, bankNow);
				// The repeats before this one were actions too: the batch takes them in, and this one follows.
				for (int i = 0; i < candidateIntervals.size() - 1; i++)
				{
					r.interval(candidateIntervals.get(i));
				}
				r.actions += candidateCount - 1;
				current = r.key();
				done = candidateCount - 1;
				lastTick = before;
				restocked = false;
				candidate = null;
				candidateIntervals.clear();
				k = 1;
			}
		}
		action(r, k, tick, pack, now);
		return true;
	}

	private void action(BenchLog.Recipe r, int k, int tick, Map<Integer, Integer> pack, long now)
	{
		int left = r.left(pack);
		String key = r.key();
		boolean same = key.equals(current) && lastTick >= 0 && tick - lastTick <= gapLimit(r);
		if (same)
		{
			if (k == 1)
			{
				r.interval(tick - lastTick);
			}
			done += k;
		}
		else
		{
			if (key.equals(current) && lastTick >= 0 && leftAtLast == 0 && restocked && tick - lastTick <= TRIP_TICKS)
			{
				// The last load ran out, the player banked, and this is the next load's first action.
				r.gap(tick - lastTick);
			}
			if (restocked && left + k >= 2)
			{
				r.load(left + k);
			}
			current = key;
			done = k;
		}
		restocked = false;
		lastTick = tick;
		leftAtLast = left;
		r.actions += k;
		r.lastAt = now;
	}

	/** Ticks with no action after which this recipe's batch is over. */
	static int gapLimit(BenchLog.Recipe r)
	{
		double pace = r.paceTicks();
		return Math.max(MIN_GAP, pace < 0 ? 0 : (int) Math.ceil(pace * GAP_PACES));
	}

	/** The whole number of actions these items going down make, or 0 if they are not a multiple of one. */
	static int multiple(Map<Integer, Integer> down, Map<Integer, Integer> inputs)
	{
		int k = -1;
		for (Map.Entry<Integer, Integer> e : inputs.entrySet())
		{
			int d = down.getOrDefault(e.getKey(), 0);
			int per = Math.max(1, e.getValue());
			if (d % per != 0)
			{
				return 0;
			}
			if (k < 0)
			{
				k = d / per;
			}
			else if (k != d / per)
			{
				return 0;
			}
		}
		return Math.max(0, k);
	}

	private static boolean within(Map<Integer, Integer> down, Map<Integer, Integer> inputs)
	{
		for (Map.Entry<Integer, Integer> e : inputs.entrySet())
		{
			int d = down.getOrDefault(e.getKey(), 0);
			if (d <= 0 || d > e.getValue())
			{
				return false;
			}
		}
		return true;
	}

	/** The item that came up most, the lowest id on a tie. */
	private static int most(Map<Integer, Integer> up)
	{
		int best = -1;
		int count = -1;
		for (Map.Entry<Integer, Integer> e : up.entrySet())
		{
			if (e.getValue() > count || e.getValue() == count && e.getKey() < best)
			{
				best = e.getKey();
				count = e.getValue();
			}
		}
		return best;
	}

	/** The batch under way, or the load just finished while the player banks; null for none. */
	public Progress progress(int tick, BenchLog log)
	{
		BenchLog.Recipe r = log.get(current);
		if (r == null || lastTick < 0 || last == null)
		{
			return null;
		}
		int since = Math.max(0, tick - lastTick);
		int left = r.left(last);
		boolean running = left > 0 && since <= gapLimit(r);
		if (!running && since > TRIP_TICKS)
		{
			return null;
		}
		double pace = r.paceTicks();
		// The action under way is part done: what has passed since the last one comes off, up to one action.
		double under = running && pace > 0 ? Math.min(since, pace) : 0;
		long loadMs = pace < 0 ? -1 : Math.max(0, Math.round((left * pace - under) * TICK_MS));

		int total = -1;
		long totalMs = -1;
		int reloads = 0;
		boolean reloadKnown = true;
		if (log.getBankSeenAt() > 0)
		{
			Map<Integer, Integer> all = new HashMap<>();
			boolean known = true;
			for (Integer id : r.inputs.keySet())
			{
				int banked = log.banked(id);
				known &= banked >= 0;
				all.put(id, last.getOrDefault(id, 0) + Math.max(0, banked));
			}
			if (known)
			{
				total = r.left(all);
				int rest = Math.max(0, total - left);
				int load = r.loadSize();
				if (load <= 0)
				{
					load = Math.max(1, done + left);
				}
				reloads = rest <= 0 ? 0 : (rest + load - 1) / load;
				double reload = r.reloadTicks();
				reloadKnown = reloads == 0 || reload >= 0;
				if (pace > 0)
				{
					totalMs = Math.max(0, Math.round((total * pace - under + reloads * Math.max(0, reload)) * TICK_MS));
				}
			}
		}
		return new Progress(r.getName(), r.getProduct(), r.getSkill(), done, left, running, since * (long) TICK_MS, pace, r.timed(),
			r.getActions(), loadMs, total, totalMs, reloads, reloadKnown, log.getBankSeenAt());
	}

	/** What the panel and the plaque show of the batch. */
	public static final class Progress
	{
		private final String name;
		private final int product;
		private final String skill;
		private final int done;
		private final int left;
		private final boolean running;
		private final long idleMs;
		private final double paceTicks;
		private final int timed;
		private final int actions;
		private final long loadMs;
		private final int total;
		private final long totalMs;
		private final int reloads;
		private final boolean reloadKnown;
		private final long bankSeenAt;

		public Progress(String name, int product, String skill, int done, int left, boolean running, long idleMs, double paceTicks,
			int timed, int actions, long loadMs, int total, long totalMs, int reloads, boolean reloadKnown, long bankSeenAt)
		{
			this.name = name;
			this.product = product;
			this.skill = skill;
			this.done = done;
			this.left = left;
			this.running = running;
			this.idleMs = idleMs;
			this.paceTicks = paceTicks;
			this.timed = timed;
			this.actions = actions;
			this.loadMs = loadMs;
			this.total = total;
			this.totalMs = totalMs;
			this.reloads = reloads;
			this.reloadKnown = reloadKnown;
			this.bankSeenAt = bankSeenAt;
		}

		public String getName()
		{
			return name;
		}

		public int getProduct()
		{
			return product;
		}

		/** The skill whose experience comes with it, or null (unfinished potions give none). */
		public String getSkill()
		{
			return skill;
		}

		/** Actions done in this load. */
		public int getDone()
		{
			return done;
		}

		/** Actions the pack still holds. */
		public int getLeft()
		{
			return left;
		}

		/** Whether the batch is under way; false while the player banks or has stopped. */
		public boolean isRunning()
		{
			return running;
		}

		public long getIdleMs()
		{
			return idleMs;
		}

		/** The player's own ticks per action; below 0 while it is still being learnt. */
		public double getPaceTicks()
		{
			return paceTicks;
		}

		/** Actions timed so far toward the pace. */
		public int getTimed()
		{
			return timed;
		}

		/** Actions of this kind counted in all, across sessions. */
		public int getActions()
		{
			return actions;
		}

		/** Time left on the pack at the player's pace; below 0 while the pace is still being learnt. */
		public long getLoadMs()
		{
			return loadMs;
		}

		/** Actions the pack and bank together hold; below 0 until the bank has been seen. */
		public int getTotal()
		{
			return total;
		}

		/** How long the pack and bank last, bank trips included; below 0 when unknown. */
		public long getTotalMs()
		{
			return totalMs;
		}

		/** Bank trips the bank's supplies take. */
		public int getReloads()
		{
			return reloads;
		}

		/** Whether the bank trips are timed from the player's own; false adds "plus banking". */
		public boolean isReloadKnown()
		{
			return reloadKnown;
		}

		public long getBankSeenAt()
		{
			return bankSeenAt;
		}

		/** The load's share done, 0 to 1. */
		public double share()
		{
			int all = done + left;
			return all <= 0 ? 1 : done / (double) all;
		}

		/** Seconds per action at the player's pace, or below 0. */
		public double secondsEach()
		{
			return paceTicks < 0 ? -1 : paceTicks * TICK_MS / 1000.0;
		}

		/** A key that changes only when what is shown changes, so the panel is not rebuilt for nothing. */
		public String key()
		{
			return name + "/" + done + "/" + left + "/" + running + "/" + (loadMs / 1000) + "/" + total + "/" + (totalMs / 60_000)
				+ "/" + reloads + "/" + reloadKnown + "/" + timed + "/" + Math.round(paceTicks * 10);
		}
	}
}
