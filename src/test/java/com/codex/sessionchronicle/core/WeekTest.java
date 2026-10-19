package com.codex.sessionchronicle.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The week card's figures: the last seven local days, the session under way, and Ledger's worth through them. */
public class WeekTest
{
	private static final ZoneId ZONE = ZoneOffset.UTC;
	private static final long MIN = 60_000L;
	private static final long HOUR = 60 * MIN;

	private static long at(int day, int hour)
	{
		return LocalDateTime.of(2026, 10, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
	}

	private static Session session(long start, int minutes, String skill, long xp)
	{
		Session s = new Session(start, "Zezima");
		Session.SkillLine line = new Session.SkillLine(skill, 1_000_000, 70);
		line.xp += xp;
		s.skills.put(skill, line);
		s.playedMs = minutes * MIN;
		s.lastSeenAt = start + s.playedMs;
		s.endedAt = s.lastSeenAt;
		return s;
	}

	@Test
	public void countsTheLastSevenDaysEachToTheDayItBegan()
	{
		long now = at(19, 20);
		List<Session> journal = Arrays.asList(
			session(at(12, 23), 90, "Mining", 100_000),
			session(at(13, 9), 60, "Mining", 200_000),
			session(at(16, 23), 120, "Fishing", 50_000),
			session(at(19, 8), 30, "Mining", 40_000));
		Week w = Week.of(journal, session(at(19, 18), 45, "Mining", 70_000), now, ZONE, null);
		assertEquals(LocalDate.of(2026, 10, 13), w.getFirst());
		assertEquals(LocalDate.of(2026, 10, 19), w.getLast());
		assertEquals(4, w.getSessions());
		assertEquals(255 * MIN, w.getPlayedMs());
		assertEquals(360_000, w.getXp());
		assertEquals(60 * MIN, w.getDays().get(0).getPlayedMs());
		// A session begun before midnight counts toward its own day, however late it ran.
		assertEquals(120 * MIN, w.getDays().get(3).getPlayedMs());
		assertEquals(75 * MIN, w.getDays().get(6).getPlayedMs());
		assertEquals(2, w.getDays().get(6).getSessions());
		assertEquals(3, w.getDaysPlayed());
		assertEquals(3, w.getBestDay());
		assertEquals("Mining", w.getActivities().get(0).getName());
		assertEquals(135 * MIN, w.getActivities().get(0).getPlayedMs());
		assertEquals("Zezima", w.getPlayer());
	}

	@Test
	public void anEmptyWeekHasNothingToTell()
	{
		Week w = Week.of(Collections.singletonList(session(at(1, 10), 60, "Mining", 1_000)), null, at(19, 10), ZONE, null);
		assertTrue(w.isEmpty());
		assertEquals(-1, w.getBestDay());
		assertEquals(7, w.getDays().size());
		assertNull(w.worthChange());
	}

	@Test
	public void aRecordBeatenAgainLaterInTheWeekIsToldOnceByItsLatest()
	{
		Session a = session(at(14, 10), 60, "Mining", 1_000);
		a.moments.add(new Moment(Moment.Kind.RECORD, at(14, 11), "Mining record: 183k xp/hr", "", 0, 0));
		Session b = session(at(16, 10), 60, "Mining", 1_000);
		b.moments.add(new Moment(Moment.Kind.RECORD, at(16, 11), "Mining record: 206k xp/hr", "", 0, 0));
		b.moments.add(new Moment(Moment.Kind.DEATH, at(16, 11), "Killed by Vorkath", "", 0, 0));
		Session c = session(at(17, 10), 60, "Mining", 1_000);
		c.moments.add(new Moment(Moment.Kind.PET, at(17, 11), "Rock golem!", "", 0, 0));
		Week w = Week.of(Arrays.asList(a, b, c), null, at(19, 10), ZONE, null);
		assertEquals(2, w.getHighlights().size());
		assertEquals("Rock golem!", w.getHighlights().get(0).getTitle());
		assertEquals("Mining record: 206k xp/hr", w.getHighlights().get(1).getTitle());
		assertEquals(2, w.getRecords());
	}

	@Test
	public void worthRunsFromTheWeeksFirstMidnightToNow()
	{
		long now = at(19, 20);
		List<LedgerWorth.Point> history = new ArrayList<>();
		for (int day = 1; day <= 19; day++)
		{
			history.add(new LedgerWorth.Point(at(day, 12), 100_000_000L + day * 1_000_000L));
		}
		LedgerWorth book = new LedgerWorth(125_000_000L, at(1, 0), history);
		Week w = Week.of(Collections.singletonList(session(at(15, 10), 60, "Mining", 1)), null, now, ZONE, book);
		// At the first midnight the worth stood at the 12th's point; it ends at Ledger's total now.
		assertEquals(at(13, 0), w.worthFrom());
		assertEquals(Long.valueOf(125_000_000L - 112_000_000L), w.worthChange());
		assertEquals(now, w.getWorth().get(w.getWorth().size() - 1)[0]);
	}

	@Test
	public void worthBegunMidWeekStartsWhereLedgerBegan()
	{
		long now = at(19, 20);
		List<LedgerWorth.Point> history = Arrays.asList(new LedgerWorth.Point(at(16, 12), 50_000_000L), new LedgerWorth.Point(at(18, 12), 60_000_000L));
		LedgerWorth book = new LedgerWorth(64_000_000L, at(16, 11), history);
		Week w = Week.of(Collections.singletonList(session(at(17, 10), 60, "Mining", 1)), null, now, ZONE, book);
		assertEquals(at(16, 12), w.worthFrom());
		assertEquals(Long.valueOf(14_000_000L), w.worthChange());
	}

	@Test
	public void noWorthBeforeLedgerHasSeenTheBank()
	{
		LedgerWorth book = new LedgerWorth(0, 0, Collections.emptyList());
		Week w = Week.of(Collections.singletonList(session(at(17, 10), 60, "Mining", 1)), null, at(19, 20), ZONE, book);
		assertTrue(w.getWorth().isEmpty());
		assertNull(w.worthChange());
	}

	@Test
	public void thinningKeepsBothEnds()
	{
		List<long[]> points = new ArrayList<>();
		for (int i = 0; i < 500; i++)
		{
			points.add(new long[]{i, i * 2});
		}
		List<long[]> thin = Week.thin(points, Week.WORTH_POINTS);
		assertEquals(Week.WORTH_POINTS, thin.size());
		assertEquals(0, thin.get(0)[0]);
		assertEquals(499, thin.get(thin.size() - 1)[0]);
	}

	@Test
	public void theWeekFollowsLocalDaysAcrossAClockChange()
	{
		ZoneId london = ZoneId.of("Europe/London");
		long now = LocalDateTime.of(2026, 10, 25, 23, 30).atZone(london).toInstant().toEpochMilli();
		long early = LocalDateTime.of(2026, 10, 19, 0, 30).atZone(london).toInstant().toEpochMilli();
		long before = LocalDateTime.of(2026, 10, 18, 23, 30).atZone(london).toInstant().toEpochMilli();
		Week w = Week.of(Arrays.asList(session(before, 60, "Mining", 1), session(early, 60, "Mining", 1)), null, now, london, null);
		assertEquals(1, w.getSessions());
		assertEquals(1, w.getDays().get(0).getSessions());
	}
}
