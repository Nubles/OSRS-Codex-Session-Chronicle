package com.codex.sessionchronicle.core;

/**
 * When to read the slayer task's varps, and whether a reading is the first since arriving in a
 * world. Logging in and hopping both make the client take its varps afresh from the server: for a
 * few ticks the kill count can read nothing, then the task comes back as it was. A task the session
 * is following is not dropped over that blink, so its pace carries on instead of starting again.
 */
public final class SlayerWatch
{
	/** Ticks after arriving in a world that a task reading nothing is waited out rather than taken as gone. */
	public static final int ARRIVAL_TICKS = 5;

	private boolean due;
	private boolean fresh;
	private int window;

	/** The player arrived in a world: a login, a world hop, or the connection coming back. */
	public void arrived()
	{
		due = true;
		fresh = true;
		window = ARRIVAL_TICKS;
	}

	/** One of the task's varps moved; it is read on the next tick, once they all have. */
	public void moved()
	{
		due = true;
	}

	/** A game tick passed. */
	public void tick()
	{
		if (window > 0)
		{
			window--;
		}
	}

	public boolean isDue()
	{
		return due;
	}

	/**
	 * Takes a reading of the task.
	 *
	 * @param left kills left as the varps say now
	 * @param following whether the session is following a task
	 * @param named whether the task's name could be read (only asked when kills are left)
	 * @return null to wait for the next tick; otherwise whether this is the first reading after
	 *         arriving, whose count may have moved elsewhere and whose emptiness is no finished task.
	 *         Only that one reading is first: a kill on the ticks after it, say just after the
	 *         connection came back mid-fight, is a kill made here.
	 */
	public Boolean take(int left, boolean following, boolean named)
	{
		boolean incomplete = left <= 0 ? following : !named;
		if (window > 0 && incomplete)
		{
			return null;
		}
		due = false;
		boolean first = fresh;
		if (left <= 0 || named)
		{
			// A reading the session cannot use (no name) leaves the first reading still to come.
			fresh = false;
		}
		return first;
	}
}
