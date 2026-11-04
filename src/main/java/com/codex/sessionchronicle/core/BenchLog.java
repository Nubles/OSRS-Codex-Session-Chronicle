package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One account's make-X actions, learnt from its own play and kept apart from its sessions: for each
 * recipe (the items one action uses up) the product it makes, the ticks between actions in a row, how
 * long the player takes to bank and start the next load, and how many actions a load holds. Also the
 * bank's count of every item a recipe uses, as last seen. Saved as JSON under the account's profile.
 * Nothing here is a rate from the wiki: every figure is the player's own.
 */
public final class BenchLog
{
	/** Recent ticks between actions kept for each recipe; the pace is their median. */
	static final int RECENT = 40;
	/** Recent bank trips and loads kept for each recipe. */
	static final int TRIPS = 12;
	/** Timed actions a recipe needs before its pace is told. */
	public static final int MIN_SAMPLES = 3;
	/** Recipes kept; the one used longest ago goes first. */
	static final int MAX_RECIPES = 60;

	/** Each recipe by its key, see {@link #key}. */
	Map<String, Recipe> recipes = new LinkedHashMap<>();
	/** The bank's count of each item some recipe uses, as last seen; an item seen absent is kept as 0. */
	Map<Integer, Integer> bank = new TreeMap<>();
	/** When the bank was last seen, epoch ms; 0 if never. */
	long bankSeenAt;

	/** One make-X action: what it uses, what it makes, and the player's own timing of it. */
	public static final class Recipe
	{
		/** Items one action uses up, by id. */
		Map<Integer, Integer> inputs;
		/** The item it was seen making first. */
		int product;
		String name;
		/** The skill whose experience came with it, or null when none did (unfinished potions). */
		String skill;
		/** Ticks between actions in a row, oldest first. */
		List<Integer> intervals;
		/** Ticks from the last action of a load to the first of the next, around a bank trip. */
		List<Integer> gaps;
		/** Actions in each load the player started. */
		List<Integer> loads;
		/** Actions counted in all. */
		int actions;
		/** Whether a last, short action was seen (fifteen shafts asked for, seven left): left is then rounded up. */
		boolean partial;
		long lastAt;

		Recipe()
		{
		}

		Recipe(Map<Integer, Integer> inputs, int product, String name, String skill)
		{
			this.inputs = new TreeMap<>(inputs);
			this.product = product;
			this.name = name;
			this.skill = skill;
			intervals = new ArrayList<>();
			gaps = new ArrayList<>();
			loads = new ArrayList<>();
		}

		public String key()
		{
			return BenchLog.key(inputs);
		}

		public Map<Integer, Integer> getInputs()
		{
			return Collections.unmodifiableMap(inputs);
		}

		public int getProduct()
		{
			return product;
		}

		public String getName()
		{
			return name;
		}

		public String getSkill()
		{
			return skill;
		}

		public int getActions()
		{
			return actions;
		}

		public int timed()
		{
			return intervals.size();
		}

		/** The player's own ticks per action, the median of recent ones; -1 until enough are timed. */
		public double paceTicks()
		{
			return intervals.size() < MIN_SAMPLES ? -1 : median(intervals);
		}

		/** Ticks a bank trip adds beyond the action itself; -1 until one is timed. */
		public double reloadTicks()
		{
			double pace = paceTicks();
			if (gaps.isEmpty() || pace < 0)
			{
				return -1;
			}
			return Math.max(0, median(gaps) - pace);
		}

		/** Actions in a usual load; -1 until one is seen. */
		public int loadSize()
		{
			return loads.isEmpty() ? -1 : (int) Math.round(median(loads));
		}

		/** How many more actions these items allow. */
		public int left(Map<Integer, Integer> items)
		{
			int least = Integer.MAX_VALUE;
			for (Map.Entry<Integer, Integer> e : inputs.entrySet())
			{
				int have = Math.max(0, items.getOrDefault(e.getKey(), 0));
				int per = Math.max(1, e.getValue());
				int n = partial ? (have + per - 1) / per : have / per;
				least = Math.min(least, n);
			}
			return least == Integer.MAX_VALUE ? 0 : least;
		}

		void interval(int ticks)
		{
			add(intervals, ticks, RECENT);
		}

		void gap(int ticks)
		{
			add(gaps, ticks, TRIPS);
		}

		void load(int actions)
		{
			add(loads, actions, TRIPS);
		}

