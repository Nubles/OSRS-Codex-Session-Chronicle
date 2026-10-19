package com.codex.sessionchronicle.overlay;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The ticker lays itself out once a second at most; a frame in between makes nothing new. */
public class SessionTickerTest
{
	@Test
	public void framesWithinASecondReuseTheLayout()
	{
		SessionTicker t = new SessionTicker();
		t.setEnabled(true);
		BufferedImage image = new BufferedImage(400, 40, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		// Paused, so the clock does not run on between the two frames.
		t.show(new SessionTicker.View(65_000, System.currentTimeMillis(), false, 12_000, 450_000));
		Dimension a = t.render(g);
		Dimension b = t.render(g);
		assertSame(a, b);
		t.show(new SessionTicker.View(65_000, System.currentTimeMillis(), false, 12_000_000, 4_500_000_000L, 40));
		Dimension c = t.render(g);
		assertNotSame(a, c);
		assertTrue(c.width > a.width);
		g.dispose();
	}

	@Test
	public void drawsNothingWhenOff()
	{
		SessionTicker t = new SessionTicker();
		t.show(new SessionTicker.View(65_000, 0, false, 1, 1));
		BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		assertNull(t.render(image.createGraphics()));
	}
}
