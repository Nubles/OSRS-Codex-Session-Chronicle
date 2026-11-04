package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Underwater Agility and Thieving: the wiki's facts, the dive watch, the log and the coach built from them. */
public class UnderwaterTest
{
	private static final long T0 = 1_760_000_000_000L;
	private final Gson gson = new Gson();

	/** A player underwater with these tears and experience. */
	private static DiveWatch.Reading under(int mermaid, int glistening, int agility, int thieving)
	{
		return new DiveWatch.Reading(true, false, false, mermaid, glistening, agility, thieving);
	}

	@Test
	public void mairinPaysWhatTheWikiSays()
	{
		// Glistening tear: E_A = 0.018 x A^2 and E_T = 0.066 x T^2 for both skills, x1.5 for one. The wiki's own
		// figures at 99: "176.4 Agility and 646.8 Thieving", "264.6 Agility", "970.2 Thieving".
		assertEquals(1764, Underwater.Mode.BOTH.agilityTenths(99));
		assertEquals(6468, Underwater.Mode.BOTH.thievingTenths(99));
		assertEquals(2646, Underwater.Mode.AGILITY.agilityTenths(99));
		assertEquals(0, Underwater.Mode.AGILITY.thievingTenths(99));
		assertEquals(9702, Underwater.Mode.THIEVING.thievingTenths(99));
		assertEquals(0, Underwater.Mode.THIEVING.agilityTenths(99));
		// At 70 both: 0.018 x 4900 = 88.2 and 0.066 x 4900 = 323.4.
		assertEquals(882, Underwater.Mode.BOTH.agilityTenths(70));
		assertEquals(3234, Underwater.Mode.BOTH.thievingTenths(70));
		// The wiki's 38,800 Agility an hour at 99 sits in its 170-230 tears an hour with searches on top.
		assertTrue(200 * 176.4 < 38_800 && 230 * 176.4 > 38_800);
	}

	@Test
	public void mairinsMarketPrices()
	{
		// Mairin's Market on the wiki: 100, 200, 300, 400, 20 and 30 mermaid's tears.
		assertEquals(100, Underwater.Reward.SMALL_FOSSIL.getCost());
		assertEquals(200, Underwater.Reward.MEDIUM_FOSSIL.getCost());
		assertEquals(300, Underwater.Reward.LARGE_FOSSIL.getCost());
		assertEquals(400, Underwater.Reward.MERFOLK_TRIDENT.getCost());
		assertEquals(20, Underwater.Reward.SEAWEED_SPORE.getCost());
		assertEquals(30, Underwater.Reward.BOWL_OF_FISH.getCost());
		assertEquals(ItemID.MERFOLK_TRIDENT, Underwater.Reward.MERFOLK_TRIDENT.getItemId());
		assertEquals(21649, Underwater.Reward.MERFOLK_TRIDENT.getItemId());
		assertEquals(21693, Underwater.Reward.BOWL_OF_FISH.getItemId());
	}

	@Test
	public void idsAndRegions()
	{
		// The wiki's item ids: mermaid's tear 21656, glistening tear 22207.
		assertTrue(Underwater.isMermaidTear(21656));
		assertTrue(Underwater.isMermaidTear(ItemID.FOSSIL_MERMAID_TEAR_5));
		assertFalse(Underwater.isMermaidTear(22207));
		assertTrue(Underwater.isGlisteningTear(22207));
		assertSame(Underwater.Spot.CLAM, Underwater.Spot.of(ObjectID.FOSSIL_CLAM_OPEN));
		assertSame(Underwater.Spot.CHEST, Underwater.Spot.of(ObjectID.FOSSIL_CHEST_OPEN));
		assertSame(Underwater.Spot.OTHER, Underwater.Spot.of(ObjectID.FOSSIL_UNDERWATER_DEEPWATER_HOLE));
		assertTrue(Underwater.isRegion(15008));
		assertTrue(Underwater.isRegion(15264));
		assertFalse(Underwater.isRegion(14652));
	}

