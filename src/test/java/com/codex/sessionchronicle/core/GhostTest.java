package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Ghost pace: which earlier session is raced, and the gap at the same minute of play. */
public class GhostTest
{
	private static final long MIN = 60_000L;
	private static final long HOUR = 60 * MIN;
	private static final long DAY = 24 * HOUR;
	private static final long T0 = 1_790_000_000_000L;
	private static final List<Session.Drop> NONE = Collections.emptyList();

	/** A session of one skill played through the chronicle, gaining {@code perMinute[i]} in minute i. */
	static Session played(long start, String skill, long[] perMinute)
	{
		Chronicle c = new Chronicle();
		c.begin(start, "Zezima", 15 * MIN);
		long xp = 5_000_000;
		c.xp(skill, xp, 85, start);
		long t = start;
		for (long gain : perMinute)
		{
			// The minute's experience comes in at its start, then its hundred ticks pass.
			xp += gain;
			c.xp(skill, xp, 85, t + 1);
			for (int i = 0; i < 100; i++)
			{
				t += 600;
				c.tick(t);
			}
		}
		return c.current();
	}

	static long[] even(int minutes, long perMinute)
	{
		long[] a = new long[minutes];
		Arrays.fill(a, perMinute);
		return a;
	}

	@Test
	public void theTrackMarksEveryMinuteOfPlay()
	{
		Session s = played(T0, "Mining", even(5, 1_000));
		Track t = s.getTrack();
		assertEquals(1, t.getStep());
		assertEquals(5, t.size());
		assertEquals(Arrays.asList(1_000L, 2_000L, 3_000L, 4_000L, 5_000L), t.x);
	}

	@Test
	public void theBestRivalIsTheFastestEarlierSessionOfTheSameActivity()
	{
		Session slow = PaceTest.skill(T0, "Mining", 60_000, HOUR);
		Session fast = PaceTest.skill(T0 + DAY, "Mining", 90_000, HOUR);
		Session other = PaceTest.skill(T0 + 2 * DAY, "Woodcutting", 200_000, HOUR);
		Session tooShort = PaceTest.skill(T0 + 3 * DAY, "Mining", 300_000, 10 * MIN);
		Session later = PaceTest.skill(T0 + 9 * DAY, "Mining", 500_000, HOUR);
		Session now = played(T0 + 5 * DAY, "Mining", even(20, 1_400));
		Ghost g = Ghost.of(now, Arrays.asList(later, tooShort, other, fast, slow));
		assertNotNull(g);
		assertEquals("Mining", g.getActivity());
		assertEquals("the fastest, not a later one, nor one of ten minutes", fast.getStartedAt(), g.getGhostStartedAt());
		assertEquals(2, g.getRivals());
		assertEquals(90_000, g.getGhostRate());
		assertFalse(g.hasKills());
	}

	@Test
	public void noRivalNoGhostAndNotInTheFirstMinutes()
	{
		Session now = played(T0 + 5 * DAY, "Mining", even(20, 1_000));
		assertNull("nothing of the same activity", Ghost.of(now, Collections.singletonList(PaceTest.skill(T0, "Fishing", 50_000, HOUR))));
		assertNull(Ghost.of(now, Collections.emptyList()));
		assertNull(Ghost.of(now, null));
		Session early = played(T0 + 5 * DAY, "Mining", even(1, 1_000));
		assertNull("one minute in is too soon", Ghost.of(early, Collections.singletonList(PaceTest.skill(T0, "Mining", 50_000, HOUR))));
		assertNull(Ghost.of(null, Collections.emptyList()));
	}

	@Test
	public void aTrackedGhostIsReadAtTheSameMinuteNotAtItsAverage()
	{
		// The ghost started slowly and finished fast: 500 a minute for 20 minutes, then 2,500 for 20.
		long[] minutes = new long[40];
		for (int i = 0; i < 40; i++)
		{
			minutes[i] = i < 20 ? 500 : 2_500;
		}
		Chronicle c = new Chronicle();
		Session ghostLive = played(T0, "Mining", minutes);
		List<Session> journal = Journal.file(Collections.emptyList(), ghostLive, 100);
		Session ghost = journal.get(0);
		// Filed, its marks stand four minutes apart.
		assertEquals(4, ghost.getTrack().getStep());
		assertEquals(10, ghost.getTrack().size());
		assertEquals(60_000, ghost.totalXp());

		// Twenty minutes in at 900 a minute: 18,000 against the ghost's 10,000 then, not its average 30,000.
		Session now = played(T0 + DAY, "Mining", even(20, 900));
		Ghost g = Ghost.of(now, journal);
		assertEquals(10_000, g.getGhostXp());
		assertEquals(8_000, g.getLead());
		assertEquals(1, g.standing());
		assertFalse(g.isEstimated());
		assertEquals("+8,000 xp ahead of your best Mining session at 20:00", g.headline());
		assertEquals(c.current(), null);
	}

