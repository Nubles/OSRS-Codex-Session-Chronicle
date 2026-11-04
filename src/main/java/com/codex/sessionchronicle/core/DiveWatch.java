package com.codex.sessionchronicle.core;

/**
 * Watches a dive at Fossil Island's Underwater Agility and Thieving from what the game shows each
 * tick: the mermaid's and glistening tears carried, Agility and Thieving experience, and whether the
 * player is underwater with a shop or bank open. It reads only; it marks nothing to click.
 *
 * <p>A dive begins at the first tear, search or obstacle underwater and ends when the player has
 * been out of the water for a few ticks or has found nothing for five minutes; its length runs from
 * its first find to its last. Within it, Thieving experience of a few points is a search ("A
 * successful search also rewards 4.5 Agility and Thieving experience"), told a clam or a chest by the
 * object last clicked; mermaid's tears gone with Mairin's Market open were spent, and gone with
 * nothing open were lost to the air running out. Glistening tears leaving the pack, and experience
 * too large for a search, are a hand-in to Mairin: the tears and what she paid are counted together.
 */
public final class DiveWatch
{
	public static final double TICKS_PER_HOUR = 6000;
	/** Out of the water this long and the dive is over. */
	static final int OUT_TICKS = 10;
	/** Nothing found for this long (five minutes) and the dive is over. */
	static final int IDLE_TICKS = 500;
	/** A dive shorter than a minute has no rate. */
	public static final int MIN_TICKS = 100;
	/** Only a dive of ten minutes or more can be the best dive. */
	public static final int BEST_TICKS = 1000;
	/** A click on a clam or chest names the next search within this many ticks. */
	static final int CLICK_TICKS = 30;
	/** A hand-in's tears and experience may land a tick or two apart. */
	static final int HAND_IN_TICKS = 2;

	/** What the game shows in one tick. Counts and experience of -1 are not known yet. */
	public static final class Reading
	{
		final boolean underwater;
		final boolean shop;
		final boolean bank;
		final int mermaid;
		final int glistening;
		final int agilityXp;
		final int thievingXp;

		public Reading(boolean underwater, boolean shop, boolean bank, int mermaid, int glistening, int agilityXp, int thievingXp)
		{
			this.underwater = underwater;
			this.shop = shop;
			this.bank = bank;
			this.mermaid = mermaid;
			this.glistening = glistening;
			this.agilityXp = agilityXp;
			this.thievingXp = thievingXp;
		}
	}

	/** A hand-in to Mairin, once its tears and experience have all landed. */
	public static final class HandIn
	{
		private final long tears;
		private final long agilityXp;
		private final long thievingXp;

		HandIn(long tears, long agilityXp, long thievingXp)
		{
			this.tears = tears;
			this.agilityXp = agilityXp;
			this.thievingXp = thievingXp;
		}

		public long getTears()
		{
			return tears;
		}

		public long getAgilityXp()
		{
			return agilityXp;
		}

		public long getThievingXp()
		{
			return thievingXp;
		}
	}

	/** What a tick changed. */
	public static final class Step
	{
		boolean changed;
		UnderwaterLog.Dive filed;
		HandIn handIn;

		/** Whether the dive or the tears carried moved, so the panel is worth redrawing. */
		public boolean isChanged()
		{
			return changed;
		}

		/** A dive that ended this tick, to file; null if none did. */
		public UnderwaterLog.Dive getFiled()
		{
			return filed;
		}

		/** A hand-in that finished this tick, or null. */
		public HandIn getHandIn()
		{
			return handIn;
		}
	}

	private int mermaid = -1;
	private int glistening = -1;
	private int agilityXp = -1;
	private int thievingXp = -1;
	private boolean underwater;

	private UnderwaterLog.Dive dive;
	private int startTick;
	private int lastTick;
	private int outTicks;

	private Underwater.Spot clicked;
	private int clickTick;

	private long handTears;
	private long handAgility;
	private long handThieving;
	private int handTick = Integer.MIN_VALUE;

	/** A clam, chest or other object was clicked underwater. */
	public void clicked(Underwater.Spot spot, int tick)
	{
		clicked = spot;
		clickTick = tick;
	}

