package com.codex.sessionchronicle.overlay;

import com.codex.sessionchronicle.core.LapClock;
import com.codex.sessionchronicle.ui.AgilityView;
import com.codex.sessionchronicle.ui.Theme;
import com.codex.sessionchronicle.ui.Ui;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The optional lap plaque in the game view: the rooftop lap under way, its clock, and how far ahead of
 * or behind the best lap on the course it is at the last obstacle. It draws what the plugin works out on
 * the client thread each tick, so rendering never touches game state. Obstacles are not highlighted
 * here: RuneLite's own Agility plugin already does that.
 */
public class LapPlaque extends Overlay
{
	private static final int PAD = 7;

	private volatile LapClock.Split split;
	private volatile boolean enabled;

	public LapPlaque()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/** The lap to show, or null for none. */
	public void show(LapClock.Split split)
	{
		this.split = split;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/** The lap shown now, for tests. */
	public LapClock.Split shown()
	{
		return enabled ? split : null;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		LapClock.Split s = split;
		if (!enabled || s == null)
		{
			return null;
		}
		// Drawn on a copy, so the renderer's clip, font, hints and paint are never changed.
		Graphics2D copy = (Graphics2D) g.create();
		try
		{
			return draw(copy, s);
		}
		finally
		{
			copy.dispose();
		}
	}

	/** Draws the plaque at the origin; the screenshot test uses the same drawing. */
	public static Dimension draw(Graphics2D g, LapClock.Split s)
	{
		Theme.smooth(g);
		FontMetrics bold = g.getFontMetrics(Theme.title());
		FontMetrics small = g.getFontMetrics(Theme.small());
		String clock = LapClock.time(s.getElapsed());
		Integer d = s.delta();
		String gap = d == null ? "" : LapClock.gap(d);
		String under = s.getCourse().getShortName() + (s.hasGhost() ? ", best " + LapClock.time(s.getBestTicks()) : ", first lap");
		int top = bold.stringWidth(clock) + (gap.isEmpty() ? 0 : 10 + bold.stringWidth(gap));
		int width = PAD + Math.max(Math.max(top, small.stringWidth(under)), 96) + PAD;
		int height = PAD + bold.getAscent() + small.getHeight() + PAD - 2;
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 230), 0, height, Theme.alpha(Theme.INK_DEEP, 230)));
		g.fill(shape);
		g.setColor(Theme.alpha(Theme.GOLD, 170));
		g.draw(shape);
		g.setFont(Theme.title());
		int base = PAD + bold.getAscent() - 2;
		Ui.shadowed(g, clock, PAD, base, Theme.TEXT);
		if (!gap.isEmpty())
		{
			Ui.shadowed(g, gap, width - PAD - bold.stringWidth(gap), base, AgilityView.gapColorOf(d));
		}
		g.setFont(Theme.small());
		Ui.shadowed(g, under, PAD, base + small.getHeight(), Theme.TEXT_MUTED);
		return new Dimension(width, height);
	}
}
