package com.codex.sessionchronicle.core;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/** The Journal's day so far: every session begun today, the live one too, and nothing from yesterday. */
public class TodayTest
{
	private static final ZoneId ZONE = ZoneOffset.UTC;
	private static final long MIN = 60_000L;

	private static long at(int day, int hour)
	{
		return LocalDateTime.of(2026, 10, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
	}

	/** A finished session of so many minutes with experience in one skill and some loot. */
	private static Session session(long start, int minutes, String skill, long xp, long loot)
	{
		Chronicle c = new Chronicle();
		c.begin(start, "Zezima", 15 * MIN);
		c.xp(skill, 1_000_000, 70, start);
		for (int i = 1; i <= minutes * 20; i++)
		{
			c.tick(start + i * 3_000L);
		}
		c.xp(skill, 1_000_000 + xp, 71, start + minutes * MIN);
		if (loot > 0)
		{
			c.loot("Abyssal demon", Collections.singletonList(new Session.Drop(4151, "Abyssal whip", 1, loot)), 1, 0, start + minutes * MIN);
		}
		return c.close();
	}

	@Test
	public void sumsTodaysSessionsAndTheLiveOne()
	{
		Session yesterday = session(at(18, 20), 60, "Mining", 90_000, 0);
		Session morning = session(at(19, 9), 30, "Slayer", 40_000, 2_000_000);
		Chronicle live = new Chronicle();
		live.begin(at(19, 14), "Zezima", 15 * MIN);
		live.xp("Slayer", 2_000_000, 80, at(19, 14));
		for (int i = 1; i <= 20 * 20; i++)
		{
			live.tick(at(19, 14) + i * 3_000L);
		}
		live.xp("Slayer", 2_025_000, 80, at(19, 14) + 20 * MIN);
		live.xp("Attack", 500_000, 70, at(19, 14) + 20 * MIN);
		live.xp("Attack", 510_000, 70, at(19, 14) + 20 * MIN);

		List<Session> journal = Arrays.asList(morning, yesterday);
		Journal.Today t = Journal.records(journal, live.current(), at(19, 15), ZONE).getToday();
		assertEquals(2, t.getSessions());
		assertEquals(50 * MIN, t.getPlayedMs(), 6_000);
		assertEquals(40_000 + 25_000 + 10_000, t.getXp());
		assertEquals(2_000_000, t.getLoot());
		assertEquals(1, t.getKills());
		assertEquals(1, t.getLevels());
		assertEquals("Slayer", t.getTopSkill());
		assertEquals(65_000, t.getTopSkillXp());
	}

	@Test
	public void aSessionPastMidnightCountsForTheDayItBegan()
	{
		Session late = session(at(18, 23), 90, "Fishing", 50_000, 0);
		Journal.Today t = Journal.records(Collections.singletonList(late), null, at(19, 10), ZONE).getToday();
		assertEquals(0, t.getSessions());
		assertNull(t.getTopSkill());
		assertEquals(1, Journal.records(Collections.singletonList(late), null, at(18, 23) + 30 * MIN, ZONE).getToday().getSessions());
	}
}
