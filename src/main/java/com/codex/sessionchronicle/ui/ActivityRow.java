package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Journal;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * One activity from the journal: its latest pace, a line of every session's pace with the best
 * marked in gilt, and how much faster (or slower) the player has got at it.
 */
class ActivityRow extends JComponent
{
	static final int HEIGHT = 50;
	private final Journal.Best best;

	ActivityRow(Journal.Best best)
	{
		this.best = best;
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		setToolTipText(tooltip(best));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int x = 0;
		int inner = w;
		Color tint = PaceRow.color(best.getKind());

		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		int base = fm.getAscent();
		String rate = PaceRow.rate(best.getLatest(), best.getKind(), best.getName());
		int rw = fm.stringWidth(rate);
		Ui.shadowed(g, rate, x + inner - rw, base, tint);
		Ui.shadowed(g, Ui.clip(best.getName(), fm, inner - rw - 8), x, base, Theme.TEXT);

		// Every session's pace, oldest to newest, scaled from nothing to a little past the best.
		List<long[]> points = best.getHistory();
		int top = base + 5;
		int height = 16;
		// Scaled between the slowest and fastest sessions, with a margin, so a change of pace shows.
		long max = 1;
		long min = Long.MAX_VALUE;
		for (long[] p : points)
		{
			max = Math.max(max, p[1]);
			min = Math.min(min, p[1]);
		}
		double spread = Math.max(max - min, max * 0.1);
		double low = Math.max(0, min - spread * 0.3);
		double scale = height / (max + spread * 0.15 - low);
		g.setColor(Theme.alpha(Theme.STROKE, 120));
		g.drawLine(x, top + height, x + inner, top + height);
		if (points.size() == 1)
		{
			float py = (float) (top + height - (points.get(0)[1] - low) * scale);
			dot(g, x + inner / 2f, py, Theme.GOLD);
		}
		else
		{
			Path2D line = new Path2D.Float();
			// Inset by a dot's width so the first and last sessions are drawn whole.
			float step = (inner - 8) / (float) (points.size() - 1);
			int bestIndex = 0;
			for (int i = 0; i < points.size(); i++)
			{
				float px = x + 4 + i * step;
				float py = (float) (top + height - (points.get(i)[1] - low) * scale);
				if (i == 0)
				{
					line.moveTo(px, py);
				}
				else
				{
					line.lineTo(px, py);
				}
				if (points.get(i)[1] > points.get(bestIndex)[1])
				{
					bestIndex = i;
				}
			}
			g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(Theme.alpha(tint, 200));
			g.draw(line);
			g.setStroke(new BasicStroke(1f));
			for (int i = 0; i < points.size(); i++)
			{
				float px = x + 4 + i * step;
				float py = (float) (top + height - (points.get(i)[1] - low) * scale);
				dot(g, px, py, i == bestIndex ? Theme.GOLD : i == points.size() - 1 ? tint : Theme.mix(tint, Theme.INK, 0.35));
			}
		}

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int sy = top + height + 4 + small.getAscent();
		String trend = trend(best);
		int tw = small.stringWidth(trend);
		g.setColor(trendColor(best));
		g.drawString(trend, x + inner - tw, sy);
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(Ui.clip(summary(best), small, inner - tw - 8), x, sy);
		g.dispose();
	}

	private static void dot(Graphics2D g, float x, float y, Color color)
	{
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(x - 3, y - 3, 6, 6));
		g.setColor(color);
		g.fill(new Ellipse2D.Float(x - 2, y - 2, 4, 4));
	}

	/** "6 sessions, best 21": how much there is to go by. */
	static String summary(Journal.Best b)
	{
		String sessions = b.getSessions() + (b.getSessions() == 1 ? " session" : " sessions");
		return sessions + ", best " + Format.amount(b.getRate());
	}

	/** "+14%" faster over the sessions shown, or "new" with too few to tell. */
	static String trend(Journal.Best b)
	{
		double t = b.trend();
		if (Double.isNaN(t))
		{
			return b.getSessions() < 2 ? "new" : "";
		}
		if (Math.abs(t) < 0.03)
		{
			return "steady";
		}
		return (t > 0 ? "+" : "-") + Math.round(Math.abs(t) * 100) + "%";
	}

	static Color trendColor(Journal.Best b)
	{
		double t = b.trend();
		if (Double.isNaN(t) || Math.abs(t) < 0.03)
		{
			return Theme.TEXT_MUTED;
		}
		return t > 0 ? Theme.GREEN : Theme.EMBER;
	}

	static String tooltip(Journal.Best b)
	{
		StringBuilder tip = new StringBuilder("<html><b>").append(Ui.escape(b.getName())).append("</b><br>");
		tip.append(b.getSessions()).append(b.getSessions() == 1 ? " session" : " sessions").append(" of 15 minutes or more, ")
			.append(Format.duration(b.getPlayedMs())).append(" in all.<br>");
		tip.append("Best ").append(PaceRow.rate(b.getRate(), b.getKind(), b.getName())).append(" on ").append(Format.day(b.getAt()))
			.append(", latest ").append(PaceRow.rate(b.getLatest(), b.getKind(), b.getName())).append('.');
		double t = b.trend();
		if (!Double.isNaN(t))
		{
			tip.append("<br>The last three sessions shown against the first three: ")
				.append(String.format(Locale.ENGLISH, "%+.0f%%", t * 100)).append('.');
		}
		Long worth = b.worthPerHour();
		if (worth != null)
		{
			tip.append("<br>Net worth by Codex Ledger: ").append(Format.signedCoins(worth)).append(" an hour, supplies and all.");
		}
		return tip.append("</html>").toString();
	}
}
