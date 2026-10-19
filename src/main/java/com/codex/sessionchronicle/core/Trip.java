package com.codex.sessionchronicle.core;

/**
 * One trip out from the bank and back: how long it took in play time, and the kills, loot and
 * experience it brought. A trip ends when the bank opens and the next begins when it closes.
 */
public class Trip
{
	/** Play time into the session when it began and ended. */
	long from;
	long to;
	int kills;
	long loot;
	long xp;

	Trip()
	{
	}

	Trip(long from, long to, int kills, long loot, long xp)
	{
		this.from = from;
		this.to = to;
		this.kills = kills;
		this.loot = loot;
		this.xp = xp;
	}

	/** How long it took, in play time. */
	public long getMs()
	{
		return Math.max(0, to - from);
	}

	public int getKills()
	{
		return kills;
	}

	public long getLoot()
	{
		return loot;
	}

	public long getXp()
	{
		return xp;
	}
}
