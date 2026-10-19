package com.codex.sessionchronicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Keeps the session that is being lived. It opens one when the player logs in, picks the same
 * one back up if they return within the break allowance, and closes it once they have been away
 * longer. Everything is fed in by the plugin with the time it happened, so it runs the same in
 * tests as in the game. Not thread safe: the plugin only touches it on the client thread.
 */
public class Chronicle
{
	/** The longest gap between two ticks that still counts as play, so a frozen client does not. */
	static final long MAX_TICK_GAP_MS = 5_000;
	static final int MAX_MOMENTS = 200;
	/** How far apart a big drop and its collection log message can be and still be one moment. */
	static final long MERGE_MS = 10 * 60_000L;

	private Session current;
	private boolean live;
	private long lastTick;
	/** The task that just ended, so its last kill's loot, arriving a moment later, is still put to it. */
	private String endedTask;
	private long endedTaskAt;
	/** Loot this soon after a task ended still counts toward it. */
	static final long TASK_LOOT_MS = 10_000;
	/** Finished tasks a session keeps. */
	static final int MAX_TASKS_DONE = 20;

	public Session current()
	{
		return current;
	}

	public boolean isLive()
	{
		return live && current != null;
	}

	/** Puts back a session saved before the client closed; it waits paused until the player is seen. */
	public void restore(Session saved)
	{
		current = saved;
		live = false;
	}

	/**
	 * The player is in the game. Continues the open session when it is theirs and they were away
	 * for no longer than the gap, otherwise starts a new one.
	 *
	 * @return the session this closed, or null
	 */
	public Session begin(long now, String player, long gapMs)
	{
		Session closed = null;
		if (current != null && (now - current.lastSeenAt > gapMs || !Objects.equals(player, current.player)))
		{
			closed = close();
		}
		if (current == null)
		{
			current = new Session(now, player);
		}
		live = true;
		lastTick = now;
		current.lastSeenAt = now;
		return closed;
	}

	/** Counts play time; called every game tick while logged in. */
	public void tick(long now)
	{
		if (!isLive())
		{
			return;
		}
		long gap = now - lastTick;
		if (gap > 0)
		{
			current.playedMs += Math.min(gap, MAX_TICK_GAP_MS);
		}
		lastTick = now;
		current.lastSeenAt = now;
	}

	/** The player left the game; the session stays open in case they come back. */
	public void pause(long now)
	{
		tick(now);
		live = false;
	}

	/** Closes the paused session once the player has been away for longer than the gap. */
	public Session expire(long now, long gapMs)
	{
		if (current == null || live || now - current.lastSeenAt <= gapMs)
		{
			return null;
		}
		return close();
	}

	/** Ends the session now. Returns it, or null when nothing happened in it. */
	public Session close()
	{
		Session session = current;
		current = null;
		live = false;
		if (session == null)
		{
			return null;
		}
		session.endedAt = session.lastSeenAt;
		return session.isEmpty() ? null : session;
	}

	/**
	 * The player's experience in a skill. The first reading of a session is its starting point;
	 * later readings are progress, and a higher level becomes a moment.
	 */
	public List<Moment> xp(String skill, long xp, int level, long now)
	{
		if (current == null || xp < 0)
		{
			return Collections.emptyList();
		}
		Session.SkillLine line = current.skills.get(skill);
		if (line == null)
		{
			current.skills.put(skill, new Session.SkillLine(skill, xp, level));
			return Collections.emptyList();
		}
		if (xp < line.xp)
		{
			// Experience never falls; a lower reading means the client was not ready yet.
			return Collections.emptyList();
		}
		line.xp = xp;
		List<Moment> moments = new ArrayList<>();
		if (level > line.level)
		{
			int from = line.level;
			line.level = level;
			Moment m;
			if (level == 99)
			{
				m = new Moment(Moment.Kind.MAX, now, "99 " + skill, "The highest level there is", 0, 0);
			}
			else
			{
				String detail = level - from > 1 ? "Up from " + from + ", " + (level - from) + " levels at once" : "Up from " + from;
				m = new Moment(Moment.Kind.LEVEL, now, skill + " level " + level, detail, 0, 0);
			}
			add(m);
			moments.add(m);
		}
		return moments;
	}

	/**
	 * The player came back to a session they had paused. Experience gained while they were
	 * away (on another device) is not this session's, so the starting point moves up with it
	 * and the rates stay honest. Also used after the game hands back stats it swapped out for a
	 * minigame, so the swap never counts as progress.
	 */
	public void resume(String skill, long xp, int level)
	{
		if (current == null || xp < 0)
		{
			return;
		}
		Session.SkillLine line = current.skills.get(skill);
		if (line == null)
		{
			current.skills.put(skill, new Session.SkillLine(skill, xp, level));
			return;
		}
		if (xp == 0 && line.xp > 0)
		{
			return;
		}
		long gained = line.gained();
		int levels = Math.max(0, line.level - line.startLevel);
		line.xp = xp;
		line.startXp = Math.max(0, xp - gained);
		line.level = level;
		line.startLevel = Math.max(1, level - levels);
	}

