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
	 * @return null to wait for the next tick; otherwise whether this is a first reading after arriving,
	 *         whose count may have moved elsewhere and whose emptiness is no finished task
	 */
	public Boolean take(int left, boolean following)
	{
		if (window > 0 && left <= 0 && following)
		{
			return null;
		}
		due = false;
		boolean first = fresh || window > 0;
		fresh = false;
		return first;
	}
}
