package com.codex.sessionchronicle.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/** The slayer task read as the plugin reads it, tick by tick, through logins and world hops. */
public class SlayerWatchTest
{
	private static final long T0 = 1_790_000_000_000L;
	private static final long MIN = 60_000L;
	private Chronicle c;
	private SlayerWatch watch;
	private long now;

	@Before
	public void begin()
	{
		c = new Chronicle();
		c.begin(T0, "Zezima", 15 * MIN);
		watch = new SlayerWatch();
		now = T0;
	}

	/** One game tick as the plugin runs it: play time moves, then the task is read if it is due. */
	private Moment tick(int left)
	{
		now += 600;
		c.tick(now);
		watch.tick();
		if (!watch.isDue())
		{
			return null;
		}
		Boolean first = watch.take(left, c.hasSlayerTask());
		if (first == null)
		{
			return null;
		}
		return c.slayer(left > 0 ? "Abyssal demons" : null, left, 120, first, now);
	}

	/** A minute of play with a kill at the end of it. */
	private void kill(int left)
	{
		for (int i = 0; i < 99; i++)
		{
			tick(left + 1);
		}
		watch.moved();
		tick(left);
	}

	@Test
	public void aWorldHopKeepsTheTaskAndItsPace()
	{
		watch.arrived();
		tick(120);
		SlayerTask task = c.current().getSlayer();
		for (int left = 119; left >= 110; left--)
		{
			kill(left);
		}
		assertEquals(10, task.killedHere());
		long paceFrom = task.firstPlayMs;

		// The hop: the client takes the varps afresh, so the count reads nothing for a couple of ticks.
		watch.arrived();
		watch.moved();
		assertNull(tick(0));
		assertNull(tick(0));
		watch.moved();
		tick(110);

		assertSame("the same task carries on", task, c.current().getSlayer());
		assertEquals(paceFrom, task.firstPlayMs);
		assertEquals(10, task.killedHere());
		assertTrue(task.perHour() > 0);
		kill(109);
		assertEquals(11, task.killedHere());
	}

	@Test
	public void aHopNearTheEndIsNoFinishedTask()
	{
		watch.arrived();
		tick(3);
		SlayerTask task = c.current().getSlayer();
		watch.arrived();
		watch.moved();
		assertNull("no task-done moment from the count blinking to nothing", tick(0));
		watch.moved();
		tick(3);
		assertSame(task, c.current().getSlayer());
		assertFalse(task.isDone());
	}

	@Test
	public void aTaskStillMissingAfterTheWaitIsDroppedQuietly()
	{
		watch.arrived();
		tick(40);
		watch.arrived();
		watch.moved();
		for (int i = 0; i < SlayerWatch.ARRIVAL_TICKS + 2; i++)
		{
			assertNull(tick(0));
		}
		assertNull(c.current().getSlayer());
		assertTrue(c.current().getSlayerDone().isEmpty());
	}

	@Test
	public void theLastKillStillFinishesTheTaskOnceSettled()
	{
		watch.arrived();
		tick(2);
		for (int i = 0; i < SlayerWatch.ARRIVAL_TICKS; i++)
		{
			tick(2);
		}
		kill(1);
		watch.moved();
		Moment done = tick(0);
		assertNotNull(done);
		assertEquals(Moment.Kind.TASK, done.getKind());
	}

	@Test
	public void withoutATaskNothingWaits()
	{
		watch.arrived();
		assertFalse(watch.take(0, false) == null);
		assertFalse(watch.isDue());
	}
}