	@Test
	public void searchesTearsAndTheSpotClicked()
	{
		DiveWatch w = new DiveWatch();
		int t = 100;
		assertNull(w.tick(t++, T0, under(50, 0, 1_000_000, 2_000_000)).getFiled());
		assertFalse(w.isDiving());

		// The first object gives no tears, only its experience: 4.5 each, so 4 or 5 a tick as the game counts it.
		w.clicked(Underwater.Spot.of(ObjectID.FOSSIL_CLAM_OPEN), t);
		w.tick(t++, T0, under(50, 0, 1_000_004, 2_000_004));
		assertTrue(w.isDiving());
		assertEquals(1, w.current().getClams());
		assertEquals(0, w.current().getTears());

		// A chest: one of each tear and 4.5 experience.
		w.clicked(Underwater.Spot.CHEST, t);
		w.tick(t++, T0, under(51, 1, 1_000_009, 2_000_009));
		UnderwaterLog.Dive d = w.current();
		assertEquals(1, d.getChests());
		assertEquals(1, d.getTears());
		assertEquals(1, d.getGlistening());
		assertEquals(9, d.getThievingXp());
		assertEquals(9, d.getAgilityXp());

		// An obstacle or hole: Agility alone, no search.
		w.tick(t++, T0, under(51, 1, 1_000_013, 2_000_009));
		assertEquals(2, w.current().getSearches());
		assertEquals(13, w.current().getAgilityXp());
		assertEquals(2, w.current().getTicks());
	}

	@Test
	public void tearsLostToTheAirAndSpentAtTheMarket()
	{
		DiveWatch w = new DiveWatch();
		w.tick(1, T0, under(100, 10, 0, 0));
		w.tick(2, T0, under(101, 11, 0, 0));
		// Out of air: "between 10 and 20" mermaid's tears gone, with nothing open.
		w.tick(3, T0, under(86, 11, 0, 0));
		assertEquals(15, w.current().getLost());
		// Mairin's Market: 30 for a bowl of fish.
		w.tick(4, T0, new DiveWatch.Reading(true, true, false, 56, 11, 0, 0));
		assertEquals(30, w.current().getSpent());
		assertEquals(15, w.current().getLost());
		assertEquals(1, w.current().getTears());
	}

	@Test
	public void aHandInToMairinIsCountedApart()
	{
		DiveWatch w = new DiveWatch();
		w.tick(1, T0, under(10, 300, 5_000_000, 6_000_000));
		w.tick(2, T0, under(11, 301, 5_000_004, 6_000_005));
		// 301 tears for both skills at 80 and 85: Mairin pays thousands in a tick.
		DiveWatch.Step s = w.tick(3, T0, under(11, 0, 5_034_667, 6_143_000));
		assertNull(s.getHandIn());
		// The experience may land a tick after the tears; it is still hers.
		s = w.tick(4, T0, under(11, 0, 5_034_670, 6_143_004));
		assertNull(s.getHandIn());
		w.tick(5, T0, under(11, 0, 5_034_670, 6_143_004));
		s = w.tick(7, T0, under(11, 0, 5_034_670, 6_143_004));
		DiveWatch.HandIn h = s.getHandIn();
		assertNotNull(h);
		assertEquals(301, h.getTears());
		assertEquals(34_666, h.getAgilityXp());
		assertEquals(142_999, h.getThievingXp());
		// None of it is a search.
		assertEquals(1, w.current().getSearches());
		assertEquals(5, w.current().getThievingXp());

		UnderwaterLog log = new UnderwaterLog();
		log.handIn(h.getTears(), h.getAgilityXp(), h.getThievingXp());
		assertSame(Underwater.Mode.BOTH, log.mode());
		log.handIn(10, 0, 9_000);
		assertSame(Underwater.Mode.THIEVING, log.mode());
		assertEquals(311, log.getHandedIn());
	}

	@Test
	public void aDiveEndsOutOfTheWaterOrIdle()
	{
		DiveWatch w = new DiveWatch();
		w.tick(1, T0, under(0, 0, 0, 0));
		w.tick(2, T0, under(1, 1, 4, 4));
		w.tick(102, T0, under(2, 2, 9, 9));
		UnderwaterLog.Dive filed = null;
		for (int t = 103; t < 103 + DiveWatch.OUT_TICKS && filed == null; t++)
		{
			filed = w.tick(t, T0, new DiveWatch.Reading(false, false, true, 2, 2, 9, 9)).getFiled();
		}
		assertNotNull(filed);
		assertEquals(100, filed.getTicks());
		assertEquals(2, filed.getTears());
		assertFalse(w.isDiving());
		// Tears banked above water are not lost.
		assertEquals(0, filed.getLost());

		w.tick(500, T0, under(0, 0, 9, 9));
		w.tick(501, T0, under(1, 1, 13, 13));
		assertNull(w.tick(501 + DiveWatch.IDLE_TICKS - 1, T0, under(1, 1, 13, 13)).getFiled());
		assertNotNull(w.tick(501 + DiveWatch.IDLE_TICKS, T0, under(1, 1, 13, 13)).getFiled());

		// A hop or logout ends the dive under way, and the next reading is only a baseline.
		w.tick(2000, T0, under(1, 1, 13, 13));
		w.tick(2001, T0, under(2, 2, 17, 17));
		assertNotNull(w.reset());
		w.tick(2002, T0, under(400, 2, 17, 17));
		assertFalse(w.isDiving());
	}

