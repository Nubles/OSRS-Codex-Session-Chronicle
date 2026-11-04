package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The rooftop coach: courses, the lap clock, the mark estimate, the log and the advice built from them. */
public class RooftopCoachTest
{
	private static final AgilityCoach.Diaries NO_DIARIES = new AgilityCoach.Diaries(false, false, false);
	private final Gson gson = new Gson();

	@Test
	public void coursesAreFoundByTheirLapMessage()
	{
		// The game's message: "Your Seers' Village Rooftop lap count is: 312." The parser keeps "... lap".
		ChatParser.KillCount kc = ChatParser.killCount("Your Seers' Village Rooftop lap count is: 312.");
		assertSame(Rooftop.SEERS, Rooftop.byLapName(kc.getName()));
		assertSame(Rooftop.AL_KHARID, Rooftop.byLapName("Al Kharid Rooftop lap"));
		assertSame(Rooftop.AL_KHARID, Rooftop.byLapName("Al-Kharid Rooftop lap"));
		assertSame(Rooftop.ARDOUGNE, Rooftop.byLapName("Ardougne Rooftop"));
		assertSame(Rooftop.DRAYNOR, Rooftop.byLapName("Draynor Village Rooftop lap"));
		// Other courses count laps too but drop no rooftop marks in this coach.
		assertNull(Rooftop.byLapName("Gnome Stronghold Agility lap"));
		assertNull(Rooftop.byLapName("Vorkath"));
		assertNull(Rooftop.byLapName(null));
	}

	@Test
	public void courseFactsMatchTheWiki()
	{
		// Rooftop Agility on the wiki: levels and experience a lap.
		int[] levels = {1, 20, 30, 40, 50, 60, 70, 80, 90};
		int[] xp = {120, 216, 270, 240, 586, 570, 890, 780, 889};
		for (int i = 0; i < levels.length; i++)
		{
			assertEquals(levels[i], Rooftop.values()[i].getLevel());
			assertEquals(xp[i], Rooftop.values()[i].xpPerLap(false));
		}
		assertEquals(1016, Rooftop.POLLNIVNEACH.xpPerLap(true));
		assertEquals(920, Rooftop.RELLEKKA.xpPerLap(true));
		assertEquals(570, Rooftop.SEERS.xpPerLap(true));
		assertSame(Rooftop.SEERS, Rooftop.byRegion(10806));
		assertNull(Rooftop.byRegion(12850));
		// The wiki's best-case rate implies the wiki's own perfect lap: Seers' 73 ticks, Draynor 72.
		assertEquals(73, AgilityCoach.wikiLapTicks(Rooftop.SEERS));
		assertEquals(72, AgilityCoach.wikiLapTicks(Rooftop.DRAYNOR));
	}

	@Test
	public void marksComeLessOftenTwentyLevelsOverButNotAtCanifis()
	{
		assertFalse(Rooftop.SEERS.markPenalty(79));
		assertTrue(Rooftop.SEERS.markPenalty(80));
		assertFalse(Rooftop.CANIFIS.markPenalty(99));
		assertEquals(1 / 3.0, MarkRate.lapChance(Rooftop.SEERS, 60), 1e-9);
		assertEquals(1 / 3.0 * 0.2, MarkRate.lapChance(Rooftop.SEERS, 80), 1e-9);
		assertEquals(2 / 5.0, MarkRate.lapChance(Rooftop.RELLEKKA, 80), 1e-9);
		assertEquals(2 / 3.0, MarkRate.lapChance(Rooftop.ARDOUGNE, 99), 1e-9);
	}

	@Test
	public void expectedMarksSitNearTheWikisRates()
	{
		// Seers' at the wiki's perfect pace: the wiki gives 11 to 13.8 an hour.
		double seers = MarkRate.perHour(Rooftop.SEERS, 73, 60, false);
		assertTrue("seers " + seers, seers > 11 && seers < 14.5);
		// Ardougne at the wiki's pace: 16 to 18.1.
		double ardy = MarkRate.perHour(Rooftop.ARDOUGNE, AgilityCoach.wikiLapTicks(Rooftop.ARDOUGNE), 90, false);
		assertTrue("ardougne " + ardy, ardy > 15 && ardy < 20);
		// Faster laps, more marks; twenty levels over, far fewer; a diary's shorter timer, more.
		assertTrue(MarkRate.perHour(Rooftop.SEERS, 60, 60, false) > seers);
		assertTrue(MarkRate.perHour(Rooftop.SEERS, 73, 80, false) < seers / 3);
		assertTrue(MarkRate.perHour(Rooftop.ARDOUGNE, 76, 90, true) > MarkRate.perHour(Rooftop.ARDOUGNE, 76, 90, false));
		assertEquals(0, MarkRate.perHour(Rooftop.SEERS, 0, 60, false), 0);
	}

