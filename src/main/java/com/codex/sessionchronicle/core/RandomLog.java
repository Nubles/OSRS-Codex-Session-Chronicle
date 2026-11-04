package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * One account's random events, kept apart from its sessions: each recent event with when it came,
 * what the player was doing, how it ended and what it gave; the totals of every event by kind and
 * by activity; the play time watched, to set the events against; and the costume pieces seen owned.
 * Saved as JSON under the account's RuneScape profile.
 */
public final class RandomLog
{
	/** Recent events kept, newest last. */
	static final int RECENT = 40;
	/** Activities kept; past it the one with least play goes. */
	static final int ACTIVITIES = 24;

	/** How an event ended. */
	public enum Outcome
	{
		/** Taken part in, and its reward was seen arrive. */
		DONE("Done"),
		/** Taken part in, but no reward was seen (left early, failed, a full pack, or experience only). */
		TAKEN("Taken part"),
		DISMISSED("Dismissed"),
		IGNORED("Ignored");

		private final String label;

		Outcome(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	/** One item an event gave. */
	public static final class Gain
	{
		int id;
		String name;
		long qty;

		public Gain()
		{
		}

		public Gain(int id, String name, long qty)
		{
			this.id = id;
			this.name = name;
			this.qty = qty;
		}

		public int getId()
		{
			return id;
		}

		public String getName()
		{
			return name == null ? "Item " + id : name;
		}

		public long getQty()
		{
			return qty;
		}
	}

	/** One random event met. */
	public static final class Seen
	{
		/** The event, by enum name. */
		String e;
		/** When it turned up, epoch ms. */
		long at;
		/** What the player was doing then; null for nothing known. */
		String activity;
		/** How it ended, by enum name. */
		String outcome;
		List<Gain> reward = new ArrayList<>();

		public Seen()
		{
		}

		public Seen(RandomEvents.Event event, long at, String activity, Outcome outcome, List<Gain> reward)
		{
			this.e = event.name();
			this.at = at;
			this.activity = activity;
			this.outcome = outcome.name();
			if (reward != null)
			{
				this.reward.addAll(reward);
			}
		}

		public RandomEvents.Event getEvent()
		{
			return RandomEvents.Event.named(e);
		}

		public long getAt()
		{
			return at;
		}

		public String getActivity()
		{
			return activity;
		}

		public Outcome getOutcome()
		{
			try
			{
				return outcome == null ? Outcome.TAKEN : Outcome.valueOf(outcome);
			}
			catch (IllegalArgumentException ex)
			{
				return Outcome.TAKEN;
			}
		}

		public List<Gain> getReward()
		{
			return Collections.unmodifiableList(reward);
		}
	}

	/** Totals for one kind of event, or for one activity. */
	public static final class Tally
	{
		int seen;
		int done;
		int taken;
		int dismissed;
		int ignored;
		/** Play time watched (activities only). */
		long ms;

		void add(Outcome o)
		{
			seen++;
			switch (o)
			{
				case DONE:
					done++;
					break;
				case TAKEN:
					taken++;
					break;
				case DISMISSED:
					dismissed++;
					break;
				default:
					ignored++;
			}
		}

		public int getSeen()
		{
			return seen;
		}

		public int getDone()
		{
			return done;
		}

		public int getTaken()
		{
			return taken;
		}

		public int getDismissed()
		{
			return dismissed;
		}

		public int getIgnored()
		{
			return ignored;
		}

		/** Taken part in, with or without a reward seen. */
		public int getAccepted()
		{
			return done + taken;
		}

		public long getMs()
		{
			return ms;
		}
	}

	List<Seen> events = new ArrayList<>();
	/** Every event filed, by kind (enum name). */
	Map<String, Tally> kinds = new LinkedHashMap<>();
	/** Every event filed and the play time watched, by what the player was doing. */
	Map<String, Tally> activities = new LinkedHashMap<>();
	/** All events filed, and how they ended. */
	Tally total = new Tally();
	/** Logged-in play time watched while the log was on. */
	long watchedMs;
	/** When the last event turned up, epoch ms; 0 before any. */
	long lastAt;
	/** Costume pieces seen owned (in the bank, worn or carried), by item id. */
	List<Integer> pieces = new ArrayList<>();

	public static RandomLog read(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return new RandomLog();
		}
		try
		{
			RandomLog log = gson.fromJson(json, RandomLog.class);
			return log == null ? new RandomLog() : log.repair();
		}
		catch (JsonParseException | IllegalStateException | ClassCastException | NumberFormatException e)
		{
			return new RandomLog();
		}
	}

