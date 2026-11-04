package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Underwater Agility and Thieving as the player plays it: the dive under way, the player's own
 * tears an hour and the experience an hour those tears and searches make at their levels, paid the
 * way they last chose at Mairin; how long until the reward they chose from Mairin's Market at that
 * pace; and their recent dives. Before ten minutes of their own dives the pace is the wiki's,
 * and says so. Built on the client thread from a copy of the log, then handed to the panel, which
 * only reads it.
 */
public final class UnderwaterCoach
{
	/** Ticks of the player's own dives before their pace is theirs rather than the wiki's: ten minutes. */
	public static final long OWN_TICKS = 1000;
	/** The wiki's "around 170–230 tears can be collected per hour", taken at its middle until the player has their own. */
	static final double WIKI_TEARS = (Underwater.WIKI_TEARS_LOW + Underwater.WIKI_TEARS_HIGH) / 2.0;

	private final UnderwaterLog.Dive live;
	private final List<UnderwaterLog.Dive> recent;
	private final UnderwaterLog.Dive total;
	private final AgilityCoach.Source source;
	private final double tearsPerHour;
	private final double glisteningPerHour;
	private final Underwater.Mode mode;
	private final boolean modeKnown;
	private final int agilityLevel;
	private final int thievingLevel;
	private final long agilityPerHour;
	private final long thievingPerHour;
	private final int carried;
	private final int glistening;
	private final int bankTears;
	private final Underwater.Reward reward;
	private final boolean underwater;
	private final long handedIn;
	private final double bestRate;

	private UnderwaterCoach(UnderwaterLog log, UnderwaterLog.Dive live, boolean underwater, int carried, int glistening,
		int agilityLevel, int thievingLevel, Underwater.Reward reward)
	{
		this.live = live;
		this.underwater = underwater;
		this.carried = Math.max(0, carried);
		this.glistening = Math.max(0, glistening);
		this.bankTears = log.getBankTears();
		this.reward = reward;
		this.agilityLevel = agilityLevel > 0 ? agilityLevel : log.getAgilityLevel();
		this.thievingLevel = thievingLevel > 0 ? thievingLevel : log.getThievingLevel();
		this.handedIn = log.getHandedIn();
		this.bestRate = log.getBestRate();
		List<UnderwaterLog.Dive> list = new ArrayList<>(log.getDives());
		Collections.reverse(list);
		this.recent = Collections.unmodifiableList(list);

		UnderwaterLog.Dive sum = new UnderwaterLog.Dive();
		if (log.getTotal().getCount() > 0)
		{
			sum.add(log.getTotal());
		}
		if (live != null)
		{
			sum.add(live);
		}
		this.total = sum;

		Underwater.Mode m = log.mode();
		this.modeKnown = m != null;
		this.mode = m == null ? Underwater.Mode.BOTH : m;

		boolean own = sum.ticks >= OWN_TICKS;
		this.source = own ? AgilityCoach.Source.YOURS : AgilityCoach.Source.WIKI;
		this.tearsPerHour = own ? sum.tears * DiveWatch.TICKS_PER_HOUR / sum.ticks : WIKI_TEARS;
		// The wiki: every object after the first gives "one of each tear", so the two come at one pace until the player's own show otherwise.
		this.glisteningPerHour = own ? sum.glistening * DiveWatch.TICKS_PER_HOUR / sum.ticks : WIKI_TEARS;
		double searchAgility = own ? sum.agilityXp * DiveWatch.TICKS_PER_HOUR / sum.ticks : 0;
		double searchThieving = own ? sum.thievingXp * DiveWatch.TICKS_PER_HOUR / sum.ticks : 0;
		boolean levels = this.agilityLevel > 0 && this.thievingLevel > 0;
		this.agilityPerHour = !levels ? 0 : Math.round(glisteningPerHour * mode.agilityTenths(this.agilityLevel) / 10.0 + searchAgility);
		this.thievingPerHour = !levels ? 0 : Math.round(glisteningPerHour * mode.thievingTenths(this.thievingLevel) / 10.0 + searchThieving);
	}

