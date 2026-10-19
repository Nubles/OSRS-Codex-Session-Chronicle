package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Session;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.Locale;
import javax.swing.JComponent;

/** One monster, raid or chest: how many this session and the game's kill count after them. */
class KillRow extends JComponent
{
	private final Session.KillLine line;
	private final double share;

	KillRow(Session.KillLine line, double share)
	{
		this.line = line;
		this.share = share;
		setOpaque(false);
		setPreferredSize(new Dimension(10, 18));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		paint(g, line, share, 0, 0, getWidth());
		g.dispose();
	}

	static void paint(Graphics2D g, Session.KillLine line, double share, int x, int y, int w)
	{
		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		int base = y + fm.getAscent();
		Path2D mark = new Path2D.Float();
		float my = base - fm.getAscent() / 2f + 1;
		mark.moveTo(x + 3, my - 3);
		mark.lineTo(x + 6, my);
		mark.lineTo(x + 3, my + 3);
		mark.lineTo(x, my);
		mark.closePath();
		g.setColor(Theme.mix(Theme.GOLD_DIM, Theme.KILLS, Math.max(0.2, share)));
		g.fill(mark);

		String count = "x" + line.count();
		String kc = line.getKillCount() > 0 ? "KC " + String.format(Locale.ENGLISH, "%,d", line.getKillCount()) : "";
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int kcw = kc.isEmpty() ? 0 : small.stringWidth(kc) + 8;
		g.setFont(Theme.body());
		int cw = fm.stringWidth(count);
		Ui.shadowed(g, count, x + w - kcw - cw, base, Theme.KILLS);
		Ui.shadowed(g, Ui.clip(line.getName(), fm, w - kcw - cw - 18), x + 12, base, Theme.TEXT);
		if (!kc.isEmpty())
		{
			g.setFont(Theme.small());
			Ui.shadowed(g, kc, x + w - kcw + 8, base, Theme.TEXT_FAINT);
		}
	}
}