	private RandomLog repair()
	{
		if (events == null)
		{
			events = new ArrayList<>();
		}
		events.removeIf(s -> s == null || s.getEvent() == null);
		for (Seen s : events)
		{
			if (s.reward == null)
			{
				s.reward = new ArrayList<>();
			}
			s.reward.removeIf(g -> g == null);
		}
		if (kinds == null)
		{
			kinds = new LinkedHashMap<>();
		}
		kinds.keySet().removeIf(k -> RandomEvents.Event.named(k) == null);
		kinds.values().removeIf(t -> t == null);
		if (activities == null)
		{
			activities = new LinkedHashMap<>();
		}
		activities.keySet().removeIf(k -> k == null);
		activities.values().removeIf(t -> t == null);
		if (total == null)
		{
			total = new Tally();
		}
		if (pieces == null)
		{
			pieces = new ArrayList<>();
		}
		pieces.removeIf(id -> id == null || !RandomEvents.Costume.isPiece(id));
		watchedMs = Math.max(0, watchedMs);
		return this;
	}

	/** Files an event that has ended. */
	public void file(Seen s)
	{
		if (s == null || s.getEvent() == null)
		{
			return;
		}
		events.add(s);
		while (events.size() > RECENT)
		{
			events.remove(0);
		}
		Outcome o = s.getOutcome();
		total.add(o);
		kinds.computeIfAbsent(s.e, k -> new Tally()).add(o);
		activities.computeIfAbsent(activityKey(s.activity), k -> new Tally()).add(o);
		trimActivities();
		lastAt = Math.max(lastAt, s.at);
	}

	/** Counts play time watched toward the activity under way. */
	public void played(String activity, long ms)
	{
		if (ms <= 0)
		{
			return;
		}
		watchedMs += ms;
		activities.computeIfAbsent(activityKey(activity), k -> new Tally()).ms += ms;
		trimActivities();
	}

	static String activityKey(String activity)
	{
		return activity == null || activity.trim().isEmpty() ? RandomWatch.NO_ACTIVITY : activity;
	}

	private void trimActivities()
	{
		while (activities.size() > ACTIVITIES)
		{
			String least = null;
			long leastMs = Long.MAX_VALUE;
			for (Map.Entry<String, Tally> e : activities.entrySet())
			{
				long weight = e.getValue().ms + e.getValue().seen * 3_600_000L;
				if (weight < leastMs)
				{
					leastMs = weight;
					least = e.getKey();
				}
			}
			activities.remove(least);
		}
	}

	/** Returns true when a piece not seen before is among those given. */
	public boolean sawPieces(Collection<Integer> itemIds)
	{
		boolean added = false;
		for (Integer id : RandomEvents.Costume.among(itemIds))
		{
			if (!pieces.contains(id))
			{
				pieces.add(id);
				added = true;
			}
		}
		return added;
	}

	public List<Seen> getEvents()
	{
		return Collections.unmodifiableList(events);
	}

	/** Recent events, newest first. */
	public List<Seen> recent()
	{
		List<Seen> out = new ArrayList<>(events);
		Collections.reverse(out);
		return out;
	}

	public Tally getTotal()
	{
		return total;
	}

	public Tally kind(RandomEvents.Event e)
	{
		Tally t = kinds.get(e.name());
		return t == null ? new Tally() : t;
	}

	/** Activities with an event or at least the play time given, most events first, then most play. */
	public List<Map.Entry<String, Tally>> activities(long minMs)
	{
		List<Map.Entry<String, Tally>> out = new ArrayList<>();
		for (Map.Entry<String, Tally> e : activities.entrySet())
		{
			if (e.getValue().seen > 0 || e.getValue().ms >= minMs)
			{
				out.add(e);
			}
		}
		out.sort(Comparator.<Map.Entry<String, Tally>>comparingInt(e -> e.getValue().seen).reversed()
			.thenComparing(Comparator.<Map.Entry<String, Tally>>comparingLong(e -> e.getValue().ms).reversed()));
		return out;
	}

	public long getWatchedMs()
	{
		return watchedMs;
	}

	public long getLastAt()
	{
		return lastAt;
	}

	public java.util.Set<Integer> getPieces()
	{
		return new TreeSet<>(pieces);
	}

	/** A copy, for the panel to read while the client thread writes the original. */
	public RandomLog copy(Gson gson)
	{
		return read(gson, gson.toJson(this));
	}
}
