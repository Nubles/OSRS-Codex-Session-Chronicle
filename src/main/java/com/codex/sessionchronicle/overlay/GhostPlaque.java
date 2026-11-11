package com.codex.sessionchronicle.overlay;

import com.codex.sessionchronicle.core.Ghost;
import com.codex.sessionchronicle.ui.GhostRow;
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
 * The optional ghost plaque in the game view: the gap to the player's best earlier session of the
 * same activity at this moment of play, and the gap minute by minute as a small line. It draws a
 * race the plugin works out on the client thread, so rendering never touches game state.
 */
public class GhostPlaque extends Overlay
{
	private static final int PAD = 7;
	private static final int SPARK_W = 64;
	private static final int SPARK_H = 18;

	private volatile Ghost ghost;
	private volatile boolean enabled;

	public GhostPlaque()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/** The race to show, or null for none. */
	public void show(Ghost ghost)
	{
		this.ghost = ghost;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/** The race shown now, for tests. */
	public Ghost shown()
	{
		return enabled ? ghost : null;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Ghost gh = ghost;
		if (!enabled || gh == null)
		{
			return null;
		}
		// Drawn on a copy, so the renderer's clip, font, hints and paint are never changed.
		Graphics2D copy = (Graphics2D) g.create();
		try
		{
			return draw(copy, gh);
		}
		finally
		{
			copy.dispose();
		}
	}

	private static Dimension draw(Graphics2D g, Ghost gh)
	{
		Theme.smooth(g);
		FontMetrics bold = g.getFontMetrics(Theme.title());
		FontMetrics small = g.getFontMetrics(Theme.small());
		String lead = GhostRow.lead(gh);
		String vs = "vs best " + gh.getActivity() + (gh.isPastGhost() ? ", past its end" : " at " + Ghost.clock(gh.getElapsed()));
		int textW = Math.max(bold.stringWidth(lead), small.stringWidth(vs));
		int width = PAD + textW + 8 + SPARK_W + PAD;
		int height = PAD + bold.getAscent() + small.getHeight() + PAD - 2;
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 230), 0, height, Theme.alpha(Theme.INK_DEEP, 230)));
		g.fill(shape);
		g.setColor(Theme.alpha(Theme.GOLD, 170));
		g.draw(shape);
		g.setFont(Theme.title());
		int base = PAD + bold.getAscent() - 2;
		Ui.shadowed(g, lead, PAD, base, GhostRow.color(gh));
		g.setFont(Theme.small());
		Ui.shadowed(g, vs, PAD, base + small.getHeight(), Theme.TEXT_MUTED);
		GhostRow.spark(g, gh.getTrail(), gh.getGhostPlayedMs(), PAD + textW + 8, (height - SPARK_H) / 2, SPARK_W, SPARK_H);
		return new Dimension(width, height);
	}
}
