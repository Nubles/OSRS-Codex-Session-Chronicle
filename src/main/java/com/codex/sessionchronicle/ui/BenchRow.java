package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Bench;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * The make-X batch under way on the Session tab: what is being made, how many more the pack holds and
 * how long that takes at the player's own pace, the load as a thin bar, and how long the pack and bank
 * together last, bank trips included. Every time is the player's own, learnt from their own actions.
 */
public class BenchRow extends JComponent
{
	static final int HEIGHT = 112;
	private static final int ICON = 32;
	private final Bench.Progress progress;
	private final ItemIcons items;

	BenchRow(Bench.Progress progress, ItemIcons items)
	{
		this.progress = progress;
		this.items = items;
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		setToolTipText("<html>" + Ui.escape(paceLine(progress)) + "<br>Learnt from your own actions: the ticks between them, "
			+ "and the time from a load's last action to the next load's first.</html>");
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		BufferedImage icon = items == null ? null : items.icon(progress.getProduct(), this::repaint);
		paint(g, progress, icon, 0, 0, getWidth(), HEIGHT);
		g.dispose();
	}

	static void paint(Graphics2D g, Bench.Progress p, BufferedImage icon, int x, int y, int w, int h)
	{
		Color tint = p.isRunning() ? Theme.GOLD : Theme.GOLD_DIM;
		RoundRectangle2D plate = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, y, Theme.CARD_TOP, 0, y + h, Theme.CARD));
		g.fill(plate);
		g.setColor(Theme.alpha(tint, 110));
		g.draw(plate);
		g.setColor(tint);
		g.fill(new RoundRectangle2D.Float(x + 1, y + 6, 2, h - 12, 2, 2));

		int px = x + 10;
		int pw = w - 20;
		// The item in a little sunken well, as the game draws an item slot.
		RoundRectangle2D well = new RoundRectangle2D.Float(px, y + 8, ICON + 4, ICON + 4, 6, 6);
		g.setColor(Theme.INK_DEEP);
		g.fill(well);
		g.setColor(Theme.STROKE_SOFT);
		g.draw(well);
		if (icon != null)
		{
			g.drawImage(icon, px + 2, y + 10, ICON, ICON, null);
		}

		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		String count = Format.count(p.getLeft());
		String unit = "left";
		int cw = Math.max(bold.stringWidth(count), small.stringWidth(unit));
		int tx = px + ICON + 10;
		int tw = pw - ICON - 10 - cw - 8;
		int base = y + 10 + bold.getAscent();

		// A long name steps down to the plain font before it is cut short.
		boolean fits = bold.stringWidth(p.getName()) <= tw;
		g.setFont(fits ? Theme.title() : Theme.body());
		Ui.shadowed(g, Ui.clip(p.getName(), g.getFontMetrics(), tw), tx, base, Theme.GOLD_BRIGHT);
		g.setFont(Theme.title());
		Ui.shadowed(g, count, px + pw - bold.stringWidth(count), base, p.getLeft() > 0 ? Theme.TEXT : Theme.TEXT_MUTED);
		g.setFont(Theme.small());
		int sub = base + small.getHeight() + 1;
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(unit, px + pw - small.stringWidth(unit), sub);
		g.setColor(statusColor(p));
		g.drawString(Ui.clip(status(p), small, tw), tx, sub);

		int by = y + 8 + ICON + 4 + 8;
		bar(g, p, px, by, pw, 6);

		int line = by + 6 + 6 + small.getAscent();
		List<String> supply = Ui.lines(supplyLine(p), small, pw, 2);
		g.setColor(Theme.TEXT);
		for (String s : supply)
		{
			g.drawString(s, px, line);
			line += small.getHeight();
		}
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(Ui.clip(paceLine(p), small, pw), px, y + h - 7);
	}

	/** The load as a bar: done in gold, with a notch for each action while there are few enough to count. */
	public static void bar(Graphics2D g, Bench.Progress p, int x, int y, int w, int h)
	{
		RoundRectangle2D track = new RoundRectangle2D.Float(x, y, w, h, h, h);
		g.setColor(Theme.INK_DEEP);
		g.fill(track);
		int fill = (int) Math.round(w * p.share());
		if (fill > 0)
		{
			Color c = p.isRunning() ? Theme.progressColor(p.share()) : Theme.GOLD_DIM;
			g.setPaint(new GradientPaint(x, y, Theme.mix(c, Theme.GOLD_BRIGHT, 0.35), x, y + h, c));
			g.fill(new RoundRectangle2D.Float(x, y, Math.max(h, fill), h, h, h));
		}
		int all = p.getDone() + p.getLeft();
		if (all > 1 && all <= 28)
		{
			g.setColor(Theme.alpha(Theme.INK_DEEP, 150));
			for (int i = 1; i < all; i++)
			{
				int nx = x + (int) Math.round(w * i / (double) all);
				g.fillRect(nx, y + 1, 1, h - 2);
			}
		}
		g.setColor(Theme.STROKE_SOFT);
		g.draw(track);
	}

	/** "About 0:41 at your pace", "Learning your pace", "Load done: bank for the next". */
	public static String status(Bench.Progress p)
	{
		if (!p.isRunning())
		{
			return p.getLeft() <= 0 ? "Load done" : "Paused";
		}
		if (p.getLoadMs() < 0)
		{
			return "Learning your pace";
		}
		if (p.getLeft() == 1)
		{
			return "Last one, " + Format.lap(p.getLoadMs());
		}
		return "About " + Format.lap(p.getLoadMs()) + " to go";
	}

	static Color statusColor(Bench.Progress p)
	{
		if (!p.isRunning())
		{
			return Theme.TEXT_MUTED;
		}
		return p.getLoadMs() < 0 ? Theme.TEXT_MUTED : p.getLeft() == 1 ? Theme.EMBER : Theme.TEAL;
	}

	/** "Pack and bank: 1,204 more, about 1h 52m with 43 bank trips." */
	public static String supplyLine(Bench.Progress p)
	{
		if (p.getTotal() < 0)
		{
			return "Open your bank once and the rest of your supplies are counted too.";
		}
		if (p.getTotal() <= p.getLeft())
		{
			return p.getTotal() <= 0 ? "Nothing left in the bank to make more." : "None in the bank: this load is the last.";
		}
		StringBuilder b = new StringBuilder("Pack and bank: ").append(Format.count(p.getTotal())).append(" more");
		if (p.getTotalMs() >= 0)
		{
			b.append(", about ").append(Format.duration(p.getTotalMs()));
			if (p.getReloads() > 0)
			{
				b.append(p.isReloadKnown() ? " with " : " plus ").append(Format.count(p.getReloads()))
					.append(p.getReloads() == 1 ? " bank trip" : " bank trips");
			}
		}
		return b.append('.').toString();
	}

	/** "2.4s each, from 340 of your actions." */
	public static String paceLine(Bench.Progress p)
	{
		if (p.getPaceTicks() < 0)
		{
			return "Timing your pace: " + p.getTimed() + " of " + com.codex.sessionchronicle.core.BenchLog.MIN_SAMPLES + " actions.";
		}
		String each = String.format(Locale.ENGLISH, p.secondsEach() >= 10 ? "%.0fs" : "%.1fs", p.secondsEach());
		return each + " each, from " + Format.count(p.getActions()) + " of your actions.";
	}

	/** The section's trailing note: the skill, when one gives experience for it. */
	public static String trailing(Bench.Progress p)
	{
		return p.getSkill() == null ? "your pace" : p.getSkill();
	}
}
