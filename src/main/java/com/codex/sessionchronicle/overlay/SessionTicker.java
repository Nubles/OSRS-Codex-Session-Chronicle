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
	private static final int ICON = 14;
	private static final java.awt.Color EDGE = Theme.alpha(Theme.GOLD, 170);
	/** The sigil drawn once, so a frame only copies it. */
	private static final java.awt.image.BufferedImage SIGIL = Sigil.icon(ICON);

	/** What the ticker shows. */
	public static final class View
	{
		final long playedMs;
		final long at;
		final boolean live;
		final long xp;
		final long loot;
		/** The goal's share in percent, or -1 without a goal. */
		final int goal;

		public View(long playedMs, long at, boolean live, long xp, long loot)
		{
			this(playedMs, at, live, xp, loot, -1);
		}

		public View(long playedMs, long at, boolean live, long xp, long loot, int goal)
		{
			this.goal = goal;
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

	/** What was last laid out, so a frame that shows the same second draws without making anything new. */
	private View laidView;
	private long laidSecond = -1;
	private String clock;
	private String xp;
	private String loot;
	private String goal;
	private int clockW;
	private int xpW;
	private int lootW;
	private int base;
	private Dimension size;
	private RoundRectangle2D shape;
	private GradientPaint fill;

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
		if (v != laidView || played / 1000 != laidSecond)
		{
			lay(g, v, played);
		}
		int height = size.height;
		g.setPaint(fill);
		g.fill(shape);
		g.setColor(EDGE);
		g.draw(shape);

		int gap = 10;
		int x = PAD;
		g.drawImage(SIGIL, x, (height - ICON) / 2, null);
		x += ICON + 6;
		g.setFont(Theme.title());
		int base = this.base;
		Ui.shadowed(g, clock, x, base, v.live ? Theme.TEXT : Theme.TEXT_MUTED);
		x += clockW + gap;
		g.setFont(Theme.small());
		Ui.shadowed(g, xp, x, base, Theme.XP);
		x += xpW + gap;
		Ui.shadowed(g, loot, x, base, Theme.LOOT);
		if (!goal.isEmpty())
		{
			x += lootW + gap;
			Ui.shadowed(g, goal, x, base, v.goal >= 100 ? Theme.GREEN : Theme.GOLD_BRIGHT);
		}
		return size;
	}

	/** Measures the plaque for a view at a second of play; runs about once a second, not every frame. */
	private void lay(Graphics2D g, View v, long played)
	{
		laidView = v;
		laidSecond = played / 1000;
		clock = Format.clock(played);
		xp = Format.xp(v.xp) + " xp";
		loot = Format.coins(v.loot) + " gp";
		goal = v.goal < 0 ? "" : v.goal >= 100 ? "goal met" : "goal " + v.goal + "%";
		FontMetrics bold = g.getFontMetrics(Theme.title());
		FontMetrics small = g.getFontMetrics(Theme.small());
		clockW = bold.stringWidth(clock);
		xpW = small.stringWidth(xp);
		lootW = small.stringWidth(loot);
		int gap = 10;
		int width = PAD + ICON + 6 + clockW + gap + xpW + gap + lootW + PAD + (goal.isEmpty() ? 0 : gap + small.stringWidth(goal));
		int height = Math.max(ICON, bold.getHeight()) + PAD;
		base = (height + bold.getAscent()) / 2 - 2;
		if (size == null || size.width != width || size.height != height)
		{
			size = new Dimension(width, height);
			shape = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, 10, 10);
			fill = new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 230), 0, height, Theme.alpha(Theme.INK_DEEP, 230));
		}
	}
}
