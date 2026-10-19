package com.codex.sessionchronicle.core;

import java.util.List;

/**
 * The net worth Codex Ledger keeps, as it saves it in the RuneLite profile (group "codexledger",
 * key "ledger"): the "total" now and its "history" as {"t": epoch ms, "v": worth}, oldest first.
 * Read only, and only these fields; the session is told without it when Ledger is not installed.
 */
public final class LedgerWorth
{
	/** One point of the worth history. */
	public static final class Point
	{
		long t;
		long v;

		Point()
		{
		}

		Point(long t, long v)
		{
			this.t = t;
			this.v = v;
		}
	}

	long total;
	long since;
	List<Point> history;

	LedgerWorth()
	{
	}

	LedgerWorth(long total, long since, List<Point> history)
	{
		this.total = total;
		this.since = since;
		this.history = history;
	}

	/** True once Ledger has seen the bank, which is when its total means the whole worth. */
	public boolean isReady()
	{
		return since > 0 && total > 0;
	}

	public long getTotal()
	{
		return total;
	}

	/**
	 * The worth as it stood at a moment: the last point at or before it. Ledger keeps a point every
	 * quarter hour for the last two days, so this is close for a session begun recently; -1 when the
	 * history does not reach back that far or Ledger began after it.
	 */
	public long at(long when)
	{
		if (history == null || when < since)
		{
			return -1;
		}
		long found = -1;
		for (Point p : history)
		{
			if (p == null)
			{
				continue;
			}
			if (p.t > when)
			{
				break;
			}
			found = p.v;
		}
		return found;
	}
}
