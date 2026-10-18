package com.codex.sessionchronicle.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class JournalTest
{
	private static final long HOUR = 3_600_000L;
	private static final long DAY = 24 * HOUR;

	private static Session session(long start, long xp, long loot)
	{
		Session s = new Session(start, "Zezima");
		Session.SkillLine line = new Session.SkillLine("Mining", 0, 1);
		line.xp = xp;
		s.skills.put("Mining", line);
		if (loot > 0)
		{
			s.loot.put(995, new Session.Drop(995, "Coins", loot, 1));
		}
		s.playedMs = HOUR;
		s.lastSeenAt = start + HOUR;
		return s;
	}

	@Test
	public void filesNewestFirstAndKeepsTheCap()
	{
		List<Session> journal = new ArrayList<>();
		for (int i = 0; i < 5; i++)
		{
			journal = Journal.file(journal, session(i * DAY, 100, 0), 3);
		}
		assertEquals(3, journal.size());
		assertEquals(4 * DAY, journal.get(0).getStartedAt());
		assertEquals(2 * DAY, journal.get(2).getStartedAt());
	}

	@Test
	public void compactingKeepsTheTotals()
	{
		Session s = session(0, 100, 0);
		for (int id = 1; id <= Journal.KEEP_DROPS + 6; id++)
		{
			s.loot.put(id, new Session.Drop(id, "Item " + id, 1, id * 10L));
		}
		long value = s.lootValue();
		Session c = Journal.compact(s);
		assertEquals(Journal.KEEP_DROPS, c.loot.size());
		assertEquals(6, c.otherDrops);
		assertEquals(value, c.lootValue());
		assertEquals(Journal.KEEP_DROPS + 6, Summary.of(c, Summary.State.ENDED).getDrops().size() + c.otherDrops);
	}

	@Test
	public void recordsAndStreak()
	{
		long now = 10 * DAY + 12 * HOUR;
		List<Session> journal = new ArrayList<>();
		Session big = session(9 * DAY, 2_000_000, 100);
		Session rich = session(8 * DAY, 10, 50_000_000);
		journal.add(big);
		journal.add(rich);
		journal.add(session(5 * DAY, 10, 0));
		Session today = session(10 * DAY, 5, 0);
		Journal.Records r = Journal.records(journal, today, now, ZoneOffset.UTC);
		assertSame(big, r.getMostXp());
		assertSame(rich, r.getMostLoot());
		assertEquals(3, r.getStreakDays());
		assertEquals(4, r.getWeekSessions());

		Journal.Records none = Journal.records(Collections.emptyList(), null, now, ZoneOffset.UTC);
		assertEquals(0, none.getStreakDays());
	}

	@Test
	public void removesOneSession()
	{
		List<Session> journal = new ArrayList<>();
		for (int i = 0; i < 3; i++)
		{
			journal = Journal.file(journal, session(i * DAY, 100, 0), 10);
		}
		List<Session> left = Journal.remove(journal, DAY);
		assertEquals(2, left.size());
		assertEquals(3, journal.size());
		assertEquals(2 * DAY, left.get(0).getStartedAt());
		assertEquals(0, left.get(1).getStartedAt());
	}

	@Test
	public void oldSavedJournalsStillRead()
	{
		// A journal saved by the first release of the plugin.
		String saved = "[{\"startedAt\":1000,\"lastSeenAt\":4000,\"endedAt\":4000,\"playedMs\":3000,\"player\":\"Zezima\","
			+ "\"skills\":{\"Mining\":{\"skill\":\"Mining\",\"startXp\":10,\"xp\":110,\"startLevel\":1,\"level\":2}},"
			+ "\"loot\":{\"995\":{\"id\":995,\"name\":\"Coins\",\"quantity\":50,\"price\":1}},\"kills\":{},"
			+ "\"moments\":[{\"kind\":\"LEVEL\",\"at\":2000,\"title\":\"Mining level 2\",\"detail\":\"Up from 1\"}],"
			+ "\"deaths\":0,\"otherLoot\":0,\"otherDrops\":0}]";
		List<Session> journal = new com.google.gson.GsonBuilder().create().fromJson(saved,
			new com.google.gson.reflect.TypeToken<List<Session>>()
			{
			}.getType());
		Summary s = Summary.of(journal.get(0), Summary.State.ENDED);
		assertEquals(100, s.getTotalXp());
		assertEquals(50, s.getLootValue());
		assertEquals("Mining level 2", s.headline());
	}
}
