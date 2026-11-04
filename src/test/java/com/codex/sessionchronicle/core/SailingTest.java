package com.codex.sessionchronicle.core;

import static org.junit.Assert.assertEquals;
import net.runelite.api.Skill;
import org.junit.Test;

/** Sailing, the 24th skill, in a session: its experience, its activity and its headline. */
public class SailingTest
{
	private static final long MIN = 60_000;
	private static final long GAP = 15 * MIN;

	@Test
	public void aSessionAtSeaIsASailingSession()
	{
		// RuneLite's own skill list carries Sailing, and the plugin reads every skill from it.
		assertEquals(24, Skill.values().length);
		assertEquals("Sailing", Skill.SAILING.getName());

		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.tick(0);
		c.xp("Sailing", 1_000_000, 70, 0);
		c.xp("Sailing", 1_090_000, 70, 30 * MIN);
		c.xp("Hitpoints", 500_000, 60, 0);
		c.xp("Hitpoints", 502_000, 60, 30 * MIN);
		c.current().playedMs = 60 * MIN;

		assertEquals("Sailing", Pace.activity(c.current()));
		Summary s = Summary.of(c.current(), Summary.State.LIVE);
		assertEquals("Sailing", s.getSkills().get(0).getSkill());
		assertEquals("Sailing at 90k/hr", s.headline());
	}
}
