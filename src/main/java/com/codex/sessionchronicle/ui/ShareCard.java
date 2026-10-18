package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Moment;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.Summary;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The shareable card: one session told on a gilt-framed page, drawn at twice the panel's scale
 * so the game's pixel fonts stay crisp when it is posted anywhere.
 */
public final class ShareCard
{
	static final int WIDTH = 360;
	static final int SCALE = 2;
	private static final int PAD = 20;
	private static final int SKILLS = 5;
	private static final int DROPS = 16;
	private static final int COLUMNS = 8;
	private static final int KILLS = 4;
	private static final int HIGHLIGHTS = 3;

	private ShareCard()
	{
	}

	/** Renders the card; icons still loading are left out and onLoaded runs when one arrives. */
	public static BufferedImage render(Summary s, boolean showName, ItemIcons items, Icons skills, Runnable onLoaded)
	{
		// A first pass only measures, so it draws into a single pixel.
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = scratch.createGraphics();
		int height = content(sg, s, showName, items, skills, onLoaded);
		sg.dispose();

		BufferedImage image = new BufferedImage(WIDTH * SCALE, height * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.scale(SCALE, SCALE);
		Theme.smooth(g);
		frame(g, height);
		content(g, s, showName, items, skills, onLoaded);
		g.dispose();
		return image;
	}

	private static void frame(Graphics2D g, int h)
	{
		int w = WIDTH;
		RoundRectangle2D page = new RoundRectangle2D.Float(0, 0, w, h, 18, 18);
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.INK, Theme.CARD_TOP, 0.5), 0, h, Theme.INK_DEEP));
		g.fill(page);
		g.setClip(page);
		g.setPaint(new RadialGradientPaint(w / 2f, 40, w * 0.8f, new float[]{0f, 1f},
			new Color[]{Theme.alpha(Theme.GOLD, 46), Theme.alpha(Theme.GOLD, 0)}));
		g.fillRect(0, 0, w, h);

		// A faint grain, the same every time, so the page reads as vellum rather than a flat fill.
		Random grain = new Random(7);
		for (int i = 0; i < w * h / 26; i++)
		{
			g.setColor(Theme.alpha(grain.nextBoolean() ? Theme.GOLD_BRIGHT : Color.BLACK, 6 + grain.nextInt(8)));
			g.fillRect(grain.nextInt(w), grain.nextInt(h), 1, 1);
		}
		g.setClip(null);

		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(Theme.GOLD, 190));
		g.draw(new RoundRectangle2D.Float(4.5f, 4.5f, w - 9, h - 9, 14, 14));
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 120));
		g.draw(new RoundRectangle2D.Float(8.5f, 8.5f, w - 17, h - 17, 10, 10));
		float[][] corners = {{14, 14}, {w - 14, 14}, {14, h - 14}, {w - 14, h - 14}};
		for (float[] c : corners)
		{
			diamond(g, c[0], c[1], 3.5f, Theme.GOLD);
		}
	}

	/** Lays out and draws everything inside the frame; returns the page height it needs. */
	private static int content(Graphics2D g, Summary s, boolean showName, ItemIcons items, Icons skills, Runnable onLoaded)
	{
		Theme.smooth(g);
		int x = PAD;
		int w = WIDTH - PAD * 2;
		int y = PAD + 4;

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		Sigil.paint(g, x, y - 1, 13);
		g.setColor(Theme.GOLD);
		g.drawString("SESSION CHRONICLE", x + 18, y + small.getAscent() - 1);
		String date = Format.date(s.getStartedAt());
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(date, x + w - small.stringWidth(date), y + small.getAscent() - 1);
		y += small.getHeight() + 8;

		if (showName && s.getPlayer() != null)
		{
			Font big = Theme.title().deriveFont(Theme.title().getSize2D() * 2);
			g.setFont(big);
			FontMetrics fm = g.getFontMetrics();
			Ui.shadowed(g, Ui.clip(s.getPlayer(), fm, w), x, y + fm.getAscent() - 4, Theme.GOLD_BRIGHT);
			y += fm.getAscent() - 2;
		}
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		Ui.shadowed(g, Ui.clip(s.headline(), bold, w), x, y + bold.getAscent(), showName ? Theme.TEXT : Theme.GOLD_BRIGHT);
		y += bold.getHeight();
		g.setFont(Theme.small());
		String when = Format.duration(s.getPlayedMs()) + " played, " + Format.time(s.getStartedAt()) + " to " + Format.time(s.getEndedAt());
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(when, x, y + small.getAscent());
		y += small.getHeight() + 8;
		rule(g, x, y, w);
		y += 10;

		// Experience and loot always; kills and levels when the session has them, levels if it has neither.
		List<String[]> tiles = new ArrayList<>();
		List<Color> colors = new ArrayList<>();
		tiles.add(new String[]{s.getTotalXp() > 0 ? Format.xp(s.getTotalXp()) : "0", "Experience", Format.rate(s.xpPerHour())});
		colors.add(Theme.XP);
		tiles.add(new String[]{Format.coins(s.getLootValue()), "Loot", Format.rate(s.lootPerHour())});
		colors.add(Theme.LOOT);
		if (s.getKills() > 0)
		{
			tiles.add(new String[]{String.valueOf(s.getKills()), "Kills", s.getKillLines().get(0).getName()});
			colors.add(Theme.KILLS);
		}
		if (s.getLevels() > 0 || s.getKills() == 0)
		{
			tiles.add(new String[]{"+" + s.getLevels(), "Levels", topLevel(s)});
			colors.add(Theme.GREEN);
		}
		int tileH = 54;
		int gap = 6;
		int left = x;
		for (int i = 0; i < tiles.size(); i++)
		{
			int tw = i == tiles.size() - 1 ? x + w - left : (w - gap * (tiles.size() - 1)) / tiles.size();
			String[] t = tiles.get(i);
			StatTile.paint(g, t[0], t[1], t[2], colors.get(i), left, y, tw, tileH);
			left += tw + gap;
		}
		y += tileH + 12;

		List<Session.SkillLine> skillLines = s.getSkills();
		if (!skillLines.isEmpty())
		{
			header(g, "Experience", skillLines.size() > SKILLS ? skillLines.size() + " skills" : null, x, y, w);
			y += 24;
			long top = skillLines.get(0).gained();
			for (int i = 0; i < skillLines.size() && i < SKILLS; i++)
			{
				Session.SkillLine line = skillLines.get(i);
				SkillRow.paint(g, line, skills == null ? null : skills.skill(line.getSkill()), line.gained() / (double) top, x, y, w);
				y += 30;
			}
			y += 4;
		}

		List<Session.Drop> drops = s.getDrops();
		if (!drops.isEmpty())
		{
			int more = drops.size() - DROPS + s.getOtherDrops();
			header(g, "Loot", more > 0 ? "+" + more + " more" : null, x, y, w);
			y += 24;
			int slotW = (w - (COLUMNS - 1) * 3) / COLUMNS;
			int shown = Math.min(DROPS, drops.size());
			for (int i = 0; i < shown; i++)
			{
				Session.Drop d = drops.get(i);
				BufferedImage icon = items == null ? null : items.icon(d.getId(), onLoaded);
				ItemGrid.paintSlot(g, x + (i % COLUMNS) * (slotW + 3), y + (i / COLUMNS) * (ItemGrid.SLOT_H + 3), slotW, ItemGrid.SLOT_H,
					icon, d.getQuantity(), d.value());
			}
			y += ((shown + COLUMNS - 1) / COLUMNS) * (ItemGrid.SLOT_H + 3) + 9;
		}

		List<Session.KillLine> kills = s.getKillLines();
		if (!kills.isEmpty())
		{
			header(g, "Kills", null, x, y, w);
			y += 24;
			int top = kills.get(0).count();
			int colW = (w - 16) / 2;
			int shown = Math.min(KILLS, kills.size());
			for (int i = 0; i < shown; i++)
			{
				KillRow.paint(g, kills.get(i), kills.get(i).count() / (double) top, x + (i % 2) * (colW + 16), y + (i / 2) * 20, colW);
			}
			y += ((shown + 1) / 2) * 20 + 6;
		}

		List<Moment> highlights = new ArrayList<>(s.getHighlights());
		if (!highlights.isEmpty())
		{
			header(g, "Highlights", null, x, y, w);
			y += 24;
			for (int i = 0; i < highlights.size() && i < HIGHLIGHTS; i++)
			{
				Moment m = highlights.get(i);
				Color color = Theme.moment(m.getKind());
				MomentRow.node(g, m.getKind(), x + 6, y + 7, color);
				int tx = x + 20;
				int right = x + w;
				if (m.getItemId() > 0 && items != null)
				{
					BufferedImage icon = items.icon(m.getItemId(), onLoaded);
					if (icon != null)
					{
						g.drawImage(icon, right - icon.getWidth(), y - 4, null);
						right -= icon.getWidth() + 6;
					}
				}
				g.setFont(Theme.body());
				FontMetrics fm = g.getFontMetrics();
				Ui.shadowed(g, Ui.clip(m.getTitle(), fm, right - tx), tx, y + fm.getAscent() - 2, Theme.mix(Theme.TEXT, color, 0.3));
				if (m.getDetail() != null)
				{
					g.setFont(Theme.small());
					g.setColor(Theme.TEXT_MUTED);
					g.drawString(Ui.clip(m.getDetail(), small, right - tx), tx, y + fm.getAscent() + small.getAscent());
				}
				y += 32;
			}
		}

		y += 2;
		rule(g, x, y, w);
		y += 8;
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_FAINT);
		g.drawString("Codex Session Chronicle for RuneLite", x, y + small.getAscent());
		String deaths = s.getDeaths() > 0 ? s.getDeaths() + (s.getDeaths() == 1 ? " death" : " deaths") : "No deaths";
		g.setColor(s.getDeaths() > 0 ? Theme.mix(Theme.TEXT_FAINT, Theme.BLOOD, 0.6) : Theme.TEXT_FAINT);
		g.drawString(deaths, x + w - small.stringWidth(deaths), y + small.getAscent());
		y += small.getHeight();
		return y + PAD;
	}

	/** "Ranged 99": the skill that rose the most, for the levels tile. */
	private static String topLevel(Summary s)
	{
		Session.SkillLine best = null;
		for (Session.SkillLine line : s.getSkills())
		{
			int up = line.getLevel() - line.getStartLevel();
			if (up > 0 && (best == null || up > best.getLevel() - best.getStartLevel()))
			{
				best = line;
			}
		}
		return best == null ? "" : best.getSkill() + " " + best.getLevel();
	}

	private static void header(Graphics2D g, String text, String trailing, int x, int y, int w)
	{
		g.setFont(Theme.small());
		SectionHeader.paint(g, text.toUpperCase(), trailing, x, y, w, 20);
	}

	private static void rule(Graphics2D g, int x, int y, int w)
	{
		g.setStroke(new BasicStroke(1f));
		g.setPaint(new GradientPaint(x, 0, Theme.alpha(Theme.GOLD, 0), x + w / 2f, 0, Theme.alpha(Theme.GOLD, 150), true));
		g.drawLine(x, y, x + w, y);
		diamond(g, x + w / 2f, y, 3, Theme.GOLD);
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
	}
}
