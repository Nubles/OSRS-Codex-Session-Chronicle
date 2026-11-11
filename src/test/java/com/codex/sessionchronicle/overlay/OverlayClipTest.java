package com.codex.sessionchronicle.overlay;

import com.codex.sessionchronicle.core.Bench;
import com.codex.sessionchronicle.core.BenchLog;
import com.codex.sessionchronicle.core.GhostFixtures;
import com.codex.sessionchronicle.core.LapClock;
import com.codex.sessionchronicle.core.Rooftop;
import com.codex.sessionchronicle.core.Underwater;
import com.codex.sessionchronicle.core.UnderwaterCoach;
import com.codex.sessionchronicle.core.UnderwaterLog;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 * RuneLite's overlay renderer hands every overlay the same graphics, with its own clip set. An overlay
 * that clears the clip (or leaves a font, stroke, hint, paint, transform or composite of its own behind)
 * breaks every overlay drawn after it, every frame. Each plaque here is drawn into graphics set up as the
 * renderer sets them, and must leave every one of them as it found them.
 */
public class OverlayClipTest
{
	private static final Rectangle CLIP = new Rectangle(3, 4, 280, 90);

	private static void leavesTheGraphicsAsTheyWere(String name, Function<Graphics2D, Dimension> render)
	{
		BufferedImage image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.translate(7, 9);
		g.setClip(CLIP);
		Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
		g.setFont(font);
		Stroke stroke = new BasicStroke(2.5f);
		g.setStroke(stroke);
		Paint paint = Color.MAGENTA;
		g.setPaint(paint);
		Composite composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
		g.setComposite(composite);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		AffineTransform transform = g.getTransform();
		Rectangle clip = g.getClipBounds();

		assertNotNull(name + " drew nothing", render.apply(g));

		assertNotNull(name + " cleared the clip", g.getClip());
		assertEquals(name + " moved the clip", clip, g.getClipBounds());
		assertEquals(name + " changed the transform", transform, g.getTransform());
		assertEquals(name + " changed the font", font, g.getFont());
		assertEquals(name + " changed the stroke", stroke, g.getStroke());
		assertEquals(name + " changed the paint", paint, g.getPaint());
		assertEquals(name + " changed the composite", composite, g.getComposite());
		assertEquals(name + " changed antialiasing", RenderingHints.VALUE_ANTIALIAS_OFF, g.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
		assertEquals(name + " changed text antialiasing", RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
			g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING));
		g.dispose();
	}

	@Test
	public void theTicker()
	{
		SessionTicker t = new SessionTicker();
		t.setEnabled(true);
		t.show(new SessionTicker.View(65_000, System.currentTimeMillis(), true, 12_000, 450_000, 40));
		leavesTheGraphicsAsTheyWere("the ticker", t::render);
	}

	@Test
	public void theGhostPlaque()
	{
		GhostPlaque p = new GhostPlaque();
		p.setEnabled(true);
		p.show(GhostFixtures.mining(1_200));
		leavesTheGraphicsAsTheyWere("the ghost plaque", p::render);
	}

	@Test
	public void theLapPlaque()
	{
		LapPlaque p = new LapPlaque();
		p.setEnabled(true);
		p.show(new LapClock.Split(Rooftop.SEERS, 40, new int[]{9, 21}, 70, new int[]{10, 22, 35, 50, 62}));
		leavesTheGraphicsAsTheyWere("the lap plaque", p::render);
	}

	@Test
	public void theBatchPlaque()
	{
		Bench bench = new Bench();
		BenchLog log = new BenchLog();
		Map<Integer, String> names = new HashMap<>();
		names.put(3, "Maple longbow (u)");
		bench.tick(0, pack(1, 27), false, null, 0, log, id -> names.getOrDefault(id, "Item " + id));
		bench.tick(3, pack(1, 26, 3, 1), false, "Fletching", 1_800, log, id -> names.getOrDefault(id, "Item " + id));
		bench.tick(6, pack(1, 25, 3, 2), false, "Fletching", 3_600, log, id -> names.getOrDefault(id, "Item " + id));
		Bench.Progress progress = bench.progress(6, log);
		assertNotNull(progress);
		BenchPlaque p = new BenchPlaque();
		p.setEnabled(true);
		p.show(progress);
		leavesTheGraphicsAsTheyWere("the batch plaque", p::render);
	}

	@Test
	public void theDivePlaque()
	{
		DivePlaque p = new DivePlaque();
		p.setEnabled(true);
		p.show(UnderwaterCoach.of(new UnderwaterLog(), new UnderwaterLog.Dive(1L), true, 10, 0, 70, 70, Underwater.Reward.MERFOLK_TRIDENT));
		leavesTheGraphicsAsTheyWere("the dive plaque", p::render);
	}

	private static Map<Integer, Integer> pack(int... idCount)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < idCount.length; i += 2)
		{
			m.put(idCount[i], idCount[i + 1]);
		}
		return m;
	}
}