	@Test
	public void theLogKeepsDivesAndTheBest()
	{
		UnderwaterLog log = new UnderwaterLog();
		assertFalse(log.file(dive(1200, 40)));
		assertEquals(200.0, log.getBestRate(), 0.01);
		assertTrue(log.file(dive(1200, 48)));
		assertFalse(log.file(dive(600, 40)));
		assertFalse(log.file(new UnderwaterLog.Dive(T0)));
		assertEquals(3, log.getDives().size());
		assertEquals(3, log.getTotal().getCount());
		assertEquals(128, log.getTotal().getTears());
		for (int i = 0; i < UnderwaterLog.RECENT + 5; i++)
		{
			log.file(dive(100, 1));
		}
		assertEquals(UnderwaterLog.RECENT, log.getDives().size());
		assertEquals(UnderwaterLog.RECENT + 8, log.getTotal().getCount());

		log.handIn(5, 100, 200);
		log.bankTears(250);
		log.levels(70, 80);
		UnderwaterLog back = UnderwaterLog.read(gson, gson.toJson(log));
		assertEquals(log.getTotal().getTears(), back.getTotal().getTears());
		assertEquals(250, back.getBankTears());
		assertSame(Underwater.Mode.BOTH, back.mode());
		assertEquals(80, back.getThievingLevel());

		assertEquals(-1, UnderwaterLog.read(gson, "{not json").getBankTears());
		assertEquals(0, UnderwaterLog.read(gson, "{\"dives\":null,\"total\":null,\"mode\":\"SWIMMING\"}").getDives().size());
		assertNull(UnderwaterLog.read(gson, "{\"mode\":\"SWIMMING\"}").mode());
	}

	@Test
	public void theCoachUsesTheWikiUntilTenMinutesOfYourOwn()
	{
		UnderwaterLog log = new UnderwaterLog();
		UnderwaterCoach c = UnderwaterCoach.of(log, null, false, 150, 0, 99, 99, Underwater.Reward.MERFOLK_TRIDENT);
		assertSame(AgilityCoach.Source.WIKI, c.getSource());
		assertEquals(200, c.getTearsPerHour(), 0.01);
		assertEquals(250, c.tearsNeeded());
		assertEquals(1.25, c.hoursToReward(), 0.001);
		// Both skills until the player hands in: 200 x 176.4.
		assertEquals(35_280, c.getAgilityPerHour());
		assertFalse(c.isModeKnown());

		// Twenty minutes of the player's own: 90 tears, 80 glistening, Thieving-only hand-ins.
		UnderwaterLog.Dive d = dive(2000, 90);
		d.glistening = 80;
		d.agilityXp = 500;
		d.thievingXp = 400;
		log.file(d);
		log.handIn(10, 0, 9702);
		log.bankTears(100);
		c = UnderwaterCoach.of(log, null, false, 150, 701, 99, 99, Underwater.Reward.MERFOLK_TRIDENT);
		assertSame(AgilityCoach.Source.YOURS, c.getSource());
		assertEquals(270, c.getTearsPerHour(), 0.01);
		assertEquals(250, c.getOwned());
		assertEquals(150, c.tearsNeeded());
		assertEquals(150 / 270.0, c.hoursToReward(), 0.001);
		assertEquals(1500, c.getAgilityPerHour());
		assertEquals(Math.round(240 * 970.2 + 1200), c.getThievingPerHour());
		assertTrue(c.isOverCap());
		assertEquals(700 * 9702L / 10, c.carriedThievingXp());
		assertEquals(0, c.carriedAgilityXp());

		// Enough already.
		c = UnderwaterCoach.of(log, null, false, 400, 0, 99, 99, Underwater.Reward.LARGE_FOSSIL);
		assertEquals(0, c.tearsNeeded());
		assertEquals(0, c.hoursToReward(), 0);
		// The dive under way counts toward the pace.
		UnderwaterLog.Dive live = dive(1000, 60);
		c = UnderwaterCoach.of(log, live, true, 0, 0, 0, 0, null);
		assertEquals(150 * 6000 / 3000.0, c.getTearsPerHour(), 0.01);
		assertEquals(2, c.getTotal().getCount());
		assertEquals(-1, c.hoursToReward(), 0);
		// Levels unknown and none saved: no experience rate is guessed.
		assertEquals(0, c.getAgilityPerHour());
	}

