package com.codex.sessionchronicle.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import org.junit.Test;

/** Sailing port tasks handed in during a session, read from the game's count of port tasks completed. */
public class PortTasksTest
{
	private static final long MIN = 60_000;
	private static final long GAP = 15 * MIN;

	@Test
	public void onlyARiseThePlayerCouldMakeIsAPortTaskDone()
	{
		assertEquals(1, Chronicle.portTasksDone(40, 41));
		assertEquals(3, Chronicle.portTasksDone(40, 43));
		assertEquals("five tasks held at once can be handed in together", 5, Chronicle.portTasksDone(40, 45));
		assertEquals("read afresh after a login or hop", 0, Chronicle.portTasksDone(-1, 41));
		assertEquals("the count arriving after a zero reading", 0, Chronicle.portTasksDone(0, 312));
		assertEquals("more than can be held at once", 0, Chronicle.portTasksDone(40, 46));
		assertEquals(0, Chronicle.portTasksDone(41, 41));
		assertEquals(0, Chronicle.portTasksDone(41, 40));
	}

	@Test
	public void portTasksAreRoundsWithTheirOwnLineAndHeadline()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.tick(0);
		c.xp("Sailing", 1_000_000, 70, 0);
		c.xp("Sailing", 1_090_000, 70, 30 * MIN);
		// As the plugin tells it: a rise from 311 to 312, then two at once to 314, then three to 317.
		c.killCount(Chronicle.PORT_TASKS, 312);
		c.killCount(Chronicle.PORT_TASKS, 312);
		c.killCount(Chronicle.PORT_TASKS, 313);
		c.killCount(Chronicle.PORT_TASKS, 314);
		c.killCount(Chronicle.PORT_TASKS, 315);
		c.killCount(Chronicle.PORT_TASKS, 317);
		c.current().playedMs = 60 * MIN;

		assertEquals("port tasks are no kills", 0, c.current().totalKills());
		Summary s = Summary.of(c.current(), Summary.State.LIVE);
		assertTrue(s.getKillLines().isEmpty());
		assertEquals(1, s.getRoundLines().size());
		Session.KillLine tasks = s.getRoundLines().get(0);
		assertTrue(tasks.isRound());
		assertFalse(tasks.isKill());
		assertEquals(6, tasks.count());
		assertEquals(317, tasks.getKillCount());
		assertEquals("tasks", Summary.noun(Chronicle.PORT_TASKS));
		assertEquals("6 Port tasks", s.headline());
		assertEquals(6, Goal.progress(c.current(), Goal.Kind.ROUNDS));
		boolean paced = false;
		for (Pace.Line line : Pace.of(c.current(), Collections.emptyList()))
		{
			paced |= line.getKind() == Pace.Kind.ROUNDS && Chronicle.PORT_TASKS.equals(line.getName());
		}
		assertTrue(paced);
	}

	@Test
	public void oneTaskIsTold()
	{
		Session.KillLine one = new Session.KillLine(Chronicle.PORT_TASKS);
		one.looted = 1;
		assertEquals("1 Port task", Summary.rounds(one));
	}
}
