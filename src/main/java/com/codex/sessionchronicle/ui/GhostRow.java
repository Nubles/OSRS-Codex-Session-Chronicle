package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Ghost;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JComponent;

/**
 * Ghost pace in the panel: how far ahead of, or behind, the player's best earlier session of the
 * same activity the session is at this moment of play, and the gap minute by minute as a small
 * line about the ghost's own pace (the level line), gold-green above it and ember below.
 */
public class GhostRow extends JComponent
{
	static final int HEIGHT = 104;
	private final Ghost ghost;

	GhostRow(Ghost ghost)
	{
		this.ghost = ghost;
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		setToolTipText(tooltip(ghost));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		paint(g, ghost, 0, 0, getWidth(), HEIGHT);
		g.dispose();
	}

	static void paint(Graphics2D g, Ghost gh, int x, int y, int w, int h)
	{
		Color tint = color(gh);
		RoundRectangle2D plate = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, y, Theme.CARD_TOP, 0, y + h, Theme.CARD));
		g.fill(plate);
		g.setColor(Theme.alpha(tint, 110));
		g.draw(plate);
		g.setColor(tint);
		g.fill(new RoundRectangle2D.Float(x + 1, y + 6, 2, h - 12, 2, 2));

		int px = x + 10;
		int pw = w - 20;
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		int base = y + 6 + bold.getAscent();
		String lead = lead(gh);
		Ui.shadowed(g, lead, px, base, tint);
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		String at = gh.isPastGhost() ? "past " + Ghost.clock(gh.getGhostPlayedMs()) : "at " + Ghost.clock(gh.getElapsed());
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(at, px + pw - small.stringWidth(at), base);
		int line = base + small.getHeight() + 1;
		g.setColor(Theme.TEXT);
		g.drawString(Ui.clip(words(gh), small, pw), px, line);

		int sy = line + 6;
		int sh = h - (sy - y) - small.getHeight() - 8;
		spark(g, gh.getTrail(), gh.getGhostPlayedMs(), px, sy, pw, sh);

		int foot = y + h - 6;
		String kills = killLine(gh);
		int kw = kills == null ? 0 : small.stringWidth(kills) + 6;
		// The ghost's day and pace, shorter when the kills share the line.
		String day = Format.day(gh.getGhostStartedAt());
		String best = "Best " + day + ", " + Format.rate(gh.getGhostRate()) + (gh.isEstimated() ? ", at its average" : "");
		if (small.stringWidth(best) > pw - kw)
		{
			best = "Best " + day.substring(day.indexOf(' ') + 1) + ", " + Format.rate(gh.getGhostRate());
		}
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(Ui.clip(best, small, pw - kw), px, foot);
		if (kills != null)
		{
			g.setColor(gh.getKillLead() > 0 ? Theme.GREEN : gh.getKillLead() < 0 ? Theme.EMBER : Theme.TEXT_MUTED);
			g.drawString(kills, px + pw - small.stringWidth(kills), foot);
		}
	}

	/** "+12k xp", "-3,400 xp" or "Level": the gap, large. */
	public static String lead(Ghost gh)
	{
		long lead = gh.getLead();
		if (!gh.isPastGhost() && gh.standing() == 0)
		{
			return "Level";
		}
		return (lead >= 0 ? "+" : "-") + Format.amount(Math.abs(lead)) + " xp";
	}

	/** What the gap is against, in words, under it. */
	static String words(Ghost gh)
	{
		String whose = "your best " + gh.getActivity() + " session";
		if (gh.isPastGhost())
		{
			return (gh.getLead() >= 0 ? "on all of " : "short of all of ") + whose;
		}
		switch (gh.standing())
		{
			case 1:
				return "ahead of " + whose;
			case -1:
				return "behind " + whose;
			default:
				return "with " + whose;
		}
	}

	/** "2 kills ahead", "1 kill behind", "kills level"; null when the race is not a monster's. */
	static String killLine(Ghost gh)
	{
		if (!gh.hasKills())
		{
			return null;
		}
		long k = gh.getKillLead();
		if (k == 0)
		{
			return "kills level";
		}
		long n = Math.abs(k);
		return n + (n == 1 ? " kill " : " kills ") + (k > 0 ? "ahead" : "behind");
	}

	public static Color color(Ghost gh)
	{
		if (gh.isPastGhost())
		{
			return gh.getLead() >= 0 ? Theme.GOLD_BRIGHT : Theme.EMBER;
		}
		int s = gh.standing();
		return s > 0 ? Theme.GREEN : s < 0 ? Theme.EMBER : Theme.GOLD;
	}

	/**
	 * The gap minute by minute: a dashed level line for the ghost, the session's lead as a line about
	 * it, filled green above and ember below, and the moment now as a dot. Where the ghost's own
	 * session ended a faint mark stands, when it falls in the span drawn.
	 */
	public static void spark(Graphics2D g, List<long[]> trail, long ghostEnd, int x, int y, int w, int h)
	{
		if (h < 8 || trail.size() < 2)
		{
			return;
		}
		long span = Math.max(1, trail.get(trail.size() - 1)[0]);
		// The span runs from the furthest behind to the furthest ahead, the ghost's level line always in
		// it with a little room beyond, so a race only ever ahead uses the height rather than half of it.
		double lo = 0;
		double hi = 0;
		for (long[] p : trail)
		{
			lo = Math.min(lo, p[1]);
			hi = Math.max(hi, p[1]);
		}
		if (hi - lo < 1)
		{
			hi = 1;
			lo = -1;
		}
		double room = (hi - lo) * 0.18;
		hi += room;
		lo -= room;
		final double top = hi;
		final double range = hi - lo;
		java.util.function.DoubleUnaryOperator ys = v -> y + 2 + (h - 4) * (top - v) / range;
		float mid = (float) ys.applyAsDouble(0);
		g.setColor(Theme.alpha(Theme.INK_DEEP, 200));
		g.fill(new RoundRectangle2D.Float(x, y, w, h, 6, 6));
		Path2D line = new Path2D.Float();
		Path2D area = new Path2D.Float();
		float lastX = x;
		float lastY = mid;
		for (int i = 0; i < trail.size(); i++)
		{
			float px = x + 2 + (w - 4) * trail.get(i)[0] / (float) span;
			float py = (float) ys.applyAsDouble(trail.get(i)[1]);
			if (i == 0)
			{
				line.moveTo(px, py);
				area.moveTo(px, mid);
			}
			else
			{
				line.lineTo(px, py);
			}
			area.lineTo(px, py);
			lastX = px;
			lastY = py;
		}
		area.lineTo(lastX, mid);
		area.closePath();
		Shape clip = g.getClip();
		g.clip(new Rectangle2D.Float(x, y, w, mid - y));
		g.setPaint(new GradientPaint(0, y, Theme.alpha(Theme.GREEN, 150), 0, mid, Theme.alpha(Theme.GREEN, 20)));
		g.fill(area);
		g.setClip(clip);
		g.clip(new Rectangle2D.Float(x, mid, w, y + h - mid));
		g.setPaint(new GradientPaint(0, mid, Theme.alpha(Theme.EMBER, 20), 0, y + h, Theme.alpha(Theme.EMBER, 150)));
		g.fill(area);
		g.setClip(clip);

		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 3f}, 0f));
		g.setColor(Theme.alpha(Theme.TEXT_MUTED, 150));
		g.drawLine(x + 2, Math.round(mid), x + w - 2, Math.round(mid));
		if (ghostEnd > 0 && ghostEnd < span)
		{
			int ex = Math.round(x + 2 + (w - 4) * ghostEnd / (float) span);
			g.setColor(Theme.alpha(Theme.GOLD, 130));
			g.drawLine(ex, y + 2, ex, y + h - 2);
		}
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.GOLD_BRIGHT);
		g.draw(line);
		g.setStroke(new BasicStroke(1f));
		Color now = lastY < mid - 0.5f ? Theme.GREEN : lastY > mid + 0.5f ? Theme.EMBER : Theme.GOLD;
		g.setColor(Theme.alpha(now, 70));
		g.fill(new Ellipse2D.Float(lastX - 5, lastY - 5, 10, 10));
		g.setColor(now);
		g.fill(new Ellipse2D.Float(lastX - 2.5f, lastY - 2.5f, 5, 5));
	}

	private static String tooltip(Ghost gh)
	{
		return "<html>" + Ui.escape(gh.headline()) + "<br>The ghost is your " + Ui.escape(gh.getActivity()) + " session of "
			+ Format.day(gh.getGhostStartedAt()) + " (" + Format.amount(gh.getGhostTotalXp()) + " xp in " + Ghost.clock(gh.getGhostPlayedMs())
			+ "), the fastest of " + gh.getRivals() + (gh.getRivals() == 1 ? " earlier session" : " earlier sessions") + " of 15 minutes or more.<br>"
			+ (gh.isEstimated() ? "It was kept before sessions were tracked minute by minute, so it is raced at its average pace."
			: "It is raced minute by minute of play, as it went.") + "</html>";
	}
}
