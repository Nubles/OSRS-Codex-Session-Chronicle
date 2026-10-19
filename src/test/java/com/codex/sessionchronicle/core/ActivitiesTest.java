package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Each activity's pace session by session, and the label Codex Ledger reads to tell what pays. */
public class ActivitiesTest
{
	private static final long HOUR = 3_600_000L;
	private static final long DAY = 24 * HOUR;

	private static Session mining(long start, long xpPerHour)
	{
		Session s = new Session(start, "Zezima");
		Session.SkillLine line = new Session.SkillLine("Mining", 0, 1);
		line.xp = xpPerHour;
		s.skills.put("Mining", line);
		s.playedMs = HOUR;
		s.lastSeenAt = start + HOUR;
		return s;
	}

	private static Session vorkath(long start, int kills, Long worthChange)
	{
		Session s = new Session(start, "Zezima");
		Session.SkillLine line = new Session.SkillLine("Ranged", 0, 90);
		line.xp = kills * 7_000L;
		s.skills.put("Ranged", line);
		Session.KillLine k = new Session.KillLine("Vorkath");
		k.looted = kills;
		s.kills.put("vorkath", k);
		s.playedMs = HOUR;
		s.lastSeenAt = start + HOUR;
		if (worthChange != null)
		{
			s.worthStart = 1_000_000_000L;
			s.worthNow = 1_000_000_000L + worthChange;
		}
		return s;
	}

	private static Journal.Best find(Journal.Records r, String name)
	{
		for (Journal.Best b : r.getBests())
		{
			if (b.getName().equals(name))
			{
				return b;
			}
		}
		return null;
	}

	@Test
	public void eachActivityKeepsItsPaceSessionBySession()
	{
		List<Session> journal = new ArrayList<>();
		long[] rates = {100_000, 110_000, 105_000, 120_000, 130_000, 140_000};
		for (int i = 0; i < rates.length; i++)
		{
			journal = Journal.file(journal, mining(i * DAY, rates[i]), 100);
		}
		journal = Journal.file(journal, vorkath(10 * DAY, 20, 2_000_000L), 100);
		journal = Journal.file(journal, vorkath(11 * DAY, 24, null), 100);
		Journal.Records r = Journal.records(journal, null, 12 * DAY, ZoneOffset.UTC);

		Journal.Best mine = find(r, "Mining");
		assertEquals(6, mine.getSessions());
		assertEquals(6 * HOUR, mine.getPlayedMs());
		assertEquals("oldest first", 100_000, mine.getHistory().get(0)[1]);
		assertEquals(140_000, mine.getLatest());
		assertEquals(140_000, mine.getRate());
		// The last three (120k, 130k, 140k) against the first three (100k, 110k, 105k).
		assertEquals(390.0 / 315 - 1, mine.trend(), 1e-9);
		assertNull("Ledger never saw a mining session", mine.worthPerHour());

		Journal.Best vork = find(r, "Vorkath");
		assertEquals(2, vork.getSessions());
		assertTrue("too few sessions for a trend", Double.isNaN(vork.trend()));
		assertEquals("only the session Ledger saw counts toward the worth an hour", Long.valueOf(2_000_000L), vork.worthPerHour());
		assertEquals("what the player does now comes first", "Vorkath", r.getBests().get(0).getName());
	}

	@Test
	public void theHistoryKeepsTheNewestSessions()
	{
		List<Session> journal = new ArrayList<>();
		for (int i = 0; i < 20; i++)
		{
			journal = Journal.file(journal, mining(i * DAY, 100_000 + i * 1_000), 100);
		}
		Journal.Best mine = find(Journal.records(journal, null, 21 * DAY, ZoneOffset.UTC), "Mining");
		assertEquals(20, mine.getSessions());
		assertEquals(Journal.Best.HISTORY, mine.getHistory().size());
		assertEquals(119_000, mine.getLatest());
		assertEquals(108_000, mine.getHistory().get(0)[1]);
	}

	@Test
	public void filedSessionsNameTheirActivityForLedger()
	{
		List<Session> journal = Journal.file(Collections.emptyList(), vorkath(0, 20, 1_500_000L), 100);
		journal = Journal.file(journal, mining(DAY, 100_000), 100);
		assertEquals("Mining", journal.get(0).getActivity());
		assertEquals("a boss trip is its monster, not its combat skill", "Vorkath", journal.get(1).getActivity());

		// Codex Ledger reads "startedAt", "playedMs", "worthStart", "worthNow" and "activity" from the journal.
		Gson gson = new Gson();
		String saved = gson.toJson(journal, new TypeToken<List<Session>>()
		{
		}.getType());
		JsonObject vork = new JsonParser().parse(saved).getAsJsonArray().get(1).getAsJsonObject();
		assertEquals("Vorkath", vork.get("activity").getAsString());
		assertEquals(HOUR, vork.get("playedMs").getAsLong());
		assertEquals(1_000_000_000L, vork.get("worthStart").getAsLong());
		assertEquals(1_001_500_000L, vork.get("worthNow").getAsLong());
		assertEquals(0, vork.get("startedAt").getAsLong());

		// A journal saved before the activity was kept is named once on load.
		List<Session> old = gson.fromJson(saved.replace("\"activity\":\"Vorkath\",", "").replace("\"activity\":\"Mining\",", ""),
			new TypeToken<List<Session>>()
			{
			}.getType());
		assertNull(old.get(1).getActivity());
		assertTrue(Journal.label(old));
		assertEquals("Vorkath", old.get(1).getActivity());
		assertFalse("nothing left to name", Journal.label(old));
	}
}
