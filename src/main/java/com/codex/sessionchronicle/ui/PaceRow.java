package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Pace;
import com.codex.sessionchronicle.core.Summary;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * One rate beside the player's own history: the rate, a bar with a tick at their usual and a
 * gilt diamond at their best, and a few words on how it compares.
 */
class PaceRow extends JComponent
{
	static final int HEIGHT = 44;
	private final Pace.Line line;

	PaceRow(Pace.Line line)
	{
		this.line = line;
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		setToolTipText(tooltip(line));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		paint(g, line, 0, 0, getWidth(), "your");
		g.dispose();
	}

	/**
	 * Draws the row at a place and width; the card uses the same drawing.
	 *
	 * @param whose "your" in the panel, "my" on the card the player shares
	 */
	static void paint(Graphics2D g, Pace.Line line, int x, int y, int w, String whose)
	{
		Color tint = color(line.getKind());
		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		int base = y + fm.getAscent();
		String rate = rate(line.getRate(), line.getKind(), line.getName());
		int rw = fm.stringWidth(rate);
		Ui.shadowed(g, rate, x + w - rw, base, tint);
		Ui.shadowed(g, Ui.clip(title(line), fm, w - rw - 8), x, base, Theme.TEXT);

		// The bar runs to a little past the larger of this rate and the best, so both always fit.
		int by = y + 19;
		long top = Math.max(1, Math.round(Math.max(line.getRate(), line.getBest()) * 1.12));
		RoundRectangle2D track = new RoundRectangle2D.Float(x, by, w, 5, 5, 5);
		g.setColor(Theme.INK_DEEP);
		g.fill(track);
		float fill = (float) Math.max(5, w * Math.min(1.0, line.getRate() / (double) top));
		g.setPaint(new GradientPaint(x, 0, Theme.mix(tint, Theme.INK, 0.45), x + fill, 0, tint));
		g.fill(new RoundRectangle2D.Float(x, by, fill, 5, 5, 5));
		if (line.getSamples() > 0)
		{
			float ux = x + (float) (w * line.getUsual() / (double) top);
			g.setColor(Theme.TEXT_MUTED);
			g.fillRect(Math.round(ux), by - 2, 1, 9);
			float bx = x + (float) (w * line.getBest() / (double) top);
			diamond(g, bx, by + 2.5f, 3.5f, line.isRecord() ? Theme.GOLD_DIM : Theme.GOLD);
		}

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int sy = y + 30 + small.getAscent();
		String best = line.getSamples() > 0 ? "best " + Format.amount(line.getBest()) : "";
		int bw = small.stringWidth(best);
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(best, x + w - bw, sy);
		String verdict = verdict(line, whose);
		g.setColor(verdictColor(line));
		g.drawString(Ui.clip(verdict, small, w - bw - 8), x, sy);
	}

	static String title(Pace.Line line)
	{
		return line.getKind() == Pace.Kind.LOOT ? "Loot" : line.getName();
	}

	/** "212k xp/hr", "31 kills/hr", "3 clears/hr", "1.2M gp/hr". */
	static String rate(long rate, Pace.Kind kind, String name)
	{
		switch (kind)
		{
			case SKILL:
				return Format.amount(rate) + " xp/hr";
			case KILLS:
				return Format.amount(rate) + " " + Summary.noun(name) + "/hr";
			default:
				return Format.amount(rate) + " gp/hr";
		}
	}

	/** How it compares, in a few words. */
	static String verdict(Pace.Line line, String whose)
	{
		if (line.getSamples() == 0)
		{
			return "First session of its kind";
		}
		if (line.isRecord())
		{
			return (line.getKind() == Pace.Kind.LOOT ? "Richer than " : "Faster than ") + whose + " best";
		}
		// One earlier session is no habit yet: it is compared as the last time, not as the usual.
		String usual = whose + (line.getSamples() == 1 ? " last" : " usual");
		double vs = line.vsUsual();
		if (Math.abs(vs) < 0.03)
		{
			return "About " + usual;
		}
		return Math.round(Math.abs(vs) * 100) + "% " + (vs > 0 ? "above " : "below ") + usual;
	}

	static Color verdictColor(Pace.Line line)
	{
		if (line.getSamples() == 0)
		{
			return Theme.TEXT_FAINT;
		}
		if (line.isRecord())
		{
			return Theme.GOLD_BRIGHT;
		}
		return line.vsUsual() >= 0.03 ? Theme.GREEN : line.vsUsual() <= -0.03 ? Theme.EMBER : Theme.TEXT_MUTED;
	}

	static Color color(Pace.Kind kind)
	{
		return kind == Pace.Kind.SKILL ? Theme.XP : kind == Pace.Kind.KILLS ? Theme.KILLS : Theme.LOOT;
	}

	private static String tooltip(Pace.Line line)
	{
		String what = line.getKind() == Pace.Kind.SKILL ? "sessions spent mostly on " + line.getName()
			: line.getKind() == Pace.Kind.KILLS ? "trips to " + line.getName()
			: "earlier sessions of the same kind";
		if (line.getSamples() == 0)
		{
			return "No earlier " + what + " of 15 minutes or more to compare with yet";
		}
		return "<html>" + rate(line.getRate(), line.getKind(), line.getName()) + " now<br>" + (line.getSamples() == 1 ? "Last " : "Usual ")
			+ rate(line.getUsual(), line.getKind(), line.getName()) + ", best " + rate(line.getBest(), line.getKind(), line.getName()) + " (" + Format.day(line.getBestAt()) + ")<br>From "
			+ line.getSamples() + " " + Ui.escape(what) + "</html>";
	}

	private static void diamond(Graphics2D g, float cx, float cy, float r, Color color)
	{
		Path2D d = new Path2D.Float();
		d.moveTo(cx, cy - r);
		d.lineTo(cx + r, cy);
		d.lineTo(cx, cy + r);
		d.lineTo(cx - r, cy);
		d.closePath();
		g.setColor(color);
		g.fill(d);
		g.setColor(Theme.INK_DEEP);
		g.draw(d);
	}
}