	/**
	 * Loot from a kill, a raid, a chest or a clue.
	 *
	 * @param source what it came from, such as "Vorkath" or "Chambers of Xeric"
	 * @param kills how many kills or clears it stands for; 0 for loot that is not a kill
	 * @param valuable a single stack worth at least this becomes a moment
	 * @return the moments the loot made
	 */
	public List<Moment> loot(String source, List<Session.Drop> drops, int kills, long valuable, long now)
	{
		return loot(source, drops, kills, false, valuable, now);
	}

	/**
	 * Loot as {@link #loot(String, List, int, long, long)}, saying whether it came from something
	 * opened or gathered (a bird nest, a crate, a raid chest) rather than from a monster's body. Such
	 * lines are kept, but count as kills only when the game keeps a kill count for them (raids do).
	 */
	public List<Moment> loot(String source, List<Session.Drop> drops, int kills, boolean event, long valuable, long now)
	{
		if (current == null)
		{
			return Collections.emptyList();
		}
		if (kills > 0 && source != null && !source.isEmpty())
		{
			Session.KillLine line = kill(source);
			line.looted += kills;
			if (!event)
			{
				if (line.tasks == null)
				{
					line.tasks = new LinkedHashMap<>();
				}
				line.tasks.merge(taskOfTheMoment(now), kills, Integer::sum);
			}
			// One monster's loot is enough to call the line kills; an event only marks a line it began.
			line.event = event && !Boolean.FALSE.equals(line.event);
		}
		List<Moment> moments = new ArrayList<>();
		for (Session.Drop drop : drops)
		{
			if (drop.quantity <= 0)
			{
				continue;
			}
			Session.Drop held = current.loot.get(drop.id);
			if (held == null)
			{
				current.loot.put(drop.id, new Session.Drop(drop.id, drop.name, drop.quantity, drop.price));
			}
			else
			{
				held.quantity += drop.quantity;
				if (drop.price > 0)
				{
					held.price = drop.price;
				}
			}
			if (valuable > 0 && drop.value() >= valuable)
			{
				String name = drop.quantity > 1 ? drop.quantity + " x " + drop.getName() : drop.getName();
				String from = source == null || source.isEmpty() ? "" : source + ", ";
				Moment logged = recent(Moment.Kind.COLLECTION, drop.getName(), now);
				if (logged != null)
				{
					// The log slot was announced first (a raid chest); the drop gives it a picture and a worth.
					logged.itemId = drop.id;
					logged.value = drop.value();
					logged.detail = "New log slot, " + from + coins(drop.value());
					moments.add(logged);
					continue;
				}
				Moment m = new Moment(Moment.Kind.DROP, now, name, from + "worth " + coins(drop.value()), drop.id, drop.value());
				add(m);
				moments.add(m);
			}
		}
		return moments;
	}

	/** The slayer task kills are put to now: the one assigned, or the one that just ended; "" without one. */
	private String taskOfTheMoment(long now)
	{
		if (current.slayer != null)
		{
			return current.slayer.name;
		}
		return endedTask != null && now - endedTaskAt <= TASK_LOOT_MS ? endedTask : "";
	}

	/**
	 * The player's slayer task as the game shows it. The count falling to nothing from its last few
	 * kills finishes the task and makes a moment; falling from more is a task cancelled or reset.
	 *
	 * @param name the task's name, or null without a task
	 * @param left kills left, 0 without a task
	 * @param total kills it was given for, or 0 when not known
	 * @param fresh the first reading after logging in: kills made elsewhere meanwhile are not this session's
	 * @return the moment of a task finished, or null
	 */
	public Moment slayer(String name, int left, int total, boolean fresh, long now)
	{
		if (current == null)
		{
			return null;
		}
		SlayerTask task = current.slayer;
		if (name == null || name.isEmpty() || left <= 0)
		{
			current.slayer = null;
			if (task == null || fresh || task.left > SlayerTask.DONE_LEFT)
			{
				return null;
			}
			task.left = 0;
			task.lastKillPlayMs = current.playedMs;
			task.doneAt = now;
			if (current.slayerDone == null)
			{
				current.slayerDone = new ArrayList<>();
			}
			current.slayerDone.add(task);
			if (current.slayerDone.size() > MAX_TASKS_DONE)
			{
				current.slayerDone.remove(0);
			}
			endedTask = task.name;
			endedTaskAt = now;
			String detail = "Slayer task done" + (task.killedHere() > 0 && task.killedHere() < task.total
				? ", " + task.killedHere() + " here" : "");
			Moment m = new Moment(Moment.Kind.TASK, now, task.total + " " + task.name, detail, 0, 0);
			add(m);
			return m;
		}
		if (task == null || !task.name.equalsIgnoreCase(name) || left > task.left)
		{
			current.slayer = new SlayerTask(name, total, left, current.playedMs);
			return null;
		}
		if (fresh)
		{
			// Killed on another client while away: the count moves, but not as this session's kills.
			task.firstLeft -= task.left - left;
			task.left = left;
		}
		else if (left < task.left)
		{
			task.left = left;
			task.lastKillPlayMs = current.playedMs;
		}
		task.total = Math.max(task.total, Math.max(total, left));
		return null;
	}