		private Recipe repair()
		{
			if (intervals == null)
			{
				intervals = new ArrayList<>();
			}
			if (gaps == null)
			{
				gaps = new ArrayList<>();
			}
			if (loads == null)
			{
				loads = new ArrayList<>();
			}
			intervals.removeIf(i -> i == null || i <= 0);
			gaps.removeIf(i -> i == null || i <= 0);
			loads.removeIf(i -> i == null || i <= 0);
			if (name == null)
			{
				name = "Item " + product;
			}
			return this;
		}
	}

	/** "1511x1,946x0": each input id and how many one action uses, by id. */
	static String key(Map<Integer, Integer> inputs)
	{
		StringBuilder b = new StringBuilder();
		for (Map.Entry<Integer, Integer> e : new TreeMap<>(inputs).entrySet())
		{
			if (b.length() > 0)
			{
				b.append(',');
			}
			b.append(e.getKey()).append('x').append(e.getValue());
		}
		return b.toString();
	}

	/** Reads a saved log; an empty or damaged save gives an empty one. */
	public static BenchLog read(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return new BenchLog();
		}
		try
		{
			BenchLog log = gson.fromJson(json, BenchLog.class);
			return log == null ? new BenchLog() : log.repair();
		}
		catch (JsonParseException | IllegalStateException | ClassCastException | NumberFormatException e)
		{
			return new BenchLog();
		}
	}

	private BenchLog repair()
	{
		Map<String, Recipe> mended = new LinkedHashMap<>();
		if (recipes != null)
		{
			for (Recipe r : recipes.values())
			{
				if (r != null && r.inputs != null && !r.inputs.isEmpty())
				{
					r.inputs = new TreeMap<>(r.inputs);
					mended.put(r.key(), r.repair());
				}
			}
		}
		recipes = mended;
		bank = bank == null ? new TreeMap<>() : new TreeMap<>(bank);
		return this;
	}

	public BenchLog copy(Gson gson)
	{
		return read(gson, gson.toJson(this));
	}

	/** The recipe whose inputs are exactly these item ids, or null. */
	Recipe byItems(java.util.Set<Integer> ids)
	{
		for (Recipe r : recipes.values())
		{
			if (r.inputs.keySet().equals(ids))
			{
				return r;
			}
		}
		return null;
	}

	public Recipe get(String key)
	{
		return key == null ? null : recipes.get(key);
	}

	public int size()
	{
		return recipes.size();
	}

	Recipe learn(Map<Integer, Integer> inputs, int product, String name, String skill, Map<Integer, Integer> bankNow)
	{
		Recipe r = new Recipe(inputs, product, name, skill);
		recipes.put(r.key(), r);
		if (bankNow != null)
		{
			for (Integer id : inputs.keySet())
			{
				bank.put(id, bankNow.getOrDefault(id, 0));
			}
		}
		while (recipes.size() > MAX_RECIPES)
		{
			String oldest = null;
			long at = Long.MAX_VALUE;
			for (Recipe each : recipes.values())
			{
				if (each.lastAt < at && each != r)
				{
					at = each.lastAt;
					oldest = each.key();
				}
			}
			recipes.remove(oldest);
		}
		return r;
	}

	/** The bank was seen: keeps its count of every item a recipe uses. Returns whether anything moved. */
	boolean sawBank(Map<Integer, Integer> items, long now)
	{
		boolean moved = bankSeenAt == 0;
		for (Recipe r : recipes.values())
		{
			for (Integer id : r.inputs.keySet())
			{
				Integer was = bank.put(id, items.getOrDefault(id, 0));
				moved |= was == null || was.intValue() != items.getOrDefault(id, 0);
			}
		}
		// Items no recipe uses any more are dropped, so the save stays small.
		for (Iterator<Integer> it = bank.keySet().iterator(); it.hasNext(); )
		{
			Integer id = it.next();
			boolean used = false;
			for (Recipe r : recipes.values())
			{
				used |= r.inputs.containsKey(id);
			}
			if (!used)
			{
				it.remove();
			}
		}
		bankSeenAt = now;
		return moved;
	}

	public long getBankSeenAt()
	{
		return bankSeenAt;
	}

	/** The bank's count of an item as last seen, or -1 when the bank has never been seen with this recipe known. */
	public int banked(int id)
	{
		Integer n = bank.get(id);
		return bankSeenAt == 0 || n == null ? -1 : n;
	}

	static double median(List<Integer> values)
	{
		List<Integer> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		int n = sorted.size();
		if (n == 0)
		{
			return 0;
		}
		return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
	}

	private static void add(List<Integer> list, int value, int cap)
	{
		list.add(value);
		while (list.size() > cap)
		{
			list.remove(0);
		}
	}
}