	@Test
	public void lapsAreTimedStartToStartWithObstacleSplits()
	{
		LapClock clock = new LapClock();
		assertNull("the first message only starts the clock", clock.lapDone(Rooftop.SEERS, 100));
		clock.xp(45, 100);
		clock.xp(20, 110);
		clock.xp(15, 125);
		clock.xp(0, 130);
		clock.xp(65, 140);
		AgilityLog.Lap lap = clock.lapDone(Rooftop.SEERS, 175);
		assertNotNull(lap);
		assertEquals(75, lap.getTicks());
		assertEquals(100, lap.getXp());
		assertTrue(Arrays.equals(new int[]{10, 25, 40}, lap.getSplits()));
		assertTrue(clock.isRunning());
		assertSame(Rooftop.SEERS, clock.getCourse());
	}

	@Test
	public void breaksOtherCoursesAndStrayMessagesAreNotLaps()
	{
		LapClock clock = new LapClock();
		clock.lapDone(Rooftop.SEERS, 0);
		assertNull("a different course starts afresh", clock.lapDone(Rooftop.CANIFIS, 70));
		assertNull("a message read twice is no lap", clock.lapDone(Rooftop.CANIFIS, 75));
		assertNull("a bank trip of eight minutes is no lap", clock.lapDone(Rooftop.CANIFIS, 75 + 800));
		assertNotNull(clock.lapDone(Rooftop.CANIFIS, 75 + 800 + 73));
		// Off the course for more than thirty seconds, the lap under way is dropped.
		for (int t = 1; t <= LapClock.AWAY_TICKS + 1; t++)
		{
			clock.tick(null, 948 + t);
		}
		assertFalse(clock.isRunning());
		assertNull(clock.lapDone(Rooftop.CANIFIS, 1040));
	}

	@Test
	public void theLapRacesTheGhostOfTheBestLap()
	{
		AgilityLog log = new AgilityLog();
		log.lap(new AgilityLog.Lap(Rooftop.SEERS, 73, new int[]{8, 20, 33, 45, 60, 70}, 570), 1000);
		LapClock clock = new LapClock();
		clock.lapDone(Rooftop.SEERS, 0);
		LapClock.Split s = clock.split(5, log.get(Rooftop.SEERS));
		assertNull("nothing to compare before the first obstacle", s.delta());
		assertNull(s.projected());
		s = clock.split(12, log.get(Rooftop.SEERS));
		assertEquals("slower than the ghost to the first obstacle", Integer.valueOf(73 + 4), s.projected());
		clock.xp(45, 10);
		clock.xp(45, 21);
		s = clock.split(22, log.get(Rooftop.SEERS));
		assertEquals(2, s.getObstacles());
		assertEquals(6, s.getGhostObstacles());
		assertEquals(Integer.valueOf(1), s.delta());
		assertEquals(Integer.valueOf(74), s.projected());
		assertEquals(2, s.ghostObstaclesNow());
		assertEquals("+0.6s", LapClock.gap(1));
		assertEquals("-1.2s", LapClock.gap(-2));
		assertEquals("43.8s", LapClock.time(73));
		assertEquals("1:04.2", LapClock.time(107));
		// No best lap yet: the lap runs without a ghost.
		assertFalse(new LapClock.Split(Rooftop.DRAYNOR, 10, new int[]{3}, 0, new int[0]).hasGhost());
	}

