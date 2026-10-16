package com.codex.sessionchronicle.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

/**
 * Recent sessions as a row of bars, oldest on the left. The tallest is gilded, hovering names
 * a bar and clicking opens that session.
 */
class BarChart extends JComponent
{
	private static final int LABEL_H = 14;

	/** One bar. */
	static final class Bar
	{
		final String label;
		final String tip;
		final long value;

		Bar(String label, String tip, long value)
		{
			this.label = label;
			this.tip = tip;
			this.value = value;
		}
	}

	private final List<Bar> bars;
	private final Color color;
	private int hover = -1;

	BarChart(List<Bar> bars, Color color, IntConsumer onClick)
	{
		this.bars = bars;
		this.color = color;
		setOpaque(false);
		setPreferredSize(new Dimension(10, 92));
		setToolTipText("");
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int index = indexAt(e.getX());
				if (index != hover)
				{
					hover = index;
					repaint();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = -1;
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				int index = indexAt(e.getX());
				if (index >= 0 && onClick != null)
				{
					onClick.accept(index);
				}
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	private int indexAt(int x)
	{
		if (bars.isEmpty())
		{
			return -1;
		}
		int index = x * bars.size() / Math.max(1, getWidth());
		return index >= 0 && index < bars.size() ? index : -1;
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		int index = indexAt(e.getX());
		return index < 0 ? null : bars.get(index).tip;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight() - LABEL_H;
		long max = 1;
		int best = -1;
		for (int i = 0; i < bars.size(); i++)
		{
			if (bars.get(i).value > max)
			{
				max = bars.get(i).value;
				best = i;
			}
		}

		// Faint guide lines at the half and the top.
		g.setColor(Theme.alpha(Theme.STROKE_SOFT, 160));
		g.drawLine(0, 4, w, 4);
		g.drawLine(0, 4 + (h - 4) / 2, w, 4 + (h - 4) / 2);
		g.setColor(Theme.STROKE);
		g.drawLine(0, h, w, h);

		float slot = w / (float) Math.max(1, bars.size());
		float bw = Math.max(3, Math.min(16, slot - 4));
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		for (int i = 0; i < bars.size(); i++)
		{
			Bar bar = bars.get(i);
			float x = i * slot + (slot - bw) / 2;
			float bh = bar.value <= 0 ? 0 : Math.max(3, (h - 6) * bar.value / (float) max);
			Color c = i == best ? Theme.GOLD_BRIGHT : color;
			if (i == hover)
			{
				g.setColor(Theme.alpha(Theme.GOLD, 26));
				g.fillRect(Math.round(i * slot), 0, Math.round(slot), h);
			}
			if (bh > 0)
			{
				RoundRectangle2D shape = new RoundRectangle2D.Float(x, h - bh, bw, bh + 3, 4, 4);
				g.setClip(0, 0, w, h);
				g.setPaint(new GradientPaint(0, h - bh, c, 0, h, Theme.mix(c, Theme.INK, 0.55)));
				g.fill(shape);
				g.setColor(Theme.alpha(java.awt.Color.WHITE, 50));
				g.drawLine(Math.round(x + 1), Math.round(h - bh + 1), Math.round(x + bw - 2), Math.round(h - bh + 1));
				g.setClip(null);
			}
			if (bars.size() <= 14 || i % 2 == bars.size() % 2)
			{
				String label = bar.label;
				g.setColor(i == hover ? Theme.TEXT : Theme.TEXT_FAINT);
				g.drawString(label, Math.round(i * slot + (slot - fm.stringWidth(label)) / 2), getHeight() - 2);
			}
		}
		g.dispose();
	}
}