	@Test
	public void betweenMarksTheGhostIsReadInAStraightLine()
	{
		Session ghostLive = played(T0, "Mining", even(30, 1_000));
		Session ghost = Journal.file(Collections.emptyList(), ghostLive, 100).get(0);
		// Marks at 4 and 8 minutes; 6 minutes is halfway between them.
		assertEquals(6_000, Ghost.xpAt(ghost, 6 * MIN));
		assertEquals(0, Ghost.xpAt(ghost, 0));
		// Past its last mark (28 minutes) it runs on to where it finished, at 30.
		assertEquals(29_000, Ghost.xpAt(ghost, 29 * MIN));
		// Past its end it stays where it finished.
		assertEquals(30_000, Ghost.xpAt(ghost, 45 * MIN));
	}

	@Test
	public void aGhostFiledBeforeTracksIsRacedAtItsAveragePace()
	{
		Session old = PaceTest.skill(T0, "Mining", 60_000, HOUR);
		old.track = null;
		assertEquals(10_000, Ghost.xpAt(old, 10 * MIN));
		Session now = played(T0 + DAY, "Mining", even(10, 800));
		Ghost g = Ghost.of(now, Collections.singletonList(old));
		assertTrue(g.isEstimated());
		assertEquals(-2_000, g.getLead());
		assertEquals(-1, g.standing());
		assertEquals("2,000 xp behind your best Mining session at 10:00", g.headline());
	}

	@Test
	public void withinAHundredthOfTheGhostTheTwoAreLevel()
	{
		Session old = PaceTest.skill(T0, "Mining", 60_000, HOUR);
		old.track = null;
		Session now = played(T0 + DAY, "Mining", even(10, 995));
		Ghost g = Ghost.of(now, Collections.singletonList(old));
		assertEquals(-50, g.getLead());
		assertEquals(0, g.standing());
		assertEquals("Level with your best Mining session at 10:00", g.headline());
	}

	@Test
	public void playingOnPastTheGhostsEndRacesItsWholeSession()
	{
		Session old = PaceTest.skill(T0, "Mining", 60_000, 20 * MIN);
		old.track = null;
		Session now = played(T0 + DAY, "Mining", even(25, 1_000));
		Ghost g = Ghost.of(now, Collections.singletonList(old));
		assertTrue(g.isPastGhost());
		assertEquals(20_000, g.getGhostXp());
		assertEquals(5_000, g.getLead());
		assertEquals("Past your best Mining session, 5,000 xp on its whole 20:00", g.headline());
	}

	@Test
	public void aBossRaceSetsTheKillsSideBySideToo()
	{
		// The ghost: an hour of Zulrah, a kill every six minutes, ranged experience all the while.
		Chronicle c = new Chronicle();
		c.begin(T0, "Zezima", 15 * MIN);
		long xp = 10_000_000;
		c.xp("Ranged", xp, 95, T0);
		long t = T0;
		for (int m = 1; m <= 60; m++)
		{
			xp += 4_000;
			c.xp("Ranged", xp, 95, t + 1);
			for (int i = 0; i < 100; i++)
			{
				t += 600;
				c.tick(t);
			}
			if (m % 6 == 0)
			{
				c.loot("Zulrah", NONE, 1, false, 0, t);
				c.killCount("Zulrah", 100 + m / 6);
			}
		}
		List<Session> journal = Journal.file(Collections.emptyList(), c.close(), 100);
		assertEquals("Zulrah", journal.get(0).getActivity());

		// Now: 24 minutes in, a kill every four minutes and 5,000 xp a minute.
		Chronicle now = new Chronicle();
		long t1 = T0 + DAY;
		now.begin(t1, "Zezima", 15 * MIN);
		xp = 11_000_000;
		now.xp("Ranged", xp, 95, t1);
		t = t1;
		for (int m = 1; m <= 24; m++)
		{
			xp += 5_000;
			now.xp("Ranged", xp, 95, t + 1);
			for (int i = 0; i < 100; i++)
			{
				t += 600;
				now.tick(t);
			}
			if (m % 4 == 0)
			{
				now.loot("Zulrah", NONE, 1, false, 0, t);
				now.killCount("Zulrah", 200 + m / 4);
			}
		}
		Ghost g = Ghost.of(now.current(), journal);
		assertTrue(g.hasKills());
		assertEquals("Zulrah", g.getActivity());
		assertEquals(120_000 - 96_000, g.getLead());
		// Six kills now against the ghost's three at its 24-minute mark: its fourth came just after the mark.
		assertEquals(3, g.getKillLead());
		assertEquals("+24k xp ahead of your best Zulrah session at 24:00", g.headline());
	}