	public Step tick(int tick, long now, Reading r)
	{
		Step step = new Step();
		int dM = mermaid >= 0 && r.mermaid >= 0 ? r.mermaid - mermaid : 0;
		int dG = glistening >= 0 && r.glistening >= 0 ? r.glistening - glistening : 0;
		int dA = agilityXp >= 0 && r.agilityXp >= 0 ? Math.max(0, r.agilityXp - agilityXp) : 0;
		int dT = thievingXp >= 0 && r.thievingXp >= 0 ? Math.max(0, r.thievingXp - thievingXp) : 0;
		step.changed = dM != 0 || dG != 0 || underwater != r.underwater;
		mermaid = r.mermaid;
		glistening = r.glistening;
		agilityXp = r.agilityXp;
		thievingXp = r.thievingXp;
		underwater = r.underwater;

		if (r.underwater)
		{
			outTicks = 0;
			boolean handing = handTick != Integer.MIN_VALUE && tick - handTick <= HAND_IN_TICKS;
			if (dG < 0 || dA > Underwater.SEARCH_XP_MAX || dT > Underwater.SEARCH_XP_MAX)
			{
				handing = true;
			}
			if (handing)
			{
				// Mairin's pay: every glistening tear given and all the experience of these ticks is hers.
				handTears += Math.max(0, -dG);
				handAgility += dA;
				handThieving += dT;
				if (dG < 0 || dA > 0 || dT > 0)
				{
					handTick = tick;
				}
				dA = 0;
				dT = 0;
				dG = Math.max(0, dG);
			}
			boolean found = false;
			if (dM > 0 || dG > 0)
			{
				found = true;
				open(tick, now).tears += Math.max(0, dM);
				dive.glistening += Math.max(0, dG);
			}
			else if (dM < 0 && !r.bank && dive != null)
			{
				if (r.shop)
				{
					dive.spent += -dM;
				}
				else
				{
					dive.lost += -dM;
				}
				step.changed = true;
			}
			if (dT > 0)
			{
				found = true;
				UnderwaterLog.Dive d = open(tick, now);
				int searches = Math.max(1, (int) Math.round(dT / Underwater.SEARCH_XP));
				Underwater.Spot spot = clicked != null && tick - clickTick <= CLICK_TICKS ? clicked : Underwater.Spot.OTHER;
				if (spot == Underwater.Spot.CLAM)
				{
					d.clams += searches;
				}
				else if (spot == Underwater.Spot.CHEST)
				{
					d.chests += searches;
				}
				else
				{
					d.others += searches;
				}
				d.thievingXp += dT;
			}
			if (dA > 0)
			{
				found = true;
				open(tick, now).agilityXp += dA;
			}
			if (found || handing && dive != null)
			{
				lastTick = tick;
				dive.ticks = lastTick - startTick;
				step.changed = true;
			}
		}
		else if (dive != null || handTears > 0 || handAgility > 0 || handThieving > 0)
		{
			outTicks++;
		}

		if (handTick != Integer.MIN_VALUE && tick - handTick > HAND_IN_TICKS)
		{
			if (handTears > 0 || handAgility > 0 || handThieving > 0)
			{
				step.handIn = new HandIn(handTears, handAgility, handThieving);
				step.changed = true;
			}
			handTears = 0;
			handAgility = 0;
			handThieving = 0;
			handTick = Integer.MIN_VALUE;
		}

		if (dive != null && (outTicks >= OUT_TICKS || tick - lastTick >= IDLE_TICKS))
		{
			step.filed = close();
			step.changed = true;
		}
		return step;
	}

	private UnderwaterLog.Dive open(int tick, long now)
	{
		if (dive == null)
		{
			dive = new UnderwaterLog.Dive(now);
			startTick = tick;
			lastTick = tick;
		}
		return dive;
	}

	private UnderwaterLog.Dive close()
	{
		UnderwaterLog.Dive d = dive;
		dive = null;
		outTicks = 0;
		return d == null || d.isEmpty() ? null : d;
	}

	/**
	 * Forgets the readings, which must be taken afresh after a hop, a logout or a new account, and
	 * ends the dive under way.
	 *
	 * @return the dive that was under way, to file, or null
	 */
	public UnderwaterLog.Dive reset()
	{
		mermaid = -1;
		glistening = -1;
		agilityXp = -1;
		thievingXp = -1;
		underwater = false;
		clicked = null;
		handTears = 0;
		handAgility = 0;
		handThieving = 0;
		handTick = Integer.MIN_VALUE;
		return close();
	}

	/** The dive under way, as a copy, or null. */
	public UnderwaterLog.Dive current()
	{
		return dive == null ? null : dive.copy();
	}

	public boolean isDiving()
	{
		return dive != null;
	}

	public boolean isUnderwater()
	{
		return underwater;
	}

	/** Mermaid's tears carried, or 0 before a reading. */
	public int getMermaid()
	{
		return Math.max(0, mermaid);
	}

	/** Glistening tears carried, or 0 before a reading. */
	public int getGlistening()
	{
		return Math.max(0, glistening);
	}
}
