package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The random event facts, the watch that follows an event to its end, the log and the report. */
public class RandomEventsTest
{
	private static final long T0 = 1_700_000_000_000L;
	private final Gson gson = new Gson();
	private final RandomWatch.Spot home = new RandomWatch.Spot(3200, 3200, 0);
	private final RandomWatch.Spot away = new RandomWatch.Spot(2500, 4800, 0);

	private static Map<Integer, Long> pack(long... idQty)
	{
		Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			m.put((int) idQty[i], idQty[i + 1]);
		}
		return m;
	}

	private static String name(int id)
	{
		return id == ItemID.MACRO_GENILAMP ? "Lamp" : id == ItemID.DRILL_TOP ? "Camo top" : id == ItemID.LOGS ? "Logs" : "Item";
	}

	@Test
	public void everyEventNpcRuneLiteListensForIsKnown()
	{
		// The NPCs RuneLite's own Random Events plugin listens for, each named to its event.
		assertEquals(RandomEvents.Event.BEEKEEPER, RandomEvents.Event.ofNpc(NpcID.MACRO_BEEKEEPER_INVITATION));
		assertEquals(RandomEvents.Event.CAPT_ARNAV, RandomEvents.Event.ofNpc(NpcID.MACRO_COMBILOCK_PIRATE));
		assertEquals(RandomEvents.Event.JEKYLL, RandomEvents.Event.ofNpc(NpcID.MACRO_JEKYLL_UNDERWATER));
		assertEquals(RandomEvents.Event.DRUNKEN_DWARF, RandomEvents.Event.ofNpc(NpcID.MACRO_DWARF));
		assertEquals(RandomEvents.Event.SURPRISE_EXAM, RandomEvents.Event.ofNpc(NpcID.PATTERN_INVITATION));
		assertEquals(RandomEvents.Event.EVIL_BOB, RandomEvents.Event.ofNpc(NpcID.MACRO_EVIL_BOB_OUTSIDE));
		assertEquals(RandomEvents.Event.PRISON_PETE, RandomEvents.Event.ofNpc(NpcID.MACRO_EVIL_BOB_PRISON));
		assertEquals(RandomEvents.Event.PINBALL, RandomEvents.Event.ofNpc(NpcID.PINBALL_INVITATION));
		assertEquals(RandomEvents.Event.FREAKY_FORESTER, RandomEvents.Event.ofNpc(NpcID.MACRO_FORESTER_INVITATION));
		assertEquals(RandomEvents.Event.KISS_THE_FROG, RandomEvents.Event.ofNpc(NpcID.MACRO_FROG_CRIER));
		assertEquals(RandomEvents.Event.GENIE, RandomEvents.Event.ofNpc(NpcID.MACRO_GENI_UNDERWATER));
		assertEquals(RandomEvents.Event.CERTERS, RandomEvents.Event.ofNpc(NpcID.MACRO_GILES));
		assertEquals(RandomEvents.Event.CERTERS, RandomEvents.Event.ofNpc(NpcID.MACRO_MILES_UNDERWATER));
		assertEquals(RandomEvents.Event.GRAVEDIGGER, RandomEvents.Event.ofNpc(NpcID.MACRO_GRAVEDIGGER_INVITATION));
		assertEquals(RandomEvents.Event.MYSTERIOUS_OLD_MAN, RandomEvents.Event.ofNpc(NpcID.MACRO_MYSTERIOUS_OLD_MAN));
		assertEquals(RandomEvents.Event.MAZE, RandomEvents.Event.ofNpc(NpcID.MACRO_MAZE_INVITATION));
		assertEquals(RandomEvents.Event.MIME, RandomEvents.Event.ofNpc(NpcID.MACRO_MIME_INVITATION));
		assertEquals(RandomEvents.Event.PILLORY, RandomEvents.Event.ofNpc(NpcID.MACRO_PILLORY_GUARD));
		assertEquals(RandomEvents.Event.EVIL_TWIN, RandomEvents.Event.ofNpc(NpcID.GRAB_POSTMAN));
		assertEquals(RandomEvents.Event.QUIZ_MASTER, RandomEvents.Event.ofNpc(NpcID.MACRO_MAGNESON_INVITATION));
		assertEquals(RandomEvents.Event.RICK_TURPENTINE, RandomEvents.Event.ofNpc(NpcID.MACRO_HIGHWAYMAN));
		assertEquals(RandomEvents.Event.SANDWICH_LADY, RandomEvents.Event.ofNpc(NpcID.MACRO_SANDWICH_LADY_NPC));
		assertEquals(RandomEvents.Event.DRILL_DEMON, RandomEvents.Event.ofNpc(NpcID.MACRO_DRILLDEMON_INVITATION));
		assertEquals(RandomEvents.Event.COUNT_CHECK, RandomEvents.Event.ofNpc(NpcID.MACRO_COUNTCHECK_SURFACE));
		assertEquals(RandomEvents.Event.STRANGE_PLANT, RandomEvents.Event.ofNpc(NpcID.MACRO_TRIFFIDSEED));
		assertNull(RandomEvents.Event.ofNpc(NpcID.MACRO_DRILLDEMON));
		// The wiki's table lists 24 events.
		assertEquals(24, RandomEvents.Event.values().length);
	}

	@Test
	public void costumesAsTheWikiGivesThem()
	{
		assertEquals(5, RandomEvents.Costume.BEEKEEPER.getCompletions());
		assertEquals(4, RandomEvents.Costume.CAMOUFLAGE.getCompletions());
		assertEquals(3, RandomEvents.Costume.LEDERHOSEN.getCompletions());
		assertEquals(5, RandomEvents.Costume.ZOMBIE.getCompletions());
		assertEquals(7, RandomEvents.Costume.MIME.getCompletions());
		assertEquals(3, RandomEvents.Costume.ROYAL_FROG.getCompletions());
		assertEquals(3, RandomEvents.Costume.CAMOUFLAGE.getPieces().length);
		assertEquals(26, Arrays.stream(RandomEvents.Costume.values()).mapToInt(c -> c.getPieces().length).sum());
		assertEquals(RandomEvents.Costume.CAMOUFLAGE, RandomEvents.Event.DRILL_DEMON.getCostume());
		assertNull(RandomEvents.Event.GENIE.getCostume());
		assertTrue(RandomEvents.Costume.isPiece(ItemID.MACRO_DIGGER_MASK));
		assertFalse(RandomEvents.Costume.isPiece(ItemID.GRAVEDIGGER_HAT));
		// The wiki's timer: a moment between one and two hours after the last event.
		assertEquals(3_600_000L, RandomEvents.TIMER_MIN_MS);
		assertEquals(7_200_000L, RandomEvents.TIMER_MAX_MS);
	}

	@Test
	public void anEventLeftAloneIsIgnoredWhenItGoes()
	{
		RandomWatch w = new RandomWatch();
		assertNull(w.spawned(RandomEvents.Event.GENIE, 7, 100, T0, "Woodcutting"));
		// The NPC turns to the player again and again; it is one event.
		assertNull(w.spawned(RandomEvents.Event.GENIE, 7, 110, T0, "Woodcutting"));
		assertNull(w.tick(150, home, pack(), RandomEventsTest::name));
		w.despawned(7, 200);
		RandomLog.Seen s = w.tick(200, home, pack(), RandomEventsTest::name);
		assertEquals(RandomLog.Outcome.IGNORED, s.getOutcome());
		assertEquals("Woodcutting", s.getActivity());
		assertFalse(w.isOpen());
	}

	@Test
	public void aDismissIsADismiss()
	{
		RandomWatch w = new RandomWatch();
		w.spawned(RandomEvents.Event.SANDWICH_LADY, 3, 100, T0, null);
		// Someone else's event is not the player's to dismiss.
		assertNull(w.clicked(RandomEvents.Event.CERTERS, 9, "Dismiss", 101, T0, null, home, pack()));
		w.clicked(RandomEvents.Event.SANDWICH_LADY, 3, "Dismiss", 102, T0, null, home, pack());
		assertEquals(RandomLog.Outcome.DISMISSED, w.tick(103, home, pack(), RandomEventsTest::name).getOutcome());
	}

	@Test
	public void anEventWhereThePlayerStandsGivesItsRewardInThePack()
	{
		RandomWatch w = new RandomWatch();
		w.spawned(RandomEvents.Event.GENIE, 7, 100, T0, "Woodcutting");
		w.clicked(RandomEvents.Event.GENIE, 7, "Talk-to", 104, T0, "Woodcutting", home, pack(ItemID.LOGS, 10));
		// A log that lands just after the click is the chopping already under way, not the reward.
		assertNull(w.tick(105, home, pack(ItemID.LOGS, 11), RandomEventsTest::name));
		assertNull(w.tick(106, home, pack(ItemID.LOGS, 11), RandomEventsTest::name));
		assertNull(w.tick(107, home, pack(ItemID.LOGS, 11), RandomEventsTest::name));
		RandomLog.Seen s = w.tick(112, home, pack(ItemID.LOGS, 11, ItemID.MACRO_GENILAMP, 1), RandomEventsTest::name);
		assertEquals(RandomLog.Outcome.DONE, s.getOutcome());
		assertEquals(1, s.getReward().size());
		assertEquals("Lamp", s.getReward().get(0).getName());
	}

	@Test
	public void anEventThatTakesThePlayerAwayPaysOnTheirReturn()
	{
		RandomWatch w = new RandomWatch();
		w.spawned(RandomEvents.Event.DRILL_DEMON, 5, 100, T0, "Mining");
		w.clicked(RandomEvents.Event.DRILL_DEMON, 5, "Talk-to", 101, T0, "Mining", home, pack(ItemID.IRON_ORE, 4));
		w.despawned(5, 104);
		assertNull(w.tick(104, away, pack(ItemID.IRON_ORE, 4), RandomEventsTest::name));
		// Away for a while: nothing is closed for the NPC having gone.
		for (int t = 105; t < 300; t++)
		{
			assertNull(w.tick(t, away, pack(ItemID.IRON_ORE, 4), RandomEventsTest::name));
		}
		assertNull(w.tick(300, home, pack(ItemID.IRON_ORE, 4, ItemID.DRILL_TOP, 1), RandomEventsTest::name));
		RandomLog.Seen s = w.tick(303, home, pack(ItemID.IRON_ORE, 4, ItemID.DRILL_TOP, 1), RandomEventsTest::name);
		assertEquals(RandomLog.Outcome.DONE, s.getOutcome());
		assertEquals(ItemID.DRILL_TOP, s.getReward().get(0).getId());
	}

	@Test
	public void anEventLeftOrFailedHasNoRewardSeen()
	{
		RandomWatch w = new RandomWatch();
		w.spawned(RandomEvents.Event.CERTERS, 2, 100, T0, null);
		w.clicked(RandomEvents.Event.CERTERS, 2, "Talk-to", 101, T0, null, home, pack());
		w.despawned(2, 120);
		assertNull(w.tick(121, home, pack(), RandomEventsTest::name));
		assertEquals(RandomLog.Outcome.TAKEN, w.tick(128, home, pack(), RandomEventsTest::name).getOutcome());
	}

	@Test
	public void aStrangePlantIsLoggedWhenPicked()
	{
		RandomWatch w = new RandomWatch();
		w.clicked(RandomEvents.Event.STRANGE_PLANT, 11, "Pick", 100, T0, "Fishing", home, pack());
		assertTrue(w.isOpen());
		RandomLog.Seen s = w.tick(110, home, pack(ItemID.MACRO_TRIFFIDFRUIT, 1), RandomEventsTest::name);
		assertEquals(RandomEvents.Event.STRANGE_PLANT, s.getEvent());
		assertEquals(RandomLog.Outcome.DONE, s.getOutcome());
	}

	@Test
	public void aHopEndsTheEventUnderWay()
	{
		RandomWatch w = new RandomWatch();
		w.spawned(RandomEvents.Event.MIME, 1, 100, T0, null);
		w.clicked(RandomEvents.Event.MIME, 1, "Talk-to", 101, T0, null, home, pack());
		assertEquals(RandomLog.Outcome.TAKEN, w.reset().getOutcome());
		assertNull(w.reset());
	}

	@Test
	public void theLogCountsAndKeepsAcrossSaves()
	{
		RandomLog log = new RandomLog();
		log.played("Woodcutting", 3 * 3_600_000L);
		log.played(null, 3_600_000L);
		log.file(new RandomLog.Seen(RandomEvents.Event.GENIE, T0, "Woodcutting", RandomLog.Outcome.DONE,
			Collections.singletonList(new RandomLog.Gain(ItemID.MACRO_GENILAMP, "Lamp", 1))));
		log.file(new RandomLog.Seen(RandomEvents.Event.DRILL_DEMON, T0 + 5_400_000L, "Woodcutting", RandomLog.Outcome.DISMISSED, null));
		log.file(new RandomLog.Seen(RandomEvents.Event.DRILL_DEMON, T0 + 10_800_000L, null, RandomLog.Outcome.DONE, null));
		assertTrue(log.sawPieces(Arrays.asList(ItemID.DRILL_TOP, ItemID.LOGS)));
		assertFalse(log.sawPieces(Collections.singletonList(ItemID.DRILL_TOP)));

		RandomLog back = RandomLog.read(gson, gson.toJson(log));
		assertEquals(3, back.getTotal().getSeen());
		assertEquals(2, back.kind(RandomEvents.Event.DRILL_DEMON).getSeen());
		assertEquals(1, back.kind(RandomEvents.Event.DRILL_DEMON).getAccepted());
		assertEquals(4 * 3_600_000L, back.getWatchedMs());
		assertEquals(T0 + 10_800_000L, back.getLastAt());
		assertEquals(Collections.singleton(ItemID.DRILL_TOP), back.getPieces());
		assertEquals(RandomEvents.Event.DRILL_DEMON, back.recent().get(0).getEvent());
		List<Map.Entry<String, RandomLog.Tally>> spent = back.activities(0);
		assertEquals("Woodcutting", spent.get(0).getKey());
		assertEquals(2, spent.get(0).getValue().getSeen());
		assertEquals(RandomWatch.NO_ACTIVITY, spent.get(1).getKey());

		// A damaged save, an unknown event or piece, and nothing saved all read without failing.
		assertEquals(0, RandomLog.read(gson, "{\"events\":[").getTotal().getSeen());
		assertEquals(0, RandomLog.read(gson, null).getTotal().getSeen());
		RandomLog odd = RandomLog.read(gson, "{\"events\":[{\"e\":\"NO_SUCH\"},null,{\"e\":\"GENIE\",\"outcome\":\"WHAT\"}],"
			+ "\"kinds\":{\"NO_SUCH\":{}},\"pieces\":[1,6654]}");
		assertEquals(1, odd.getEvents().size());
		assertEquals(RandomLog.Outcome.TAKEN, odd.getEvents().get(0).getOutcome());
		assertEquals(Collections.singleton(ItemID.DRILL_TOP), odd.getPieces());
	}

	@Test
	public void theReportSetsTheRateAgainstTheWikisTimer()
	{
		RandomLog log = new RandomLog();
		RandomReport r = RandomReport.of(log, T0, null);
		assertEquals(-1, r.msPerEvent());
		assertEquals(0, r.nextFrom());
		assertEquals(0.5, RandomReport.wikiLow(), 1e-9);
		assertEquals(1.0, RandomReport.wikiHigh(), 1e-9);
		log.played("Fishing", 6 * 3_600_000L);
		for (int i = 0; i < 4; i++)
		{
			log.file(new RandomLog.Seen(RandomEvents.Event.GENIE, T0 + i * 5_400_000L, "Fishing", RandomLog.Outcome.DONE, null));
		}
		r = RandomReport.of(log, T0 + 4 * 5_400_000L, null);
		assertEquals(5_400_000L, r.msPerEvent());
		assertEquals(0.667, r.perHour(), 0.001);
		assertTrue(r.isOwnRate());
		assertEquals(0, r.versusWiki());
		assertEquals(T0 + 3 * 5_400_000L + 3_600_000L, r.nextFrom());
		assertEquals(T0 + 3 * 5_400_000L + 7_200_000L, r.nextBy());
		log.played("Fishing", 20 * 3_600_000L);
		assertEquals(-1, RandomReport.of(log, T0, null).versusWiki());
	}

	@Test
	public void costumePiecesStillToCome()
	{
		RandomLog log = new RandomLog();
		log.sawPieces(Arrays.asList(ItemID.DRILL_TOP, ItemID.LADERHOSEN_HAT, ItemID.LADERHOSEN_TOP, ItemID.LADERHOSEN_LEGS));
		log.file(new RandomLog.Seen(RandomEvents.Event.DRILL_DEMON, T0, null, RandomLog.Outcome.DONE, null));
		RandomReport r = RandomReport.of(log, T0, null);
		assertEquals(4, r.piecesOwned());
		assertEquals(26, r.piecesAll());
		RandomReport.CostumeLine camo = r.getCostumes().get(RandomEvents.Costume.CAMOUFLAGE.ordinal());
		assertEquals(2, camo.getMissing().size());
		assertEquals(1, camo.getCompleted());
		// The wiki's four completions for three pieces, two of them missing: about three more.
		assertEquals(3, camo.completionsLeft());
		RandomReport.CostumeLine leder = r.getCostumes().get(RandomEvents.Costume.LEDERHOSEN.ordinal());
		assertTrue(leder.isDone());
		assertEquals(0, leder.completionsLeft());
		assertFalse(r.eventsWithPieces().contains(RandomEvents.Event.FREAKY_FORESTER));
		assertTrue(r.eventsWithPieces().contains(RandomEvents.Event.DRILL_DEMON));
		assertNotNull(r.eventsWithPieces());
	}
}
