package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Session;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import javax.swing.JComponent;

/**
 * One monster, raid or chest: how many this session and the game's kill count after them. A boss
 * wears its hiscores icon, as a skill wears its own; anything else keeps the gold diamond.
 */
class KillRow extends JComponent
{
	/** Width kept for the icon or diamond, so names line up whichever a row has. */
	static final int MARK = 16;

	private final Session.KillLine line;
	private final double share;
	private final Icons icons;
	/** One callback for the row's lifetime, so a waiting icon holds it once however often the row paints. */
	private final Runnable repaint = this::repaint;

	KillRow(Session.KillLine line, double share, Icons icons)
	{
		this.line = line;
		this.share = share;
		this.icons = icons;
		setOpaque(false);
		setPreferredSize(new Dimension(10, 18));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		paint(g, line, share, icons == null ? null : icons.boss(line.getName(), repaint), 0, 0, getWidth());
		g.dispose();
	}

	/** True when every one of these names fits whole in a row of that width. */
	static boolean fits(Graphics2D g, java.util.List<Session.KillLine> lines, int w)
	{
		for (Session.KillLine line : lines)
		{
			if (!Ui.clip(line.getName(), g.getFontMetrics(Theme.body()), nameWidth(g, line, w)).equals(line.getName()))
			{
				return false;
			}
		}
		return true;
	}

	private static String countText(Session.KillLine line)
	{
		return "x" + Format.count(line.count());
	}

	private static String kcText(Session.KillLine line)
	{
		if (line.getKillCount() <= 0)
		{
			return "";
		}
		return (line.isRound() ? "Total " : "KC ") + Format.count(line.getKillCount());
	}

	private static int nameWidth(Graphics2D g, Session.KillLine line, int w)
	{
		String kc = kcText(line);
		int kcw = kc.isEmpty() ? 0 : g.getFontMetrics(Theme.small()).stringWidth(kc) + 8;
		return w - kcw - g.getFontMetrics(Theme.body()).stringWidth(countText(line)) - MARK - 6;
	}

	/** @param icon the boss's icon, or null for the diamond */
	static void paint(Graphics2D g, Session.KillLine line, double share, java.awt.image.BufferedImage icon, int x, int y, int w)
	{
		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		int base = y + fm.getAscent();
		float my = base - fm.getAscent() / 2f + 1;
		if (icon != null)
		{
			// Boss icons are 25 across; fitted to the mark's width beside the name.
			double scale = Math.min(1, Math.min(MARK / (double) icon.getWidth(), MARK / (double) icon.getHeight()));
			int iw = (int) Math.round(icon.getWidth() * scale);
			int ih = (int) Math.round(icon.getHeight() * scale);
			g.drawImage(icon, x + (MARK - iw) / 2, Math.round(my - ih / 2f), iw, ih, null);
		}
		else
		{
			float mx = x + MARK / 2f;
			Path2D mark = new Path2D.Float();
			mark.moveTo(mx, my - 3);
			mark.lineTo(mx + 3, my);
			mark.lineTo(mx, my + 3);
			mark.lineTo(mx - 3, my);
			mark.closePath();
			g.setColor(Theme.mix(Theme.GOLD_DIM, Theme.KILLS, Math.max(0.2, share)));
			g.fill(mark);
		}

		String count = countText(line);
		String kc = kcText(line);
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int kcw = kc.isEmpty() ? 0 : small.stringWidth(kc) + 8;
		g.setFont(Theme.body());
		int cw = fm.stringWidth(count);
		Ui.shadowed(g, count, x + w - kcw - cw, base, Theme.KILLS);
		Ui.shadowed(g, Ui.clip(line.getName(), fm, w - kcw - cw - MARK - 6), x + MARK + 4, base, Theme.TEXT);
		if (!kc.isEmpty())
		{
			g.setFont(Theme.small());
			Ui.shadowed(g, kc, x + w - kcw + 8, base, Theme.TEXT_FAINT);
		}
	}
}