	@Test
	public void divesToTheReward()
	{
		UnderwaterLog log = new UnderwaterLog();
		log.file(dive(3000, 100));
		log.file(dive(3000, 120));
		log.file(dive(3000, 80));
		UnderwaterCoach c = UnderwaterCoach.of(log, null, false, 0, 0, 60, 60, Underwater.Reward.MERFOLK_TRIDENT);
		assertEquals(100, c.usualDiveTears());
		assertEquals(4, c.divesToReward());
		assertEquals(3, c.getRecent().size());
		assertEquals(80, c.getRecent().get(0).getTears());
	}

	@Test
	public void aScriptedDiveFromStartToHandIn()
	{
		// Ten minutes underwater, played tick by tick: a search every six ticks, clams and chests in turn,
		// one of each tear every third search, the air running out once (12 tears gone), a hand-in to
		// Mairin for Thieving alone, then back to the boat.
		DiveWatch w = new DiveWatch();
		UnderwaterLog log = new UnderwaterLog();
		int m = 0;
		int g = 0;
		int a = 1_000_000;
		int th = 2_000_000;
		int searches = 0;
		int tick = 0;
		w.tick(tick, T0, under(m, g, a, th));
		for (tick = 1; tick <= 1000; tick++)
		{
			if (tick % 6 == 0)
			{
				w.clicked(searches % 2 == 0 ? Underwater.Spot.CLAM : Underwater.Spot.CHEST, tick - 2);
				searches++;
				int xp = searches % 2 == 0 ? 5 : 4;
				a += xp;
				th += xp;
				if (searches % 3 == 0)
				{
					m++;
					g++;
				}
			}
			if (tick == 500)
			{
				m -= 12;
			}
			DiveWatch.Step s = w.tick(tick, T0, under(m, g, a, th));
			assertNull(s.getFiled());
		}
		assertEquals(166, searches);
		assertEquals(83, w.current().getClams());
		assertEquals(83, w.current().getChests());
		assertEquals(55, w.current().getTears());
		assertEquals(12, w.current().getLost());
		assertEquals(996 - 6, w.current().getTicks());

		// 55 glistening tears for Thieving at 75: 0.099 x 5625 = 556.8 each.
		int pay = 55 * Underwater.Mode.THIEVING.thievingTenths(75) / 10;
		g = 0;
		th += pay;
		for (int i = 0; i < 4; i++)
		{
			DiveWatch.Step s = w.tick(tick++, T0, under(m, g, a, th));
			if (s.getHandIn() != null)
			{
				log.handIn(s.getHandIn().getTears(), s.getHandIn().getAgilityXp(), s.getHandIn().getThievingXp());
			}
		}
		assertEquals(55, log.getHandedIn());
		assertEquals(pay, log.getHandInThievingXp());
		assertSame(Underwater.Mode.THIEVING, log.mode());

		UnderwaterLog.Dive filed = null;
		for (int i = 0; i < DiveWatch.OUT_TICKS && filed == null; i++)
		{
			filed = w.tick(tick++, T0, new DiveWatch.Reading(false, false, false, m, g, a, th)).getFiled();
		}
		assertNotNull(filed);
		log.file(filed);
		// The hand-in at Mairin is part of the dive: 55 tears in 997 ticks, 331 an hour. The search
		// experience is the searches', not Mairin's.
		assertEquals(997, filed.getTicks());
		assertEquals(331, Math.round(filed.tearsPerHour()));
		assertEquals(th - 2_000_000 - pay, filed.getThievingXp());
		UnderwaterCoach c = UnderwaterCoach.of(log, null, false, m, g, 75, 75, Underwater.Reward.MERFOLK_TRIDENT);
		assertSame(AgilityCoach.Source.WIKI, c.getSource());
		assertEquals(400 - 43, c.tearsNeeded());
	}

	private static UnderwaterLog.Dive dive(long ticks, long tears)
	{
		UnderwaterLog.Dive d = new UnderwaterLog.Dive(T0);
		d.ticks = ticks;
		d.tears = tears;
		d.glistening = tears;
		d.clams = (int) tears;
		return d;
	}
}
