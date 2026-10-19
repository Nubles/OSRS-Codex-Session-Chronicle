package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Chronicle;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.Summary;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The session as plain text for pasting into a chat. */
public class RecapTest
{
	private static final long T0 = 1_790_000_000_000L;
	private static final long MIN = 60_000L;

	private static Summary vorkath()
	{
		Chronicle c = new Chronicle();
		c.begin(T0, "Zezima", 15 * MIN);
		c.xp("Ranged", 13_000_000, 98, T0);
		c.xp("Hitpoints", 9_000_000, 96, T0);
		for (int i = 1; i <= 60 * 20; i++)
		{
			c.tick(T0 + i * 3_000L);
		}
		c.xp("Ranged", 13_100_000, 99, T0 + 60 * MIN);
		c.xp("Hitpoints", 9_030_000, 96, T0 + 60 * MIN);
		c.loot("Vorkath", Arrays.asList(new Session.Drop(11286, "Draconic visage", 1, 4_200_000),
			new Session.Drop(536, "Dragon bones", 2, 2_500)), 1, 500_000, T0 + 60 * MIN);
		c.killCount("Vorkath", 412);
		return Summary.of(c.current(), Summary.State.LIVE);
	}

	@Test
	public void namesTheTotalsAndTheBestOfEachList()
	{
		String text = Recap.text(vorkath(), true);
		String[] lines = text.split("\n");
		assertTrue(lines[0], lines[0].startsWith("Zezima's session, "));
		assertTrue(lines[0], lines[0].endsWith("1h 0m played"));
		assertTrue(text, text.contains("+130k xp (130k/hr)"));
		assertTrue(text, text.contains("1 level"));
		assertTrue(text, text.contains("Skills: Ranged +100k (98 to 99), Hitpoints +30k"));
		assertTrue(text, text.contains("Best loot: Draconic visage (4.2M), Dragon bones x2 (5,000)"));
		assertTrue(text, text.contains("Kills: Vorkath x1 (kc 412)"));
		assertTrue(text, text.contains("99 Ranged"));
		assertEquals("(Codex Session Chronicle)", lines[lines.length - 1]);
	}

	@Test
	public void leavesTheNameOffWhenTheCardDoes()
	{
		String text = Recap.text(vorkath(), false);
		assertTrue(text.startsWith("Session, "));
		assertFalse(text.contains("Zezima"));
	}

	@Test
	public void aQuietSessionStillReads()
	{
		Chronicle c = new Chronicle();
		c.begin(T0, "Zezima", 15 * MIN);
		c.tick(T0 + 3_000);
		String text = Recap.text(Summary.of(c.current(), Summary.State.LIVE), true);
		assertFalse(text.contains("Skills:"));
		assertFalse(text.contains("Best loot:"));
		assertFalse(text.contains("null"));
		assertTrue(text.split("\n").length >= 3);
		assertEquals(Collections.emptyList(), Collections.emptyList());
	}
}