	@Test
	public void theLogKeepsTheBestLapAndRecentOnes()
	{
		AgilityLog log = new AgilityLog();
		assertFalse("the first lap is a best but not a new one to tell", log.lap(new AgilityLog.Lap(Rooftop.SEERS, 80, new int[]{10}, 570), 1));
		assertFalse(log.lap(new AgilityLog.Lap(Rooftop.SEERS, 90, new int[]{12}, 570), 2));
		assertTrue(log.lap(new AgilityLog.Lap(Rooftop.SEERS, 74, new int[]{9}, 570), 3));
		AgilityLog.CourseLog c = log.get(Rooftop.SEERS);
		assertEquals(3, c.getLaps());
		assertEquals(74, c.getBestTicks());
		assertEquals(3, c.getBestAt());
		assertTrue(Arrays.equals(new int[]{9}, c.getBestSplits()));
		assertEquals(80, c.typicalTicks());
		assertEquals(244, c.getTicks());
		for (int i = 0; i < 60; i++)
		{
			log.lap(new AgilityLog.Lap(Rooftop.SEERS, 76, new int[0], 570), 10 + i);
		}
		assertEquals(AgilityLog.RECENT, c.getRecent().size());
		assertEquals(76, c.typicalTicks());
		log.mark(Rooftop.SEERS, 2);
		log.mark(Rooftop.SEERS, 0);
		assertEquals(2, c.getMarks());

		AgilityLog back = AgilityLog.read(gson, gson.toJson(log));
		assertEquals(63, back.get(Rooftop.SEERS).getLaps());
		assertEquals(74, back.get(Rooftop.SEERS).getBestTicks());
	}

	@Test
	public void aDamagedOrStrangeSaveReadsAsEmpty()
	{
		assertTrue(AgilityLog.read(gson, null).all().isEmpty());
		assertTrue(AgilityLog.read(gson, "{not json").all().isEmpty());
		assertTrue(AgilityLog.read(gson, "[]").all().isEmpty());
		AgilityLog odd = AgilityLog.read(gson, "{\"courses\":{\"NOWHERE\":{\"laps\":3},\"SEERS\":null,\"CANIFIS\":{\"laps\":2}},\"graceful\":null}");
		assertEquals(Collections.singleton(Rooftop.CANIFIS), odd.all().keySet());
		assertTrue(odd.getGraceful().isEmpty());
		assertEquals(-1, odd.getBankMarks());
		assertEquals(0, odd.get(Rooftop.CANIFIS).typicalTicks());
	}

	@Test
	public void gracefulPiecesAreKnownInEveryColour()
	{
		int sum = 0;
		for (Graceful g : Graceful.values())
		{
			sum += g.getCost();
		}
		assertEquals(Graceful.SET_COST, sum);
		assertEquals(35, Graceful.HOOD.getCost());
		assertEquals(60, Graceful.LEGS.getCost());
		assertSame(Graceful.HOOD, Graceful.of(ItemID.GRACEFUL_HOOD));
		assertSame(Graceful.HOOD, Graceful.of(ItemID.GRACEFUL_HOOD_WORN));
		assertSame(Graceful.TOP, Graceful.of(ItemID.ZEAH_GRACEFUL_TOP_ARCEUUS));
		assertSame(Graceful.BOOTS, Graceful.of(ItemID.GRACEFUL_BOOTS_WYRM_WORN));
		assertNull("a bank placeholder is no piece", Graceful.of(ItemID.Placeholder.GRACEFUL_HOOD));
		assertNull(Graceful.of(ItemID.GRACE));
		assertEquals(EnumSet.of(Graceful.HOOD, Graceful.CAPE),
			Graceful.among(Arrays.asList(ItemID.GRACEFUL_HOOD, ItemID.GRACEFUL_CAPE_HALLOWED, ItemID.GRACE)));
		assertEquals(260 - 35 - 40, Graceful.costOfMissing(EnumSet.of(Graceful.HOOD, Graceful.CAPE)));
	}

	@Test
	public void gracefulOnceSeenStaysOwned()
	{
		AgilityLog log = new AgilityLog();
		assertTrue(log.sawGraceful(EnumSet.of(Graceful.HOOD)));
		assertFalse(log.sawGraceful(EnumSet.of(Graceful.HOOD)));
		assertFalse(log.sawGraceful(EnumSet.noneOf(Graceful.class)));
		assertEquals(EnumSet.of(Graceful.HOOD), log.getGraceful());
		assertTrue(log.bankMarks(40));
		assertFalse(log.bankMarks(40));
	}

