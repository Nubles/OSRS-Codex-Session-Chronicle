package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Ghost;
import com.codex.sessionchronicle.core.GhostFixtures;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/** The ghost row's words, and that it and the plaque draw. */
public class GhostRowTest
{
	@Test
	public void theGapIsToldLargeAndTheRivalInWords()
	{
		Ghost ahead = GhostFixtures.mining(1_400);
		assertEquals("+4,000 xp", GhostRow.lead(ahead));
		assertEquals("ahead of your best Mining session", GhostRow.words(ahead));
		assertNull("a skill's race has no kills", GhostRow.killLine(ahead));
		Ghost behind = GhostFixtures.mining(800);
		assertEquals("-2,000 xp", GhostRow.lead(behind));
		assertEquals("behind your best Mining session", GhostRow.words(behind));
		Ghost level = GhostFixtures.mining(1_000);
		assertEquals("Level", GhostRow.lead(level));
		assertEquals("with your best Mining session", GhostRow.words(level));
	}

	@Test
	public void theRowAndThePlaqueDraw()
	{
		Ghost g = GhostFixtures.mining(1_400);
		BufferedImage img = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D gfx = img.createGraphics();
		GhostRow.paint(gfx, g, 0, 0, 200, GhostRow.HEIGHT);
		com.codex.sessionchronicle.overlay.GhostPlaque plaque = new com.codex.sessionchronicle.overlay.GhostPlaque();
		assertNull("off, it draws nothing", plaque.render(gfx));
		plaque.setEnabled(true);
		plaque.show(g);
		assertEquals(g, plaque.shown());
		plaque.render(gfx);
		gfx.dispose();
	}
}
