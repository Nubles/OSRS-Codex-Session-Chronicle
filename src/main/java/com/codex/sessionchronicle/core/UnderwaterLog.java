package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One account's dives at Fossil Island's Underwater Agility and Thieving, kept apart from its
 * sessions: each recent dive, the totals of every dive (the player's own pace), the tears handed to
 * Mairin and the experience they gave, how the player last chose to be paid, and the mermaid's tears
 * last seen in the bank. Saved as JSON under the account's RuneScape profile.
 */
public final class UnderwaterLog
{
	/** Recent dives kept, newest last. */
	static final int RECENT = 30;

	List<Dive> dives = new ArrayList<>();
	/** Every dive ever filed, summed. */
	Dive total = new Dive();
	/** Glistening tears handed to Mairin, and the experience they gave. */
	long handedIn;
	long handInAgilityXp;
	long handInThievingXp;
	/** How the player was last paid for glistening tears, by enum name; null before any hand-in. */
	String mode;
	/** Mermaid's tears in the bank when it was last open, or -1 if never seen. */
	int bankTears = -1;
	/** The unboosted levels last read, so the log still knows them while logged out; 0 if never read. */
	int agilityLevel;
	int thievingLevel;
	/** Tears an hour of the best dive of ten minutes or more, for a new-best moment; 0 before one. */
	double bestRate;

	/** One dive: from its first tear or search to its last. */
	public static final class Dive
	{
		/** When it began, epoch ms; on the total, the first dive's. */
		long startAt;
		/** Ticks from the first tear or search to the last. */
		long ticks;
		/** Mermaid's tears and glistening tears found. */
		long tears;
		long glistening;
		/** Successful searches of clams, of chests, and of a spot not told apart. */
		int clams;
		int chests;
		int others;
		/** Mermaid's tears lost on running out of air, and spent at Mairin's Market. */
		long lost;
		long spent;
		/** Agility and Thieving experience from searches and obstacles, apart from Mairin's. */
		long agilityXp;
		long thievingXp;
		/** On a total, how many dives it sums; 0 on a single dive. */
		int count;

		public Dive()
		{
		}

		public Dive(long startAt)
		{
			this.startAt = startAt;
		}

		public long getStartAt()
		{
			return startAt;
		}

		public long getTicks()
		{
			return ticks;
		}

		public long getMs()
		{
			return ticks * 600;
		}

		public long getTears()
		{
			return tears;
		}

		public long getGlistening()
		{
			return glistening;
		}

		public int getClams()
		{
			return clams;
		}

		public int getChests()
		{
			return chests;
		}

		public int getOthers()
		{
			return others;
		}

		public int getSearches()
		{
			return clams + chests + others;
		}

		public long getLost()
		{
			return lost;
		}

		public long getSpent()
		{
			return spent;
		}

		public long getAgilityXp()
		{
			return agilityXp;
		}

		public long getThievingXp()
		{
			return thievingXp;
		}

		public int getCount()
		{
			return count;
		}

		/** Mermaid's tears an hour, or 0 under a minute. */
		public double tearsPerHour()
		{
			return ticks < DiveWatch.MIN_TICKS ? 0 : tears * DiveWatch.TICKS_PER_HOUR / (double) ticks;
		}

		/** Whether anything was found or searched. */
		public boolean isEmpty()
		{
			return tears <= 0 && glistening <= 0 && getSearches() <= 0;
		}

		void add(Dive d)
		{
			if (count == 0 || d.startAt > 0 && (startAt <= 0 || d.startAt < startAt))
			{
				startAt = d.startAt;
			}
			ticks += d.ticks;
			tears += d.tears;
			glistening += d.glistening;
			clams += d.clams;
			chests += d.chests;
			others += d.others;
			lost += d.lost;
			spent += d.spent;
			agilityXp += d.agilityXp;
			thievingXp += d.thievingXp;
			count += Math.max(1, d.count);
		}

		Dive copy()
		{
			Dive d = new Dive(startAt);
			d.ticks = ticks;
			d.tears = tears;
			d.glistening = glistening;
			d.clams = clams;
			d.chests = chests;
			d.others = others;
			d.lost = lost;
			d.spent = spent;
			d.agilityXp = agilityXp;
			d.thievingXp = thievingXp;
			d.count = count;
			return d;
		}
	}

	/** Reads a saved log; an empty one for nothing saved or a save that cannot be read. */
	public static UnderwaterLog read(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return new UnderwaterLog();
		}
		try
		{
			UnderwaterLog log = gson.fromJson(json, UnderwaterLog.class);
			return log == null ? new UnderwaterLog() : log.repair();
		}
		catch (JsonParseException | IllegalStateException | ClassCastException | NumberFormatException e)
		{
			return new UnderwaterLog();
		}
	}

	private UnderwaterLog repair()
	{
		if (dives == null)
		{
			dives = new ArrayList<>();
		}
		dives.removeIf(d -> d == null);
		if (total == null)
		{
			total = new Dive();
		}
		if (mode() == null)
		{
			mode = null;
		}
		return this;
	}

	/**
	 * Files a finished dive.
	 *
	 * @return true when it is the best dive yet for tears an hour, among dives of ten minutes or more,
	 * and there was an earlier best to beat
	 */
	public boolean file(Dive dive)
	{
		if (dive == null || dive.isEmpty())
		{
			return false;
		}
		Dive d = dive.copy();
		d.count = 0;
		dives.add(d);
		while (dives.size() > RECENT)
		{
			dives.remove(0);
		}
		total.add(d);
		if (d.ticks >= DiveWatch.BEST_TICKS && d.tearsPerHour() > bestRate)
		{
			boolean had = bestRate > 0;
			bestRate = d.tearsPerHour();
			return had;
		}
		return false;
	}

	/** A hand-in to Mairin: glistening tears given and the experience paid for them. */
	public void handIn(long tears, long agilityXp, long thievingXp)
	{
		handedIn += Math.max(0, tears);
		handInAgilityXp += Math.max(0, agilityXp);
		handInThievingXp += Math.max(0, thievingXp);
		Underwater.Mode m = Underwater.Mode.of(agilityXp, thievingXp);
		if (m != null)
		{
			mode = m.name();
		}
	}

	/** Mermaid's tears seen in the bank; returns true when the count changed. */
	public boolean bankTears(int count)
	{
		int c = Math.max(0, count);
		boolean changed = c != bankTears;
		bankTears = c;
		return changed;
	}

	/** Returns true when either level changed. */
	public boolean levels(int agility, int thieving)
	{
		boolean changed = false;
		if (agility > 0 && agility != agilityLevel)
		{
			agilityLevel = agility;
			changed = true;
		}
		if (thieving > 0 && thieving != thievingLevel)
		{
			thievingLevel = thieving;
			changed = true;
		}
		return changed;
	}

	/** Recent dives, oldest first. */
	public List<Dive> getDives()
	{
		return Collections.unmodifiableList(dives);
	}

	public Dive getTotal()
	{
		return total;
	}

	public long getHandedIn()
	{
		return handedIn;
	}

	public long getHandInAgilityXp()
	{
		return handInAgilityXp;
	}

	public long getHandInThievingXp()
	{
		return handInThievingXp;
	}

	/** How the player was last paid, or null before any hand-in. */
	public Underwater.Mode mode()
	{
		if (mode == null)
		{
			return null;
		}
		try
		{
			return Underwater.Mode.valueOf(mode);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	public int getBankTears()
	{
		return bankTears;
	}

	public int getAgilityLevel()
	{
		return agilityLevel;
	}

	public int getThievingLevel()
	{
		return thievingLevel;
	}

	public double getBestRate()
	{
		return bestRate;
	}

	/** A copy, for the panel to read while the client thread writes the original. */
	public UnderwaterLog copy(Gson gson)
	{
		return read(gson, gson.toJson(this));
	}
}
