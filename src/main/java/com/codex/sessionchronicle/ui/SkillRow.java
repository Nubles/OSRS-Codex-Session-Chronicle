package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Session;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import javax.swing.JComponent;

/** One skill's session: its icon and name, the levels it rose through, the experience, and its share of the whole. */
class SkillRow extends JComponent
{
	private final Session.SkillLine line;
	private final BufferedImage icon;
	private final double share;
	private final long perHour;

	SkillRow(Session.SkillLine line, BufferedImage icon, double share, long perHour)
	{
		this.line = line;
		this.icon = icon;
		this.share = share;
		this.perHour = perHour;
		setOpaque(false);
		setPreferredSize(new Dimension(10, 30));
		setToolTipText(line.getSkill() + ": " + String.format(Locale.ENGLISH, "%,d", line.gained()) + " xp" + (perHour > 0 ? ", " + Format.rate(perHour) : ""));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		paint(g, line, icon, share, 0, 0, getWidth());
		g.dispose();
	}

	/** Draws the row at a place and width; the card uses the same drawing. */
	static void paint(Graphics2D g, Session.SkillLine line, BufferedImage icon, double share, int x, int y, int w)
	{
		int iconBox = 20;
		if (icon != null)
		{
			double scale = Math.min(1, iconBox / (double) Math.max(icon.getWidth(), icon.getHeight()));
			int iw = (int) Math.round(icon.getWidth() * scale);
			int ih = (int) Math.round(icon.getHeight() * scale);
			g.drawImage(icon, x + (iconBox - iw) / 2, y + 2 + (iconBox - ih) / 2, iw, ih, null);
		}
		int tx = x + iconBox + 6;
		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		int base = y + fm.getAscent() + 1;
		String gained = Format.xp(line.gained());
		int gw = fm.stringWidth(gained);
		Ui.shadowed(g, gained, x + w - gw, base, Theme.XP);
		String levels = line.getLevel() > line.getStartLevel() ? line.getStartLevel() + " > " + line.getLevel() : String.valueOf(line.getLevel());
		int nameRoom = w - (tx - x) - gw - 8;
		String name = Ui.clip(line.getSkill(), fm, nameRoom);
		Ui.shadowed(g, name, tx, base, Theme.TEXT);
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int lx = tx + fm.stringWidth(name) + 6;
		if (lx + small.stringWidth(levels) < x + w - gw - 6)
		{
			Ui.shadowed(g, levels, lx, base, line.getLevel() > line.getStartLevel() ? Theme.GREEN : Theme.TEXT_FAINT);
		}

		int by = y + 22;
		int bw = w - (tx - x);
		RoundRectangle2D track = new RoundRectangle2D.Float(tx, by, bw, 4, 4, 4);
		g.setColor(Theme.INK_DEEP);
		g.fill(track);
		float fill = (float) Math.max(4, bw * Math.max(0, Math.min(1, share)));
		g.setPaint(new GradientPaint(tx, 0, Theme.mix(Theme.TEAL, Theme.INK, 0.35), tx + bw, 0, Theme.TEAL));
		g.fill(new RoundRectangle2D.Float(tx, by, fill, 4, 4, 4));
	}
}