	/** The game's own kill count after a kill. */
	public void killCount(String name, int count)
	{
		if (current == null || count <= 0)
		{
			return;
		}
		Session.KillLine line = kill(name);
		String counter = name.toLowerCase(Locale.ROOT).trim();
		if (line.counters == null)
		{
			line.counters = new LinkedHashMap<>();
			if (line.firstKc > 0)
			{
				// Saved before counters were kept apart: that one counter was most likely this one.
				line.counters.put(counter, new int[]{line.firstKc, line.lastKc});
			}
		}
		int[] range = line.counters.get(counter);
		if (range == null)
		{
			range = new int[]{count, count};
			line.counters.put(counter, range);
		}
		range[0] = Math.min(range[0], count);
		range[1] = Math.max(range[1], count);
		if (line.firstKc <= 0 || count < line.firstKc)
		{
			line.firstKc = count;
		}
		line.lastKc = range[1];
	}

	/** Trips kept on a live session; older ones only count toward the totals. */
	static final int MAX_TRIPS = 50;
	/** A bank closed and opened again sooner than this, with nothing done between, is no trip. */
	static final long MIN_TRIP_MS = 20_000;

	/**
	 * The bank opened or closed. Closing it sets out on a trip; opening it again brings the trip home,
	 * and a trip that brought kills, loot or experience is kept.
	 *
	 * @return the trip just finished, or null
	 */
	public Trip bank(boolean open)
	{
		if (current == null)
		{
			return null;
		}
		Session s = current;
		if (!open)
		{
			if (s.out == null)
			{
				s.out = new Trip(s.playedMs, 0, s.totalKills(), s.lootValue(), s.totalXp());
			}
			return null;
		}
		Trip out = s.out;
		s.out = null;
		if (out == null)
		{
			return null;
		}
		Trip trip = new Trip(out.from, s.playedMs, s.totalKills() - out.kills, s.lootValue() - out.loot, s.totalXp() - out.xp);
		boolean brought = trip.kills > 0 || trip.loot > 0 || trip.xp > 0;
		if (!brought || trip.getMs() < MIN_TRIP_MS)
		{
			return null;
		}
		if (s.trips == null)
		{
			s.trips = new ArrayList<>();
		}
		s.trips.add(trip);
		while (s.trips.size() > MAX_TRIPS)
		{
			s.trips.remove(0);
		}
		s.tripCount++;
		s.tripMs += trip.getMs();
		if (s.bestTripMs <= 0 || trip.getMs() < s.bestTripMs)
		{
			s.bestTripMs = trip.getMs();
		}
		return trip;
	}

	/** A session older than this when Ledger is first read takes its starting worth from Ledger's history. */
	static final long FRESH_MS = 2 * 60_000L;

	/**
	 * Codex Ledger's worth, read again. A session just begun takes the worth as it stands for its start
	 * (Ledger saves as the player logs out, so at login it is current); one already under way looks it up
	 * in Ledger's history, or goes without rather than guess.
	 *
	 * @return true when what the session shows changed
	 */
	public boolean worth(LedgerWorth worth)
	{
		if (current == null || worth == null || !worth.isReady())
		{
			return false;
		}
		Long before = current.worthChange();
		if (current.worthStart == null)
		{
			long start = current.playedMs <= FRESH_MS ? worth.getTotal() : worth.at(current.startedAt);
			if (start > 0)
			{
				current.worthStart = start;
			}
		}
		current.worthNow = worth.getTotal();
		return !Objects.equals(before, current.worthChange());
	}

	/** Sets the live session's goal, or clears it with null. */
	public void goal(Goal goal)
	{
		if (current != null)
		{
			current.goal = goal;
		}
	}

