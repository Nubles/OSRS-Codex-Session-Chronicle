package com.codex.sessionchronicle.core;

/**
 * A slayer task as the session saw it: what it is, how many it was for and how many are left, and
 * how quickly this session has been getting through it. Plain fields so it saves with the session.
 */
public class SlayerTask
{
	/** Kills left that a task's count can fall from to nothing and still be a finished task, not a cancelled one. */
	static final int DONE_LEFT = 3;
	/** Kills this session needs on a task before its pace says how long the rest will take. */
	static final int PACE_KILLS = 3;

	/** The task's name as the game's slayer helper writes it, such as "Abyssal demons". */
	String name;
	/** How many the task was for, or 0 when the game did not say. */
	int total;
	/** How many are left, as last seen. */
	int left;
	/** How many were left when this session first saw the task. */
	int firstLeft;
	/** The session's play time when it first saw the task, and at the last kill that counted toward it. */
	long firstPlayMs;
	long lastKillPlayMs;
	/** When it was finished, epoch ms; 0 while it is under way. */
	long doneAt;

	SlayerTask()
	{
	}

	SlayerTask(String name, int total, int left, long playMs)
	{
		this.name = name;
		this.total = Math.max(total, left);
		this.left = left;
		this.firstLeft = left;
		this.firstPlayMs = playMs;
		this.lastKillPlayMs = playMs;
	}

	public String getName()
	{
		return name;
	}

	public int getTotal()
	{
		return total;
	}

	public int getLeft()
	{
		return left;
	}

	public long getDoneAt()
	{
		return doneAt;
	}

	public boolean isDone()
	{
		return doneAt > 0;
	}

	/** Kills toward it this session. */
	public int killedHere()
	{
		return Math.max(0, firstLeft - left);
	}

	/** How far through the whole task, 0 to 1. */
	public double progress()
	{
		return total <= 0 ? 0 : Math.min(1, Math.max(0, (total - left) / (double) total));
	}

	/** Play time the rest should take at this session's pace on it, or -1 until there is a pace to go by. */
	public long etaMs()
	{
		int killed = killedHere();
		long spent = lastKillPlayMs - firstPlayMs;
		if (killed < PACE_KILLS || left <= 0 || spent <= 0)
		{
			return -1;
		}
		return Math.round(spent / (double) killed * left);
	}

	/** Kills an hour of play on it this session, or 0 until there is a pace. */
	public double perHour()
	{
		int killed = killedHere();
		long spent = lastKillPlayMs - firstPlayMs;
		return killed < PACE_KILLS || spent <= 0 ? 0 : killed * 3_600_000.0 / spent;
	}
}
