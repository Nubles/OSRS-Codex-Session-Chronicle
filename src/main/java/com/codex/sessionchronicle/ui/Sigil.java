package com.codex.sessionchronicle.ui;

import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Session Chronicle's mark: a gilt quill writing on an unrolled scroll, with a drop of teal ink. */
public final class Sigil
{
	private Sigil()
	{
	}

	public static BufferedImage icon(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		Theme.smooth(g);
		paint(g, 0, 0, size);
		g.dispose();
		return image;
	}

	/** Draws the mark in a size by size box. */
	public static void paint(Graphics2D g, float x, float y, float size)
	{
		float s = size / 16f;

		// The scroll: a sheet with a roll at each end.
		RoundRectangle2D sheet = new RoundRectangle2D.Float(x + 2.2f * s, y + 4.4f * s, 10.6f * s, 10 * s, 1.5f * s, 1.5f * s);
		g.setPaint(new GradientPaint(x, y + 4 * s, Theme.mix(Theme.GOLD_BRIGHT, Theme.TEXT, 0.4), x, y + 15 * s, Theme.GOLD_DIM));
		g.fill(sheet);
		g.setColor(Theme.INK_DEEP);
		g.setStroke(new BasicStroke(Math.max(0.7f, 0.6f * s)));
		g.draw(sheet);
		for (float ry : new float[]{4.4f, 14.4f})
		{
			RoundRectangle2D roll = new RoundRectangle2D.Float(x + 1.2f * s, y + (ry - 1.1f) * s, 12.6f * s, 2.2f * s, 2.2f * s, 2.2f * s);
			g.setPaint(new GradientPaint(x, y + (ry - 1.1f) * s, Theme.GOLD_BRIGHT, x, y + (ry + 1.1f) * s, Theme.GOLD_DIM));
			g.fill(roll);
			g.setColor(Theme.INK_DEEP);
			g.draw(roll);
		}

		// Lines of writing.
		g.setStroke(new BasicStroke(Math.max(0.8f, 0.7f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.alpha(Theme.INK_DEEP, 190));
		float[][] lines = {{4, 7.4f, 10.5f}, {4, 9.6f, 9}, {4, 11.8f, 7.2f}};
		for (float[] l : lines)
		{
			g.drawLine(Math.round(x + l[0] * s), Math.round(y + l[1] * s), Math.round(x + l[2] * s), Math.round(y + l[1] * s));
		}

		// The quill, leaning in from the top right.
		Path2D feather = new Path2D.Float();
		feather.moveTo(x + 8.6f * s, y + 11.8f * s);
		feather.curveTo(x + 10 * s, y + 7 * s, x + 12.5f * s, y + 2.6f * s, x + 15.6f * s, y + 0.4f * s);
		feather.curveTo(x + 15 * s, y + 4 * s, x + 12.6f * s, y + 8.2f * s, x + 8.6f * s, y + 11.8f * s);
		feather.closePath();
		g.setPaint(new GradientPaint(x + 9 * s, y + 12 * s, Theme.GOLD, x + 15 * s, y, Theme.TEXT));
		g.fill(feather);
		g.setColor(Theme.INK_DEEP);
		g.setStroke(new BasicStroke(Math.max(0.6f, 0.5f * s)));
		g.draw(feather);

		// A drop of ink at the nib.
		float r = 1.3f * s;
		g.setColor(Theme.alpha(Theme.TEAL, 100));
		g.fill(new Ellipse2D.Float(x + 8.2f * s - r * 1.6f, y + 12.4f * s - r * 1.6f, r * 3.2f, r * 3.2f));
		g.setColor(Theme.TEAL);
		g.fill(new Ellipse2D.Float(x + 8.2f * s - r, y + 12.4f * s - r, r * 2, r * 2));
	}
}
