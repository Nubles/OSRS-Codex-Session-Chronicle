package com.codex.sessionchronicle.ui;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/** The panel's own drawing clips to its shapes and then puts back the clip it was given, never clearing it. */
public class ClipTest
{
	private static final Rectangle CLIP = new Rectangle(2, 3, 150, 60);

	private static Graphics2D clipped(BufferedImage image)
	{
		Graphics2D g = image.createGraphics();
		g.setClip(CLIP);
		return g;
	}

	@Test
	public void theCardsFrameKeepsTheClip()
	{
		BufferedImage image = new BufferedImage(ShareCard.WIDTH, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = clipped(image);
		ShareCard.frame(g, 200);
		assertNotNull(g.getClip());
		assertEquals(CLIP, g.getClipBounds());
		g.dispose();
	}

	@Test
	public void aCardAndAChartKeepTheClip()
	{
		Card card = new Card(new java.awt.BorderLayout());
		card.accent(Theme.GOLD);
		card.setSize(180, 50);
		BufferedImage image = new BufferedImage(200, 80, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = clipped(image);
		card.paintComponent(g);
		assertNotNull(g.getClip());
		assertEquals(CLIP, g.getClipBounds());

		BarChart chart = new BarChart(Arrays.asList(new BarChart.Bar("1", "one", 10), new BarChart.Bar("2", "two", 30)), Theme.XP, null);
		chart.setSize(180, 60);
		chart.paintComponent(g);
		assertNotNull(g.getClip());
		assertEquals(CLIP, g.getClipBounds());
		g.dispose();
	}
}
