package com.codex.sessionchronicle.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/** The slayer task as a session lives it: its progress, the kills made on it, and its end. */
public class SlayerTaskTest
{
	private static final long T0 = 1_790_000_000_000L;
	private static final long MIN = 60_000L;
	private static final List<Session.Drop> NONE = Collections.emptyList();
	private Chronicle c;

	@Before
	public void begin()
	{
		c = new Chronicle();
		c.begin(T0, "Zezima", 15 * MIN);
	}

	/** Plays on for so many minutes, a tick at a time is not needed: play time is what moves. */
	private long play(long at, int minutes)
	{
		for (int i = 1; i <= minutes * 20; i++)
		{
			c.tick(at + i * 3_000L);
		}
		return at + minutes * MIN;
	}

	@Test
	public void killsCountDownAndThePaceSaysHowLongIsLeft()
	{
		assertNull(c.slayer("Abyssal demons", 120, 120, true, T0));
		SlayerTask task = c.current().getSlayer();
		assertEquals("Abyssal demons", task.getName());
		assertEquals(-1, task.etaMs());
		long t = T0;
		for (int left = 119; left >= 100; left--)
		{
			t = play(t, 1);
			c.slayer("Abyssal demons", left, 120, false, t);
		}
		assertSame(task, c.current().getSlayer());
		assertEquals(20, task.killedHere());
		assertEquals(100, task.getLeft());
		assertEquals(20.0 / 120, task.progress(), 1e-9);
		// Twenty kills in twenty minutes: a hundred more take a hundred minutes.
		assertEquals(100 * MIN, task.etaMs(), 3_000);
		assertEquals(60, task.perHour(), 0.5);
	}

	@Test
	public void theLastKillFinishesTheTaskAsAMoment()
	{
		c.slayer("Gargoyles", 2, 150, true, T0);
		c.slayer("Gargoyles", 1, 150, false, T0 + MIN);
		Moment done = c.slayer(null, 0, 0, false, T0 + 2 * MIN);
		assertNotNull(done);
		assertSame(Moment.Kind.TASK, done.getKind());
		assertEquals("150 Gargoyles", done.getTitle());
		assertTrue(done.getDetail().startsWith("Slayer task done"));
		assertNull(c.current().getSlayer());
		assertEquals(1, c.current().getSlayerDone().size());
		assertTrue(c.current().getSlayerDone().get(0).isDone());
		assertEquals(1.0, c.current().getSlayerDone().get(0).progress(), 1e-9);
	}

	@Test
	public void aCancelledTaskIsNoMoment()
	{
		c.slayer("Spiritual creatures", 140, 140, true, T0);
		assertNull("skipped at a slayer master with 140 left", c.slayer(null, 0, 0, false, T0 + MIN));
		assertTrue(c.current().getSlayerDone().isEmpty());
		// A new task straight away is a new task, not progress.
		c.slayer("Spiritual creatures", 160, 160, false, T0 + 2 * MIN);
		assertEquals(0, c.current().getSlayer().killedHere());
	}

	@Test
	public void killsMadeElsewhereWhileAwayAreNotThisSessions()
	{
		c.slayer("Hellhounds", 100, 140, true, T0);
		c.slayer("Hellhounds", 95, 140, false, T0 + MIN);
		c.pause(T0 + 2 * MIN);
		c.begin(T0 + 10 * MIN, "Zezima", 15 * MIN);
		// Thirty killed on the mobile app meanwhile.
		c.slayer("Hellhounds", 65, 140, true, T0 + 10 * MIN);
		assertEquals(5, c.current().getSlayer().killedHere());
		assertEquals(65, c.current().getSlayer().getLeft());
	}

	@Test
	public void eachKillLineKnowsTheTaskItWasMadeOn()
	{
		c.loot("Vorkath", NONE, 1, false, 0, T0);
		c.slayer("Blue dragons", 1, 40, true, T0 + MIN);
		c.loot("Vorkath", NONE, 1, false, 0, T0 + 2 * MIN);
		c.loot("Vorkath", NONE, 1, false, 0, T0 + 3 * MIN);
		c.slayer(null, 0, 0, false, T0 + 4 * MIN);
		// The last kill's loot lands a moment after the count reached nothing: it is still the task's.
		c.loot("Vorkath", NONE, 1, false, 0, T0 + 4 * MIN + 2_000);
		c.loot("Vorkath", NONE, 1, false, 0, T0 + 6 * MIN);
		c.loot("Bird nest", NONE, 1, true, 0, T0 + 6 * MIN);
		Map<String, Integer> tasks = c.current().kills.get("vorkath").getTasks();
		assertEquals(Integer.valueOf(3), tasks.get("Blue dragons"));
		assertEquals(Integer.valueOf(2), tasks.get(""));
		assertTrue("opened loot is no kill on any task", c.current().kills.get("bird nest").getTasks().isEmpty());
	}

	@Test
	public void theJournalKeepsTheTasks()
	{
		c.slayer("Gargoyles", 1, 150, true, T0);
		c.loot("Gargoyle", NONE, 1, false, 0, T0 + MIN);
		c.slayer(null, 0, 0, false, T0 + MIN);
		c.slayer("Kraken", 100, 100, false, T0 + 2 * MIN);
		Session filed = Journal.file(Collections.emptyList(), c.close(), 100).get(0);
		assertEquals(1, filed.getSlayerDone().size());
		assertEquals("Kraken", filed.getSlayer().getName());
		assertFalse(filed.getSlayer().isDone());
	}
}
