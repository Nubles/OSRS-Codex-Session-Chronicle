package com.codex.sessionchronicle.core;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PaceTest
{
	private static final long MIN = 60_000L;
	private static final long HOUR = 60 * MIN;
	private static final long DAY = 24 * HOUR;

	/** A session of one skill at a rate an hour, played for so long. */
	static Session skill(long start, String skill, long perHour, long playedMs)
	{
		Session s = new Session(start, "Zezima");
		Session.SkillLine line = new Session.SkillLine(skill, 1_000_000, 70);
		line.xp = line.startXp + perHour * playedMs / HOUR;
		s.skills.put(skill, line);
		s.playedMs = playedMs;
		s.lastSeenAt = start + playedMs;
		return s;
	}

	/** A boss trip: kills an hour, loot an hour and some incidental combat experience. */
	static Session boss(long start, String boss, int kills, long loot, long playedMs)
	{
		Session s = skill(start, "Ranged", 200_000, playedMs);
		Session.KillLine line = new Session.KillLine(boss);
		line.looted = kills;
		s.kills.put(Chronicle.key(boss), line);
		if (loot > 0)
		{
			s.loot.put(995, new Session.Drop(995, "Coins", loot, 1));
		}
		return s;
	}

	@Test
	public void cluesOpenedOnACombatTripAreNotItsMonster()
	{
		// Hill giants for beginner clues: twelve caskets opened outnumber the eight giants killed, but
		// the trip is about the giants, and a casket an hour is no kill rate to race.
		Session s = boss(10 * DAY, "Hill Giant", 8, 0, HOUR);
		Session.KillLine clues = new Session.KillLine("Clue Scroll (Beginner)");
		clues.looted = 12;
		clues.event = true;
		s.kills.put(Chronicle.key(clues.name), clues);
		assertEquals("Hill Giant", Pace.mainKill(s).getName());
		for (Pace.Line line : Pace.of(s, Collections.emptyList()))
		{
			assertFalse(line.getName(), "Clue Scroll (Beginner)".equals(line.getName()));
		}
	}

	@Test
	public void comparesTheMainSkillWithSessionsSpentOnIt()
	{
		List<Session> journal = Arrays.asList(
			skill(1 * DAY, "Mining", 150_000, HOUR),
			skill(2 * DAY, "Mining", 180_000, HOUR),
			skill(3 * DAY, "Mining", 210_000, HOUR),
			// Other skills, short sessions and later sessions never count.
			skill(4 * DAY, "Fishing", 900_000, HOUR),
			skill(5 * DAY, "Mining", 999_000, 10 * MIN),
			skill(20 * DAY, "Mining", 999_000, HOUR));
		Session now = skill(10 * DAY, "Mining", 198_000, HOUR);
		List<Pace.Line> lines = Pace.of(now, journal);
		assertEquals(1, lines.size());
		Pace.Line line = lines.get(0);
		assertEquals(Pace.Kind.SKILL, line.getKind());
		assertEquals("Mining", line.getName());
		assertEquals(198_000, line.getRate());
		assertEquals(180_000, line.getUsual());
		assertEquals(210_000, line.getBest());
		assertEquals(3 * DAY, line.getBestAt());
		assertEquals(3, line.getSamples());
		assertEquals(0.1, line.vsUsual(), 1e-9);
		assertFalse(line.isRecord());
		assertTrue(Pace.of(skill(10 * DAY, "Mining", 220_000, HOUR), journal).get(0).isRecord());
	}

	@Test
	public void evenCountUsesTheMiddlePair()
	{
		List<Session> journal = Arrays.asList(skill(DAY, "Mining", 100, HOUR), skill(2 * DAY, "Mining", 300, HOUR));
		assertEquals(200, Pace.of(skill(3 * DAY, "Mining", 250, HOUR), journal).get(0).getUsual());
	}

	@Test
	public void bossTripsCompareKillsAndLootNotTheCombatSkill()
	{
		List<Session> journal = Arrays.asList(
			boss(DAY, "Vorkath", 20, 4_000_000, HOUR),
			boss(2 * DAY, "Vorkath", 24, 6_000_000, HOUR),
			boss(3 * DAY, "Zulrah", 30, 9_000_000, HOUR));
		// The game's kill count names it "Vorkath" too; matched by the same fold as the session's kill lines.
		List<Pace.Line> lines = Pace.of(boss(5 * DAY, "Vorkath", 26, 4_500_000, HOUR), journal);
		assertEquals(2, lines.size());
		assertEquals(Pace.Kind.KILLS, lines.get(0).getKind());
		assertEquals(26, lines.get(0).getRate());
		assertEquals(22, lines.get(0).getUsual());
		assertTrue(lines.get(0).isRecord());
		assertEquals(Pace.Kind.LOOT, lines.get(1).getKind());
		assertEquals(5_000_000, lines.get(1).getUsual());
		assertEquals(6_000_000, lines.get(1).getBest());
	}

	@Test
	public void tooShortOrFirstOfItsKind()
	{
		assertTrue(Pace.of(skill(DAY, "Mining", 200_000, 4 * MIN), Collections.emptyList()).isEmpty());
		Pace.Line first = Pace.of(skill(DAY, "Mining", 200_000, HOUR), Collections.emptyList()).get(0);
		assertEquals(0, first.getSamples());
		assertFalse("nothing to beat is no record", first.isRecord());
		assertEquals(0, first.vsUsual(), 0);
	}

	@Test
	public void recordsAreMarkedOnceAgainstEarlierSessions()
	{
		List<Session> journal = new ArrayList<>();
		journal = Journal.file(journal, skill(DAY, "Mining", 150_000, HOUR), 10);
		Session first = skill(0, "Mining", 999_000, HOUR);
		assertTrue("the very first session sets no record", Journal.markRecords(first, Collections.emptyList()).isEmpty());

		Session fast = skill(2 * DAY, "Mining", 180_000, HOUR);
		List<Moment> marked = Journal.markRecords(fast, journal);
		assertEquals(2, marked.size());
		assertEquals("Mining record: 180k xp/hr", marked.get(0).getTitle());
		assertEquals("Your best was 150k xp/hr", marked.get(0).getDetail());
		assertEquals(Moment.Kind.RECORD, marked.get(0).getKind());
		assertEquals("Most experience yet", marked.get(1).getTitle());
		// Filing the same session again does not mark it twice.
		assertTrue(Journal.markRecords(fast, journal).isEmpty());
		assertEquals(2, fast.getMoments().size());
		// The records travel into the journal with it and lead its highlights.
		journal = Journal.file(journal, fast, 10);
		assertEquals(Moment.Kind.RECORD, Summary.of(journal.get(0), Summary.State.ENDED).getHighlights().get(0).getKind());

		Session slow = skill(3 * DAY, "Mining", 120_000, 10 * MIN);
		assertTrue("a short burst is no record", Journal.markRecords(slow, journal).isEmpty());
	}

	@Test
	public void richestSessionIsARecord()
	{
		List<Session> journal = Collections.singletonList(boss(DAY, "Vorkath", 20, 4_000_000, HOUR));
		List<Moment> marked = Journal.markRecords(boss(2 * DAY, "Vorkath", 18, 9_000_000, HOUR), journal);
		assertEquals(1, marked.size());
		assertEquals("Richest session yet", marked.get(0).getTitle());
		// Written as the rest of the suite writes coins, both sides alike.
		assertEquals("9.0M of loot, beating 4.0M", marked.get(0).getDetail());
	}

	@Test
	public void bestsAndLongestStreak()
	{
		List<Session> journal = Arrays.asList(
			skill(10 * DAY, "Mining", 160_000, HOUR),
			skill(9 * DAY, "Mining", 200_000, HOUR),
			boss(8 * DAY, "Vorkath", 25, 0, HOUR),
			skill(3 * DAY, "Fishing", 50_000, HOUR),
			skill(2 * DAY, "Fishing", 40_000, 10 * MIN),
			skill(4 * DAY, "Fishing", 1, 10 * MIN),
			skill(5 * DAY, "Fishing", 1, 10 * MIN));
		Journal.Records r = Journal.records(journal, null, 10 * DAY + HOUR * 2, ZoneOffset.UTC);
		assertEquals(4, r.getLongestStreak());
		assertEquals(3, r.getStreakDays());
		assertEquals(3, r.getBests().size());
		// The most recently set first; the short Fishing session counts for nothing.
		assertEquals("Mining", r.getBests().get(0).getName());
		assertEquals(200_000, r.getBests().get(0).getRate());
		assertEquals(9 * DAY, r.getBests().get(0).getAt());
		assertEquals("Vorkath", r.getBests().get(1).getName());
		assertEquals(25, r.getBests().get(1).getRate());
		assertEquals(50_000, r.getBests().get(2).getRate());
	}

	@Test
	public void noBestsInAnEmptyJournal()
	{
		Journal.Records r = Journal.records(Collections.emptyList(), null, DAY, ZoneOffset.UTC);
		assertTrue(r.getBests().isEmpty());
		assertEquals(0, r.getLongestStreak());
		assertNull(r.getMostXp());
	}

	@Test
	public void birdNestsAreNotWhatAWoodcuttingSessionWasAbout()
	{
		Session s = skill(DAY, "Woodcutting", 90_000, HOUR);
		Session.KillLine nests = new Session.KillLine("Bird nest");
		nests.looted = 6;
		s.kills.put(Chronicle.key("Bird nest"), nests);
		List<Pace.Line> lines = Pace.of(s, Collections.emptyList());
		assertEquals(1, lines.size());
		assertEquals(Pace.Kind.SKILL, lines.get(0).getKind());
		assertEquals("Woodcutting", lines.get(0).getName());
	}

	@Test
	public void raidRecordsCountClears()
	{
		List<Session> journal = Collections.singletonList(boss(DAY, "Chambers of Xeric", 2, 0, HOUR));
		List<Moment> marked = Journal.markRecords(boss(2 * DAY, "Chambers of Xeric", 3, 0, HOUR), journal);
		assertEquals("Chambers of Xeric record: 3 clears/hr", marked.get(0).getTitle());
		assertEquals("Your best was 2 clears/hr", marked.get(0).getDetail());
	}

	@Test
	public void aSessionTooShortToFileARecordIsNeverShownAsOne()
	{
		List<Session> journal = Arrays.asList(skill(DAY, "Mining", 150_000, HOUR), skill(2 * DAY, "Mining", 180_000, HOUR));
		// Six lucky minutes: faster than the best so far, but too short to stand as a record.
		Session lucky = skill(3 * DAY, "Mining", 260_000, 6 * MIN);
		Pace.Line line = Pace.of(lucky, journal).get(0);
		assertEquals(260_000, line.getRate());
		assertFalse("the panel and card must not call it a record", line.isRecord());
		assertTrue(Journal.markRecords(lucky, journal).isEmpty());
		// Played on to fifteen minutes at that pace, it is one.
		assertTrue(Pace.of(skill(3 * DAY, "Mining", 260_000, 15 * MIN), journal).get(0).isRecord());
	}
}
