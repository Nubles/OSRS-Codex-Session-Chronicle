package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Moment;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * One stop on the session's timeline: the time, a node on a gilt thread coloured by the kind
 * of moment, and what happened. Consecutive rows join their threads into one line.
 */
class MomentRow extends JComponent
{
	static final int HEIGHT = 34;
	private static final int TIME_W = 34;

	private final Moment moment;
	private final BufferedImage icon;
	private final boolean first;
	private final boolean last;

	MomentRow(Moment moment, BufferedImage icon, boolean first, boolean last)
	{
		this.moment = moment;
		this.icon = icon;
		this.first = first;
		this.last = last;
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		String detail = moment.getDetail();
		setToolTipText(detail == null ? moment.getTitle() : moment.getTitle() + ": " + detail);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		Color color = Theme.moment(moment.getKind());
		int nx = TIME_W + 6;
		int ny = 10;

		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 140));
		if (!first)
		{
			g.drawLine(nx, 0, nx, ny);
		}
		if (!last)
		{
			g.drawLine(nx, ny, nx, h);
		}
		node(g, moment.getKind(), nx, ny, color);

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		Ui.shadowed(g, Format.time(moment.getAt()), 0, ny + small.getAscent() / 2 - 1, Theme.TEXT_FAINT);

		int tx = nx + 10;
		int right = w;
		if (icon != null)
		{
			g.drawImage(icon, w - icon.getWidth(), Math.max(0, (h - icon.getHeight()) / 2 - 2), null);
			right = w - icon.getWidth() - 4;
		}
		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		Ui.shadowed(g, Ui.clip(moment.getTitle(), fm, right - tx), tx, ny + fm.getAscent() / 2 - 1, Theme.mix(Theme.TEXT, color, 0.25));
		if (moment.getDetail() != null)
		{
			g.setFont(Theme.small());
			Ui.shadowed(g, Ui.clip(moment.getDetail(), small, right - tx), tx, ny + fm.getAscent() / 2 + small.getHeight(), Theme.TEXT_MUTED);
		}
		g.dispose();
	}

	/** The node: a diamond for most moments, a ringed star for a pet or a 99. Shared with the card. */
	static void node(Graphics2D g, Moment.Kind kind, float x, float y, Color color)
	{
		boolean grand = kind == Moment.Kind.PET || kind == Moment.Kind.MAX;
		if (grand)
		{
			g.setColor(Theme.alpha(color, 60));
			g.fill(new Ellipse2D.Float(x - 7, y - 7, 14, 14));
			g.setColor(Theme.alpha(color, 160));
			g.draw(new Ellipse2D.Float(x - 6, y - 6, 12, 12));
		}
		float r = grand ? 4.5f : 4f;
		Path2D diamond = new Path2D.Float();
		diamond.moveTo(x, y - r);
		diamond.lineTo(x + r, y);
		diamond.lineTo(x, y + r);
		diamond.lineTo(x - r, y);
		diamond.closePath();
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(x - r - 1.5f, y - r - 1.5f, r * 2 + 3, r * 2 + 3));
		g.setColor(color);
		g.fill(diamond);
	}
}