	/**
	 * @param live the dive under way, or null
	 * @param carried mermaid's tears in the pack now
	 * @param glistening glistening tears in the pack now
	 * @param agilityLevel unboosted Agility level now, or 0 to use the last one read
	 * @param thievingLevel unboosted Thieving level now, or 0 to use the last one read
	 * @param reward what the player is saving tears for, or null for nothing
	 */
	public static UnderwaterCoach of(UnderwaterLog log, UnderwaterLog.Dive live, boolean underwater, int carried, int glistening,
		int agilityLevel, int thievingLevel, Underwater.Reward reward)
	{
		return new UnderwaterCoach(log, live, underwater, carried, glistening, agilityLevel, thievingLevel, reward);
	}

	/** The dive under way, or null. */
	public UnderwaterLog.Dive getLive()
	{
		return live;
	}

	public boolean isUnderwater()
	{
		return underwater;
	}

	/** Recent dives, newest first. */
	public List<UnderwaterLog.Dive> getRecent()
	{
		return recent;
	}

	/** Every dive, the one under way included. */
	public UnderwaterLog.Dive getTotal()
	{
		return total;
	}

	/** Whether the pace is the player's own or the wiki's. */
	public AgilityCoach.Source getSource()
	{
		return source;
	}

	public double getTearsPerHour()
	{
		return tearsPerHour;
	}

	public double getGlisteningPerHour()
	{
		return glisteningPerHour;
	}

	/** How glistening tears are paid: as the player last chose, else both skills. */
	public Underwater.Mode getMode()
	{
		return mode;
	}

	public boolean isModeKnown()
	{
		return modeKnown;
	}

	public int getAgilityLevel()
	{
		return agilityLevel;
	}

	public int getThievingLevel()
	{
		return thievingLevel;
	}

	/** Agility experience an hour at this pace: glistening tears paid at the player's level, and searches. */
	public long getAgilityPerHour()
	{
		return agilityPerHour;
	}

	public long getThievingPerHour()
	{
		return thievingPerHour;
	}

	public int getCarried()
	{
		return carried;
	}

	public int getGlistening()
	{
		return glistening;
	}

	/** Whether there are glistening tears past the most Mairin takes at once. */
	public boolean isOverCap()
	{
		return glistening > Underwater.EXCHANGE_CAP;
	}

	/** Agility experience the glistening tears carried would pay now, up to the most Mairin takes at once. */
	public long carriedAgilityXp()
	{
		return agilityLevel <= 0 ? 0 : Math.min(glistening, Underwater.EXCHANGE_CAP) * (long) mode.agilityTenths(agilityLevel) / 10;
	}

	public long carriedThievingXp()
	{
		return thievingLevel <= 0 ? 0 : Math.min(glistening, Underwater.EXCHANGE_CAP) * (long) mode.thievingTenths(thievingLevel) / 10;
	}

	/** Mermaid's tears in the bank when last seen, or -1 if never. */
	public int getBankTears()
	{
		return bankTears;
	}

	/** Mermaid's tears carried and banked. */
	public int getOwned()
	{
		return carried + Math.max(0, bankTears);
	}

	public Underwater.Reward getReward()
	{
		return reward;
	}

	/** Mermaid's tears still to find for the reward; 0 with none chosen or enough already. */
	public int tearsNeeded()
	{
		return reward == null ? 0 : Math.max(0, reward.getCost() - getOwned());
	}

	/** Hours of diving at this pace to find the tears still needed; 0 with enough, below nothing if it cannot be told. */
	public double hoursToReward()
	{
		if (reward == null)
		{
			return -1;
		}
		int need = tearsNeeded();
		if (need == 0)
		{
			return 0;
		}
		return tearsPerHour <= 0 ? -1 : need / tearsPerHour;
	}

	/** Dives like the player's usual one to find the tears still needed, or 0 if it cannot be told. */
	public int divesToReward()
	{
		long usual = usualDiveTears();
		int need = tearsNeeded();
		return usual <= 0 || need <= 0 ? 0 : (int) ((need + usual - 1) / usual);
	}

	/** The middle of the recent dives' mermaid's tears, or 0 before two dives. */
	public long usualDiveTears()
	{
		if (recent.size() < 2)
		{
			return 0;
		}
		List<Long> t = new ArrayList<>();
		for (UnderwaterLog.Dive d : recent)
		{
			t.add(d.getTears());
		}
		Collections.sort(t);
		int n = t.size();
		return n % 2 == 1 ? t.get(n / 2) : (t.get(n / 2 - 1) + t.get(n / 2) + 1) / 2;
	}

	public long getHandedIn()
	{
		return handedIn;
	}

	/** Tears an hour of the best dive of ten minutes or more, or 0. */
	public double getBestRate()
	{
		return bestRate;
	}
}
