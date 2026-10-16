package com.codex.sessionchronicle.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import javax.swing.JComponent;

/**
 * The session clock in the crest: the ring fills through each hour played, a gold pip marks
 * every hour already done, and the middle reads the time played and whether it is still live.
 */
public class SessionClock extends JComponent
{
	private final int diameter;
	private final Animator animator = new Animator(this);
	private String text = "-";
	private String caption = "";
	private Color captionColor = Theme.TEXT_MUTED;
	private int hours;

	public SessionClock(int diameter)
	{
		this.diameter = diameter;
		Dimension size = new Dimension(diameter, diameter);
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		setOpaque(false);
	}

	public void set(long playedMs, String caption, Color captionColor, boolean animate)
	{
		long minutes = playedMs / 60_000;
		hours = (int) (minutes / 60);
		text = playedMs <= 0 && caption.isEmpty() ? "-" : Format.clock(playedMs);
		this.caption = caption;
		this.captionColor = captionColor;
		animator.set((minutes % 60) / 60.0, animate);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		float thickness = 5;
		float inset = thickness / 2f + 3;
		float size = diameter - inset * 2;
		float c = diameter / 2f;

		g.setPaint(new RadialGradientPaint(c, c, size / 2f, new float[]{0f, 1f},
			new Color[]{Theme.alpha(Theme.TEAL, 34), Theme.alpha(Theme.INK_DEEP, 0)}));
		g.fill(new Ellipse2D.Float(inset, inset, size, size));

		// Minute ticks around the outside, longer on the quarters.
		g.setStroke(new BasicStroke(1f));
		for (int i = 0; i < 60; i += 5)
		{
			double a = Math.toRadians(i * 6 - 90);
			float outer = diameter / 2f - 0.5f;
			float inner = outer - (i % 15 == 0 ? 3.5f : 2f);
			g.setColor(Theme.alpha(Theme.GOLD_DIM, i % 15 == 0 ? 200 : 120));
			g.draw(new Line2D.Double(c + Math.cos(a) * inner, c + Math.sin(a) * inner, c + Math.cos(a) * outer, c + Math.sin(a) * outer));
		}

		g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.alpha(Theme.STROKE, 200));
		g.draw(new Arc2D.Float(inset, inset, size, size, 0, 360, Arc2D.OPEN));
		double value = animator.get();
		if (value > 0)
		{
			g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			int segments = Math.max(1, (int) Math.ceil(value * 72));
			double sweep = value * 360 / segments;
			for (int i = 0; i < segments; i++)
			{
				double t = (i + 1) / (double) segments * value;
				g.setColor(Theme.mix(Theme.TEAL, Theme.GOLD_BRIGHT, t));
				g.draw(new Arc2D.Double(inset, inset, size, size, 90 - i * sweep, -sweep - (i < segments - 1 ? 0.6 : 0), Arc2D.OPEN));
			}
		}

		// One pip for each whole hour, in an arc across the top of the well.
		int pips = Math.min(hours, 12);
		float pr = size / 2f - thickness - 3;
		for (int i = 0; i < pips; i++)
		{
			double a = Math.toRadians(-90 + (i - (pips - 1) / 2.0) * 13);
			float px = (float) (c + Math.cos(a) * pr);
			float py = (float) (c + Math.sin(a) * pr);
			g.setColor(Theme.alpha(Theme.GOLD, 70));
			g.fill(new Ellipse2D.Float(px - 2.5f, py - 2.5f, 5, 5));
			g.setColor(Theme.GOLD_BRIGHT);
			g.fill(new Ellipse2D.Float(px - 1.5f, py - 1.5f, 3, 3));
		}

		g.setFont(Theme.title());
		FontMetrics fm = g.getFontMetrics();
		int textY = diameter / 2 + fm.getAscent() / 2 - 4;
		Ui.shadowed(g, text, (diameter - fm.stringWidth(text)) / 2, textY, Theme.TEXT);
		if (!caption.isEmpty())
		{
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			Ui.shadowed(g, caption, (diameter - small.stringWidth(caption)) / 2, textY + small.getAscent() + 1, captionColor);
		}
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 90));
		g.draw(new Ellipse2D.Float(0.5f, 0.5f, diameter - 1, diameter - 1));
		g.dispose();
	}
}
