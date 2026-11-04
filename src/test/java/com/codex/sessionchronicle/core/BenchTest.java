package com.codex.sessionchronicle.core;

import com.codex.sessionchronicle.overlay.BenchPlaque;
import com.codex.sessionchronicle.ui.BenchRow;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The bench: make-X actions read from the pack tick by tick, the player's own pace, and the pack and bank
 * supplies. Item ids here are placeholders (no game data is used by the bench), named for what they stand for.
 */
public class BenchTest
{
	private static final int KNIFE = 1;
	private static final int LOGS = 10;
	private static final int BOW = 11;
	private static final int UNF = 20;
	private static final int HERB = 21;
	private static final int WATER = 22;
	private static final int SHAFTS = 30;
	private static final int FEATHERS = 31;
	private static final int HEADLESS = 32;
	private static final int POTION_4 = 40;
	private static final int POTION_3 = 41;

	private final Gson gson = new Gson();
	private final Bench bench = new Bench();
	private final BenchLog log = new BenchLog();

	private static Map<Integer, Integer> pack(int... idCount)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < idCount.length; i += 2)
		{
			if (idCount[i + 1] > 0)
			{
				m.put(idCount[i], idCount[i + 1]);
			}
		}
		return m;
	}

	private boolean tick(int tick, Map<Integer, Integer> pack, String skill)
	{
		return bench.tick(tick, pack, false, skill, 1_000L * tick, log, id -> id == BOW ? "Maple longbow (u)" : "Item " + id);
	}

	/** Cuts a load of logs into bows, one every {@code every} ticks from {@code start}; returns the tick of the last. */
	private int fletchLoad(int start, int logs, int every)
	{
		tick(start - 1, pack(KNIFE, 1, LOGS, logs), null);
		int t = start;
		for (int made = 1; made <= logs; made++, t += every)
		{
			tick(t, pack(KNIFE, 1, LOGS, logs - made, BOW, made), "Fletching");
		}
		return t - every;
	}

	@Test
	public void learnsARecipeFromExperienceAndCountsWhatThePackHolds()
	{
		tick(0, pack(KNIFE, 1, LOGS, 27), null);
		assertTrue(tick(3, pack(KNIFE, 1, LOGS, 26, BOW, 1), "Fletching"));
		assertEquals(1, log.size());
		BenchLog.Recipe r = log.byItems(java.util.Collections.singleton(LOGS));
		assertNotNull(r);
		assertEquals("Maple longbow (u)", r.getName());
		assertEquals("Fletching", r.getSkill());
		assertEquals(BOW, r.getProduct());
		Bench.Progress p = bench.progress(3, log);
		assertEquals(26, p.getLeft());
		assertEquals(1, p.getDone());
		assertTrue(p.isRunning());
		assertTrue("no pace before three are timed", p.getLoadMs() < 0);
		assertEquals("Learning your pace", BenchRow.status(p));
	}

	@Test
	public void learnsThePaceFromTheTicksBetweenActions()
	{
		tick(0, pack(KNIFE, 1, LOGS, 27), null);
		for (int i = 1; i <= 5; i++)
		{
			tick(3 * i, pack(KNIFE, 1, LOGS, 27 - i, BOW, i), "Fletching");
		}
		Bench.Progress p = bench.progress(15, log);
		assertEquals(3.0, p.getPaceTicks(), 0.001);
		assertEquals(22, p.getLeft());
		// 22 more at 3 ticks (1.8s) each, none of the next under way yet.
		assertEquals(22 * 3 * 600, p.getLoadMs());
		assertEquals("About 0:39 to go", BenchRow.status(p));
		// A tick into the next action, the clock has moved on by it.
		assertEquals((22 * 3 - 1) * 600, bench.progress(16, log).getLoadMs());
		assertEquals("1.8s each, from 5 of your actions.", BenchRow.paceLine(p));
	}

	@Test
	public void unfinishedPotionsGiveNoExperienceSoTheyAreLearntFromThreeInARow()
	{
		tick(0, pack(HERB, 14, WATER, 14), null);
		assertFalse(tick(2, pack(HERB, 13, WATER, 13, UNF, 1), null));
		assertFalse(tick(4, pack(HERB, 12, WATER, 12, UNF, 2), null));
		assertEquals(0, log.size());
		assertTrue(tick(6, pack(HERB, 11, WATER, 11, UNF, 3), null));
		BenchLog.Recipe r = log.byItems(new java.util.HashSet<>(java.util.Arrays.asList(HERB, WATER)));
		assertNotNull(r);
		assertNull(r.getSkill());
		assertEquals(3, r.getActions());
		assertEquals(2, r.timed());
		Bench.Progress p = bench.progress(6, log);
		assertEquals(3, p.getDone());
		assertEquals(11, p.getLeft());
		assertEquals("your pace", BenchRow.trailing(p));
	}

	@Test
	public void aPotionDrunkIsNoActionAndABankOpenIsNone()
	{
		tick(0, pack(POTION_4, 1), null);
		assertFalse(tick(1, pack(POTION_3, 1), null));
		assertEquals(0, log.size());
		// Taking logs out and putting bows in with the bank open is not fletching.
		bench.tick(2, pack(KNIFE, 1, BOW, 27), false, null, 2, log, id -> "x");
		assertFalse(bench.tick(3, pack(KNIFE, 1, LOGS, 27), true, "Fletching", 3, log, id -> "x"));
		assertEquals(0, log.size());
	}

	@Test
	public void twoActionsInOneTickCountAsTwo()
	{
		tick(0, pack(KNIFE, 1, LOGS, 27), null);
		tick(3, pack(KNIFE, 1, LOGS, 26, BOW, 1), "Fletching");
		tick(6, pack(KNIFE, 1, LOGS, 24, BOW, 3), "Fletching");
		assertEquals(3, bench.progress(6, log).getDone());
		assertEquals(3, log.byItems(java.util.Collections.singleton(LOGS)).getActions());
		// A double tick is no measure of one action's time.
		assertEquals(0, log.byItems(java.util.Collections.singleton(LOGS)).timed());
	}

	@Test
	public void theLastShortActionOfAStackIsCountedAndLeftIsRoundedUp()
	{
		tick(0, pack(SHAFTS, 37, FEATHERS, 37), null);
		tick(2, pack(SHAFTS, 22, FEATHERS, 22, HEADLESS, 15), "Fletching");
		BenchLog.Recipe r = log.byItems(new java.util.HashSet<>(java.util.Arrays.asList(SHAFTS, FEATHERS)));
		assertEquals(15, (int) r.getInputs().get(SHAFTS));
		assertEquals(1, bench.progress(2, log).getLeft());
		tick(4, pack(SHAFTS, 7, FEATHERS, 7, HEADLESS, 30), "Fletching");
		tick(6, pack(HEADLESS, 37), "Fletching");
		assertEquals(3, r.getActions());
		assertTrue(r.partial);
		// Once a short last action has been seen, seven shafts count as one more.
		assertEquals(1, r.left(pack(SHAFTS, 7, FEATHERS, 9)));
	}

	@Test
	public void banksTripsAndLoadsAreLearntAndTheBankSuppliesAreTimed()
	{
		int end = fletchLoad(1, 27, 3);
		assertFalse(bench.progress(end, log).isRunning());
		assertEquals("Load done", BenchRow.status(bench.progress(end, log)));
		// The bank: 270 logs in it.
		bench.sawBank(pack(LOGS, 270), 5_000, log);
		bench.tick(end + 5, pack(KNIFE, 1, LOGS, 27), true, null, 0, log, id -> "x");
		int second = end + 30;
		bench.forget();
		int last = fletchLoad(second, 27, 3);
		BenchLog.Recipe r = log.byItems(java.util.Collections.singleton(LOGS));
		assertEquals("a fresh load after the bank", 27, r.loadSize());
		assertEquals(1, r.gaps.size());
		assertEquals(30, (int) r.gaps.get(0));
		assertEquals(27.0, r.reloadTicks(), 0.001);

		// Halfway through a third load, with 216 in the bank.
		bench.sawBank(pack(LOGS, 216), 9_000, log);
		bench.tick(last + 5, pack(KNIFE, 1, LOGS, 27), true, null, 0, log, id -> "x");
		bench.forget();
		int third = last + 30;
		tick(third - 1, pack(KNIFE, 1, LOGS, 27), null);
		for (int i = 1; i <= 13; i++)
		{
			tick(third + 3 * (i - 1), pack(KNIFE, 1, LOGS, 27 - i, BOW, i), "Fletching");
		}
		int now = third + 3 * 12;
		Bench.Progress p = bench.progress(now, log);
		assertEquals(14, p.getLeft());
		assertEquals(14 + 216, p.getTotal());
		assertEquals(8, p.getReloads());
		assertTrue(p.isReloadKnown());
		assertEquals((230 * 3 + 8 * 27) * 600L, p.getTotalMs());
		assertEquals("Pack and bank: 230 more, about 9m with 8 bank trips.", BenchRow.supplyLine(p));
	}

	@Test
	public void withoutTheBankSeenTheSuppliesWaitForIt()
	{
		fletchLoad(1, 5, 3);
		Bench.Progress p = bench.progress(10, log);
		assertEquals(-1, p.getTotal());
		assertEquals("Open your bank once and the rest of your supplies are counted too.", BenchRow.supplyLine(p));
	}

	@Test
	public void aBreakEndsTheBatchAndALongOneHidesIt()
	{
		tick(0, pack(KNIFE, 1, LOGS, 27), null);
		tick(3, pack(KNIFE, 1, LOGS, 26, BOW, 1), "Fletching");
		assertTrue(bench.progress(3 + Bench.MIN_GAP, log).isRunning());
		Bench.Progress paused = bench.progress(4 + Bench.MIN_GAP, log);
		assertFalse(paused.isRunning());
		assertEquals("Paused", BenchRow.status(paused));
		assertNull(bench.progress(4 + Bench.TRIP_TICKS, log));
		// The plaque shows a load just finished, never a batch paused part way.
		BenchPlaque plaque = new BenchPlaque();
		plaque.setEnabled(true);
		plaque.show(paused);
		assertNull(plaque.shown());
	}

	@Test
	public void theLogKeepsItsShapeThroughASaveAndSurvivesADamagedOne()
	{
		fletchLoad(1, 10, 3);
		bench.sawBank(pack(LOGS, 50, 999, 3), 7_000, log);
		BenchLog back = BenchLog.read(gson, gson.toJson(log));
		BenchLog.Recipe r = back.byItems(java.util.Collections.singleton(LOGS));
		assertEquals(10, r.getActions());
		assertEquals(3.0, r.paceTicks(), 0.001);
		assertEquals(50, back.banked(LOGS));
		assertEquals("items no recipe uses are not kept", -1, back.banked(999));
		assertEquals(0, BenchLog.read(gson, "{\"recipes\":5}").size());
		assertEquals(0, BenchLog.read(gson, "not json").size());
		assertEquals(0, BenchLog.read(gson, null).size());
		BenchLog mended = BenchLog.read(gson, "{\"recipes\":{\"10x1\":{\"inputs\":{\"10\":1},\"product\":11}}}");
		assertEquals(1, mended.size());
		assertEquals(-1, mended.byItems(java.util.Collections.singleton(LOGS)).paceTicks(), 0.001);
	}

	@Test
	public void theOldestRecipeMakesWayPastTheCap()
	{
		for (int i = 0; i <= BenchLog.MAX_RECIPES; i++)
		{
			Map<Integer, Integer> in = pack(1000 + i, 1);
			BenchLog.Recipe r = log.learn(in, 5000 + i, "x", "Cooking", null);
			r.lastAt = 1 + i;
		}
		assertEquals(BenchLog.MAX_RECIPES, log.size());
		assertNull(log.byItems(java.util.Collections.singleton(1000)));
	}

	@Test
	public void theLastOneIsToldQuietly()
	{
		tick(0, pack(KNIFE, 1, LOGS, 5), null);
		for (int i = 1; i <= 4; i++)
		{
			tick(3 * i, pack(KNIFE, 1, LOGS, 5 - i, BOW, i), "Fletching");
		}
		Bench.Progress p = bench.progress(12, log);
		assertEquals(1, p.getLeft());
		assertEquals("Last one, 0:01", BenchRow.status(p));
	}

	@Test
	public void aBurntOneIsStillAnActionOfTheSameBatch()
	{
		int raw = 50;
		int cooked = 51;
		int burnt = 52;
		tick(0, pack(raw, 28), null);
		tick(4, pack(raw, 27, cooked, 1), "Cooking");
		tick(8, pack(raw, 26, cooked, 1, burnt, 1), null);
		tick(12, pack(raw, 25, cooked, 2, burnt, 1), "Cooking");
		Bench.Progress p = bench.progress(12, log);
		assertEquals(3, p.getDone());
		assertEquals(25, p.getLeft());
		assertEquals(1, log.size());
		assertEquals(cooked, p.getProduct());
	}
}
