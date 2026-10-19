package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

	/**
	 * Ledger's saved book, or null when it is not installed or cannot be read. Only "total", "since"
	 * and "history" are read, so damage anywhere else in the ledger is no matter; a point of history
	 * that cannot be read, or holds a worth no account has, is left out, and the rest put in order.
	 */
	public static LedgerWorth read(Gson gson, String json)
	{
		LedgerWorth read = null;
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			// The quick way, which skips the rest of the ledger unread.
			read = gson.fromJson(json, LedgerWorth.class);
		}
		catch (RuntimeException e)
		{
			read = readByPoint(gson, json);
		}
		if (read == null)
		{
			return null;
		}
		List<Point> kept = new java.util.ArrayList<>();
		if (read.history != null)
		{
			for (Point p : read.history)
			{
				if (p != null && p.t > 0 && p.v >= 0 && p.v <= Saved.MAX_WORTH)
				{
					kept.add(p);
				}
			}
		}
		kept.sort((a, b) -> Long.compare(a.t, b.t));
		long total = read.total < 0 || read.total > Saved.MAX_WORTH ? 0 : read.total;
		return new LedgerWorth(total, Math.max(0, read.since), kept);
	}

	private static LedgerWorth readByPoint(Gson gson, String json)
	{
		JsonElement root = Saved.tree(gson, json);
		if (root == null || !root.isJsonObject())
		{
			return null;
		}
		JsonObject o = root.getAsJsonObject();
		LedgerWorth read = new LedgerWorth(whole(o.get("total")), whole(o.get("since")), new java.util.ArrayList<>());
		JsonElement list = o.get("history");
		if (list != null && list.isJsonArray())
		{
			for (JsonElement e : list.getAsJsonArray())
			{
				Point p = Saved.as(gson, e, Point.class);
				if (p != null)
				{
					read.history.add(p);
				}
			}
		}
		return read;
	}

	private static long whole(JsonElement e)
	{
		try
		{
			return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()
				? new java.math.BigDecimal(e.getAsString()).longValueExact() : 0;
		}
		catch (ArithmeticException | NumberFormatException ex)
		{
			return 0;
		}
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
	 * The worth over a stretch of time, oldest first, as {epoch ms, worth}: the point standing as it
	 * began (when the history reaches that far back), every point after it, and the total now.
	 * Empty before Ledger has seen the bank.
	 */
	public List<long[]> since(long from, long now)
	{
		List<long[]> out = new java.util.ArrayList<>();
		if (!isReady() || history == null)
		{
			return out;
		}
		long before = at(from);
		if (before >= 0)
		{
			out.add(new long[]{from, before});
		}
		for (Point p : history)
		{
			if (p != null && p.t > from && p.t <= now)
			{
				out.add(new long[]{p.t, p.v});
			}
		}
		if (!out.isEmpty() || since <= now)
		{
			out.add(new long[]{now, total});
		}
		return out;
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