	@Test
	public void theTrailFollowsTheGapMinuteByMinute()
	{
		Session old = PaceTest.skill(T0, "Mining", 60_000, HOUR);
		old.track = null;
		// Behind for five minutes, then ahead.
		long[] minutes = new long[12];
		for (int i = 0; i < 12; i++)
		{
			minutes[i] = i < 5 ? 500 : 2_000;
		}
		Ghost g = Ghost.of(played(T0 + DAY, "Mining", minutes), Collections.singletonList(old));
		List<long[]> trail = g.getTrail();
		assertEquals(0, trail.get(0)[1]);
		assertEquals(-2_500, trail.get(5)[1]);
		assertEquals(12 * MIN, trail.get(trail.size() - 1)[0]);
		assertEquals(g.getLead(), trail.get(trail.size() - 1)[1]);
		assertTrue(g.getLead() > 0);
	}

	@Test
	public void aLongSessionsTrailIsSampledDown()
	{
		Session old = PaceTest.skill(T0, "Mining", 60_000, 4 * HOUR);
		old.track = null;
		Ghost g = Ghost.of(played(T0 + DAY, "Mining", even(150, 1_000)), Collections.singletonList(old));
		assertEquals(Ghost.TRAIL, g.getTrail().size());
		assertEquals(0, g.getTrail().get(0)[0]);
		assertEquals(150 * MIN, g.getTrail().get(Ghost.TRAIL - 1)[0]);
	}

	@Test
	public void theRaceClockReadsMinutesAndSeconds()
	{
		assertEquals("40:00", Ghost.clock(40 * MIN));
		assertEquals("0:59", Ghost.clock(59_400));
		assertEquals("1:05:30", Ghost.clock(HOUR + 5 * MIN + 30_000));
	}

	@Test
	public void theTrackIsSavedAndReadBackAndDamageIsMended()
	{
		Gson gson = new Gson();
		Session s = played(T0, "Mining", even(6, 1_000));
		Session back = Session.read(gson, gson.toJson(s));
		assertEquals(s.getTrack().x, back.getTrack().x);
		// A figure that falls, or a list of kills cut short, is damage: mended rather than lost.
		String damaged = gson.toJson(s).replace("\"x\":[1000,2000,3000", "\"x\":[1000,500,3000").replaceAll("\"k\":\\[[^\\]]*\\]", "\"k\":[0]");
		Session mended = Session.read(gson, damaged);
		assertEquals(Arrays.asList(1_000L, 1_000L, 3_000L, 4_000L, 5_000L, 6_000L), mended.getTrack().x);
		assertEquals(6, mended.getTrack().k.size());
		// A track of the wrong shape is left out, and the session kept.
		Session wrong = Session.read(gson, gson.toJson(s).replaceAll("\"track\":\\{[^}]*\\}", "\"track\":\"no\""));
		assertNotNull(wrong);
		assertNull(wrong.getTrack());
		Type journal = new TypeToken<List<Session>>()
		{
		}.getType();
		List<Session> filed = Journal.file(Collections.emptyList(), s, 10);
		List<Session> read = Journal.read(gson, gson.toJson(filed, journal));
		assertEquals(4, read.get(0).getTrack().getStep());
	}

	@Test
	public void aLongSessionsTrackThinsAndStaysSmall()
	{
		Track t = new Track();
		for (int m = 1; m <= 1000; m++)
		{
			t.mark(m * MIN, m * 100L, m / 10);
		}
		assertTrue(t.size() <= Track.MAX_MARKS);
		assertEquals(4, t.getStep());
		// Its marks still say where it stood: 4 minutes a mark, so the first is minute 4.
		assertEquals(400L, (long) t.x.get(0));
		assertEquals(500 * 100L, t.xpAt(500 * MIN, 1000 * MIN, 100_000));
		List<Long> copy = new ArrayList<>(t.x);
		Track filed = t.filed();
		assertSame(4, filed.getStep());
		assertEquals(copy, filed.x);
	}

	@Test
	public void aSessionRestoredFromBeforeTracksKeepsNone()
	{
		Gson gson = new Gson();
		Session s = PaceTest.skill(T0, "Mining", 60_000, HOUR);
		String saved = gson.toJson(s).replaceAll(",\"track\":\\{[^}]*\\}", "");
		Chronicle c = new Chronicle();
		c.restore(Session.read(gson, saved));
		c.begin(T0 + HOUR + MIN, "Zezima", 15 * MIN);
		c.tick(T0 + HOUR + MIN + 600);
		assertNull("its first hour has no marks to give", c.current().getTrack());
	}
}