	@Test
	public void theCoachUsesYourLapsOnceThereAreEnoughAndSaysSo()
	{
		AgilityLog log = new AgilityLog();
		for (int i = 0; i < AgilityCoach.OWN_LAPS - 1; i++)
		{
			log.lap(new AgilityLog.Lap(Rooftop.SEERS, 100, new int[0], 570), i);
		}
		AgilityCoach coach = AgilityCoach.of(log, 60, NO_DIARIES, 0, null);
		AgilityCoach.Course seers = coach.course(Rooftop.SEERS);
		assertEquals(AgilityCoach.Source.WIKI, seers.getPaceSource());
		assertEquals(46_800, seers.getXpPerHour(), 100);
		log.lap(new AgilityLog.Lap(Rooftop.SEERS, 100, new int[0], 570), 9);
		seers = AgilityCoach.of(log, 60, NO_DIARIES, 0, null).course(Rooftop.SEERS);
		assertEquals(AgilityCoach.Source.YOURS, seers.getPaceSource());
		assertEquals(100, seers.getLapTicks());
		// 60 laps an hour at 570 each.
		assertEquals(34_200, seers.getXpPerHour());
		assertTrue("not run long enough to have a found rate", seers.getMarksSeen() < 0);
		assertEquals(seers.getMarksExpected(), seers.marksPerHour(), 0);
	}

	@Test
	public void theBestCourseNowWeighsTheMarkPenalty()
	{
		AgilityLog log = new AgilityLog();
		// At 80, Seers' and lower give 80% fewer marks; Rellekka does not, and Canifis is spared.
		AgilityCoach coach = AgilityCoach.of(log, 80, NO_DIARIES, 0, null);
		assertSame(Rooftop.RELLEKKA, coach.getBestXp().getCourse());
		assertSame(Rooftop.CANIFIS, coach.getBestMarks().getCourse());
		assertTrue(coach.course(Rooftop.SEERS).hasPenalty());
		assertFalse(coach.course(Rooftop.ARDOUGNE).isUnlocked());
		assertEquals(8, coach.ranked().size());
		assertSame(Rooftop.RELLEKKA, coach.ranked().get(0).getCourse());
		// At 90, Ardougne leads both.
		coach = AgilityCoach.of(log, 90, NO_DIARIES, 0, null);
		assertSame(Rooftop.ARDOUGNE, coach.getBestXp().getCourse());
		assertSame(Rooftop.ARDOUGNE, coach.getBestMarks().getCourse());
		// Unknown level: nothing unlocked, nothing advised.
		assertNull(AgilityCoach.of(log, 0, NO_DIARIES, 0, null).getBestXp());
	}

	@Test
	public void marksStillNeededCountPiecesOwnedAndMarksBankedAndCarried()
	{
		AgilityLog log = new AgilityLog();
		AgilityCoach coach = AgilityCoach.of(log, 60, NO_DIARIES, 12, null);
		assertFalse(coach.isBankSeen());
		assertEquals(260 - 12, coach.marksNeeded());
		log.sawGraceful(EnumSet.of(Graceful.HOOD, Graceful.BOOTS));
		log.bankMarks(50);
		coach = AgilityCoach.of(log, 60, NO_DIARIES, 12, null);
		assertTrue(coach.isBankSeen());
		assertEquals(62, coach.getMarksOwned());
		assertEquals(260 - 35 - 40 - 62, coach.marksNeeded());
		double hours = coach.hoursToOutfit(coach.getBestMarks());
		assertEquals(coach.marksNeeded() / coach.getBestMarks().marksPerHour(), hours, 1e-9);
		Set<Graceful> all = new HashSet<>(Arrays.asList(Graceful.values()));
		log.sawGraceful(all);
		coach = AgilityCoach.of(log, 60, NO_DIARIES, 0, null);
		assertTrue(coach.outfitDone());
		assertEquals(0, coach.marksNeeded());
	}

	@Test
	public void marksFoundBecomeTheRateOnceTheCourseIsRunLongEnough()
	{
		AgilityLog log = new AgilityLog();
		for (int i = 0; i < 30; i++)
		{
			log.lap(new AgilityLog.Lap(Rooftop.CANIFIS, 75, new int[0], 240), i);
		}
		log.mark(Rooftop.CANIFIS, 7);
		AgilityCoach.Course c = AgilityCoach.of(log, 50, NO_DIARIES, 0, null).course(Rooftop.CANIFIS);
		// 2,250 ticks, 22.5 minutes: 7 marks is 18.7 an hour.
		assertEquals(7 * 6000.0 / 2250, c.getMarksSeen(), 1e-9);
		assertEquals(c.getMarksSeen(), c.marksPerHour(), 0);
		assertEquals(Arrays.asList(Rooftop.CANIFIS), Arrays.asList(AgilityCoach.of(log, 50, NO_DIARIES, 0, null).run().get(0).getCourse()));
	}
}