	/**
	 * Marks the goal reached once the session gets there.
	 *
	 * @return the moment that tells it, once, or null
	 */
	public Moment checkGoal(long now)
	{
		Goal goal = current == null ? null : current.getGoal();
		if (goal == null || goal.isReached() || Goal.progress(current, goal.getKind()) < goal.getTarget())
		{
			return null;
		}
		goal.reachedMs = Math.max(1, current.playedMs);
		Moment m = new Moment(Moment.Kind.GOAL, now, "Goal reached: " + goal.describe(),
			"After " + minutes(current.playedMs) + " of play", 0, 0);
		add(m);
		return m;
	}

	private static String minutes(long ms)
	{
		long m = Math.max(1, ms / 60_000);
		return m >= 60 ? m / 60 + "h " + m % 60 + "m" : m + "m";
	}

	public void moment(Moment moment)
	{
		if (current == null || moment == null)
		{
			return;
		}
		if (moment.getKind() == Moment.Kind.COLLECTION)
		{
			Moment drop = recent(Moment.Kind.DROP, moment.getTitle(), moment.getAt());
			if (drop != null)
			{
				// The same item as a big drop a moment ago: one moment that says both.
				drop.kind = Moment.Kind.COLLECTION;
				drop.title = moment.getTitle();
				drop.detail = "New log slot, " + (drop.detail == null ? "" : drop.detail.replace("worth ", ""));
				return;
			}
		}
		add(moment);
	}

	/** The latest moment of a kind about an item, if it happened within the merge window. */
	private Moment recent(Moment.Kind kind, String item, long now)
	{
		List<Moment> moments = current.moments;
		for (int i = moments.size() - 1; i >= 0 && i >= moments.size() - 12; i--)
		{
			Moment m = moments.get(i);
			if (now - m.getAt() > MERGE_MS)
			{
				break;
			}
			String title = m.getTitle();
			if (m.getKind() == kind && (title.equals(item) || title.endsWith(" x " + item)))
			{
				return m;
			}
		}
		return null;
	}

	/**
	 * The player died.
	 *
	 * @param killer what they were fighting, or null
	 */
	public void death(long now, String killer)
	{
		if (current == null)
		{
			return;
		}
		current.deaths++;
		String detail = killer == null || killer.isEmpty() ? "Back to the respawn point" : "Killed by " + killer;
		add(new Moment(Moment.Kind.DEATH, now, "Oh dear, you are dead", detail, 0, 0));
	}

	private void add(Moment moment)
	{
		List<Moment> moments = current.moments;
		moments.add(moment);
		if (moments.size() > MAX_MOMENTS)
		{
			// Let the least telling moment go, never the newest.
			int drop = 0;
			for (int i = 1; i < moments.size() - 1; i++)
			{
				if (moments.get(i).rank() < moments.get(drop).rank())
				{
					drop = i;
				}
			}
			moments.remove(drop);
		}
	}

	private Session.KillLine kill(String name)
	{
		String key = key(name);
		Session.KillLine line = current.kills.get(key);
		if (line == null)
		{
			line = new Session.KillLine(name);
			current.kills.put(key, line);
		}
		return line;
	}

	/**
	 * Loot and kill count messages name the same boss slightly differently ("The Nightmare" and
	 * "Nightmare", "Barrows" and "Barrows chest", "Tombs of Amascut" and "Tombs of Amascut: Expert
	 * Mode", "Chambers of Xeric Challenge Mode" and "Chambers of Xeric", Dusk and the Grotesque
	 * Guardians); this folds them together so a kill counts once.
	 */
	static String key(String name)
	{
		String key = name.toLowerCase(Locale.ROOT).trim();
		int colon = key.indexOf(':');
		if (colon > 0)
		{
			key = key.substring(0, colon).trim();
		}
		if (key.endsWith(" challenge mode"))
		{
			// The Loot Tracker files a challenge mode chest as plain "Chambers of Xeric".
			key = key.substring(0, key.length() - 15);
		}
		if (key.startsWith("the "))
		{
			key = key.substring(4);
		}
		if (key.endsWith(" chest"))
		{
			key = key.substring(0, key.length() - 6);
		}
		if (key.equals("dusk") || key.equals("dawn"))
		{
			key = "grotesque guardians";
		}
		return key;
	}

	/** "950", "41k", "1.2M": a number short enough for a headline. */
	static String amount(long n)
	{
		return n < 1_000 ? String.valueOf(n) : coins(n);
	}

	static String coins(long gp)
	{
		if (gp >= 1_000_000_000)
		{
			return String.format(Locale.ROOT, "%.2fB", gp / 1e9);
		}
		if (gp >= 1_000_000)
		{
			// "5M" rather than "5.0M"; "1.2M" keeps its tenth.
			return String.format(Locale.ROOT, "%.1fM", gp / 1e6).replace(".0M", "M");
		}
		if (gp >= 1_000)
		{
			return Math.round(gp / 1e3) + "k";
		}
		return gp + " coins";
	}
}
