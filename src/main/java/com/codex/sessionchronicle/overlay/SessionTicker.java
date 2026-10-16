package com.codex.sessionchronicle.overlay;

import com.codex.sessionchronicle.ui.Format;
import com.codex.sessionchronicle.ui.Sigil;
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
 * A slim gilt plaque in the game view with the session's clock, experience and loot. It draws
 * a view the plugin prepares, and runs the clock on from it, so rendering never touches game state.
 */
public class SessionTicker extends Overlay
{
	private static final int PAD = 7;

	/** What the ticker shows. */
	public static final class View
	{
		final long playedMs;
		final long at;
		final boolean live;
		final long xp;
		final long loot;

		public View(long playedMs, long at, boolean live, long xp, long loot)
		{
			this.playedMs = playedMs;
			this.at = at;
			this.live = live;
			this.xp = xp;
			this.loot = loot;
		}
	}

	private volatile View view;
	private volatile boolean enabled;

	public SessionTicker()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	public void show(View view)
	{
		this.view = view;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		View v = view;
		if (!enabled || v == null)
		{
			return null;
		}
		Theme.smooth(g);
		long played = v.playedMs + (v.live ? Math.max(0, Math.min(5_000, System.currentTimeMillis() - v.at)) : 0);
		String clock = Format.clock(played);
		String xp = Format.xp(v.xp) + " xp";
		String loot = Format.coins(v.loot) + " gp";

		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int icon = 14;
		int gap = 10;
		int width = PAD + icon + 6 + bold.stringWidth(clock) + gap + small.stringWidth(xp) + gap + small.stringWidth(loot) + PAD;
		int height = Math.max(icon, bold.getHeight()) + PAD;

		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 230), 0, height, Theme.alpha(Theme.INK_DEEP, 230)));
		g.fill(shape);
		g.setColor(Theme.alpha(Theme.GOLD, 170));
		g.draw(shape);

		int x = PAD;
		Sigil.paint(g, x, (height - icon) / 2f, icon);
		x += icon + 6;
		int base = (height + bold.getAscent()) / 2 - 2;
		g.setFont(Theme.title());
		Ui.shadowed(g, clock, x, base, v.live ? Theme.TEXT : Theme.TEXT_MUTED);
		x += bold.stringWidth(clock) + gap;
		g.setFont(Theme.small());
		Ui.shadowed(g, xp, x, base, Theme.XP);
		x += small.stringWidth(xp) + gap;
		Ui.shadowed(g, loot, x, base, Theme.LOOT);
		return new Dimension(width, height);
	}
}
