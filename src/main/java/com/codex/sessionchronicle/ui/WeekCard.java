package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Moment;
import com.codex.sessionchronicle.core.Week;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * The week card: the last seven days on the same gilt page as a session card. The play of each
 * day as a row of columns with the best day in gold, the totals, what the week went on, its best
 * moments and, with Codex Ledger, the net worth's line through the week.
 */
public final class WeekCard
{
	private static final int WIDTH = ShareCard.WIDTH;
	private static final int SCALE = ShareCard.SCALE;
	private static final int PAD = 20;
	private static final int BARS_H = 64;
	private static final int WORTH_H = 50;
	private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_ONLY = DateTimeFormatter.ofPattern("d", Locale.ENGLISH);
	private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

	private WeekCard()
	{
	}

	/** Renders the card; icons still loading are left out and onLoaded runs when one arrives. */
	public static BufferedImage render(Week w, boolean showName, ItemIcons items, Icons skills, Runnable onLoaded)
	{
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = scratch.createGraphics();
		int height = content(sg, w, showName, items, skills, onLoaded);
		sg.dispose();

		BufferedImage image = new BufferedImage(WIDTH * SCALE, height * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.scale(SCALE, SCALE);
		Theme.smooth(g);
		ShareCard.frame(g, height);
		content(g, w, showName, items, skills, onLoaded);
		g.dispose();
		return image;
	}

	/** "13 to 19 October", or "28 September to 4 October" across a month's end. */
	public static String range(Week w)
	{
		LocalDate a = w.getFirst();
		LocalDate b = w.getLast();
		return (a.getMonth() == b.getMonth() ? DAY_ONLY.format(a) : DAY_MONTH.format(a)) + " to " + DAY_MONTH.format(b);
	}

	/** What the week was, in a line: "A week of Mining", or how many days it was played across. */
	public static String headline(Week w)
	{
		List<Week.Activity> spent = w.getActivities();
		if (!spent.isEmpty() && spent.get(0).getPlayedMs() * 2 > w.getPlayedMs())
		{
			return "A week of " + spent.get(0).getName();
		}
		int days = w.getDaysPlayed();
		return days == Week.DAYS ? "Played every day" : "Played " + days + (days == 1 ? " day" : " days") + " of seven";
	}

	private static int content(Graphics2D g, Week w, boolean showName, ItemIcons items, Icons skills, Runnable onLoaded)
	{
		Theme.smooth(g);
		int x = PAD;
		int cw = WIDTH - PAD * 2;
		int y = PAD + 4;

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		Sigil.paint(g, x, y - 1, 13);
		g.setColor(Theme.GOLD);
		g.drawString("SESSION CHRONICLE", x + 18, y + small.getAscent() - 1);
		String range = range(w);
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(range, x + cw - small.stringWidth(range), y + small.getAscent() - 1);
		y += small.getHeight() + 8;

		boolean named = showName && w.getPlayer() != null;
		String big = named ? w.getPlayer() + "'s week" : "My week";
		Font large = Theme.title().deriveFont(Theme.title().getSize2D() * 2f);
		if (g.getFontMetrics(large).stringWidth(big) > cw)
		{
			large = Theme.title().deriveFont(Theme.title().getSize2D() * 1.5f);
		}
		g.setFont(large);
		FontMetrics lm = g.getFontMetrics();
		Ui.shadowed(g, Ui.clip(big, lm, cw), x, y + lm.getAscent() - 4, Theme.GOLD_BRIGHT);
		y += lm.getAscent() - 2;
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		Ui.shadowed(g, Ui.clip(headline(w), bold, cw), x, y + bold.getAscent(), Theme.TEXT);
		y += bold.getHeight();
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_MUTED);
		String when = Format.duration(w.getPlayedMs()) + " played over " + w.getSessions() + (w.getSessions() == 1 ? " session" : " sessions");
		g.drawString(Ui.clip(when, small, cw), x, y + small.getAscent());
		y += small.getHeight() + 8;
		ShareCard.rule(g, x, y, cw);
		y += 10;

		// The fourth tile is the week's kills, else its levels, else how many sessions it held.
		boolean kills = w.getKills() > 0;
		boolean levels = !kills && w.getLevels() > 0;
		long hours = Math.max(1, w.getPlayedMs());
		String[][] tiles = {
			{Format.duration(w.getPlayedMs()).replace("under a minute", "0m"), "Played", w.getDaysPlayed() + (w.getDaysPlayed() == 1 ? " day" : " days")},
			{w.getXp() > 0 ? Format.xp(w.getXp()) : "0", "Experience", w.getPlayedMs() >= 60_000 ? Format.rate(Math.round(w.getXp() * 3_600_000.0 / hours)) : ""},
			{Format.coins(w.getLoot()), "Loot", w.getPlayedMs() >= 60_000 ? Format.rate(Math.round(w.getLoot() * 3_600_000.0 / hours)) : ""},
			kills ? new String[]{Format.count(w.getKills()), "Kills", w.getLevels() > 0 ? "+" + w.getLevels() + (w.getLevels() == 1 ? " level" : " levels") : ""}
				: levels ? new String[]{"+" + w.getLevels(), "Levels", ""}
				: new String[]{String.valueOf(w.getSessions()), "Sessions", ""}};
		Color[] colors = {Theme.EMBER, Theme.XP, Theme.LOOT, kills ? Theme.KILLS : levels ? Theme.GREEN : Theme.TEAL};
		int gap = 6;
		int tileH = 54;
		int left = x;
		for (int i = 0; i < tiles.length; i++)
		{
			int tw = i == tiles.length - 1 ? x + cw - left : (cw - gap * (tiles.length - 1)) / tiles.length;
			StatTile.paint(g, tiles[i][0], tiles[i][1], tiles[i][2], colors[i], left, y, tw, tileH);
			left += tw + gap;
		}
		y += tileH + 12;

		int best = w.getBestDay();
		String bestDay = best < 0 || w.getDaysPlayed() < 2 ? null : best == Week.DAYS - 1 ? "best today"
			: "best " + w.getDays().get(best).getDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		ShareCard.header(g, "The week", bestDay, x, y, cw);
		y += 24;
		y = days(g, w, best, x, y, cw) + 10;

		List<long[]> worth = w.getWorth();
		Long change = w.worthChange();
		if (change != null)
		{
			long from = w.worthFrom();
			LocalDate start = java.time.Instant.ofEpochMilli(from).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
			String trailing = Format.signedCoins(change) + (start.isAfter(w.getFirst()) ? " since " + SHORT.format(start) : " this week");
			ShareCard.header(g, "Net worth", trailing, x, y, cw);
			y += 24;
			y = worth(g, worth, change, x, y, cw) + 10;
		}

		List<Week.Activity> spent = w.getActivities();
		if (!spent.isEmpty())
		{
			ShareCard.header(g, "Mostly", null, x, y, cw);
			y += 24;
			for (Week.Activity a : spent)
			{
				activity(g, a, w.getPlayedMs(), skills == null ? null : skills.skill(a.getName()), x, y, cw);
				y += 28;
			}
			y += 4;
		}

		List<Moment> highlights = w.getHighlights();
		if (!highlights.isEmpty())
		{
			ShareCard.header(g, "Highlights", w.getRecords() > 0 ? w.getRecords() + (w.getRecords() == 1 ? " record" : " records") : null, x, y, cw);
			y += 24;
			for (Moment m : highlights)
			{
				Color color = Theme.moment(m.getKind());
				MomentRow.node(g, m.getKind(), x + 6, y + 7, color);
				int tx = x + 20;
				int right = x + cw;
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
				g.setFont(Theme.small());
				g.setColor(Theme.TEXT_MUTED);
				String detail = Format.day(m.getAt()) + (m.getDetail() == null ? "" : ". " + m.getDetail());
				g.drawString(Ui.clip(detail, small, right - tx), tx, y + fm.getAscent() + small.getAscent());
				y += 32;
			}
		}

		y += 2;
		ShareCard.rule(g, x, y, cw);
		y += 8;
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_FAINT);
		g.drawString("Codex Session Chronicle for RuneLite", x, y + small.getAscent());
		String deaths = w.getDeaths() > 0 ? w.getDeaths() + (w.getDeaths() == 1 ? " death" : " deaths") : "No deaths";
		g.setColor(w.getDeaths() > 0 ? Theme.mix(Theme.TEXT_FAINT, Theme.BLOOD, 0.6) : Theme.TEXT_FAINT);
		g.drawString(deaths, x + cw - small.stringWidth(deaths), y + small.getAscent());
		y += small.getHeight();
		return y + PAD;
	}

	/** Seven columns, one a day, as tall as the day was played; the best in gold, a day off a dim mark. */
	private static int days(Graphics2D g, Week w, int best, int x, int y, int cw)
	{
		List<Week.Day> days = w.getDays();
		long most = best < 0 ? 1 : Math.max(1, days.get(best).getPlayedMs());
		int gap = 6;
		int colW = (cw - gap * (Week.DAYS - 1)) / Week.DAYS;
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int labelH = small.getHeight();
		int base = y + labelH + BARS_H;
		for (int i = 0; i < days.size(); i++)
		{
			Week.Day d = days.get(i);
			int cx = x + i * (colW + gap);
			boolean top = i == best && w.getDaysPlayed() > 1;
			boolean today = i == days.size() - 1;
			if (d.getPlayedMs() > 0)
			{
				int h = (int) Math.max(4, Math.round(BARS_H * d.getPlayedMs() / (double) most));
				Color c = top ? Theme.GOLD_BRIGHT : Theme.EMBER;
				RoundRectangle2D bar = new RoundRectangle2D.Float(cx + 0.5f, base - h + 0.5f, colW - 1, h - 1, 5, 5);
				g.setPaint(new GradientPaint(0, base - h, c, 0, base, Theme.mix(c, Theme.INK_DEEP, 0.55)));
				g.fill(bar);
				g.setColor(Theme.alpha(top ? Theme.GOLD_BRIGHT : Theme.GOLD_DIM, top ? 200 : 110));
				g.draw(bar);
				String hours = hours(d.getPlayedMs());
				g.setColor(top ? Theme.GOLD_BRIGHT : Theme.TEXT_MUTED);
				g.drawString(hours, cx + (colW - small.stringWidth(hours)) / 2, base - h - 3);
			}
			else
			{
				g.setColor(Theme.STROKE);
				g.fillRect(cx + colW / 2 - 2, base - 2, 4, 2);
			}
			String label = today ? "Today" : d.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
			g.setColor(today ? Theme.GOLD : top ? Theme.GOLD_BRIGHT : Theme.TEXT_FAINT);
			g.drawString(label, cx + (colW - small.stringWidth(label)) / 2, base + 4 + small.getAscent());
		}
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 110));
		g.drawLine(x, base, x + cw, base);
		return base + 4 + labelH;
	}

	/** "2h", "2h 5m", "45m": a column's play time, short enough for its width. */
	static String hours(long ms)
	{
		long minutes = Math.max(1, ms / 60_000);
		if (minutes < 60)
		{
			return minutes + "m";
		}
		long h = minutes / 60;
		long m = minutes % 60;
		return h >= 10 || m == 0 ? h + "h" : h + "h " + m + "m";
	}

	/** The worth as a gilt line over the week, with where it began and ended. */
	private static int worth(Graphics2D g, List<long[]> points, long change, int x, int y, int cw)
	{
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (long[] p : points)
		{
			min = Math.min(min, p[1]);
			max = Math.max(max, p[1]);
		}
		long t0 = points.get(0)[0];
		long t1 = Math.max(t0 + 1, points.get(points.size() - 1)[0]);
		double span = Math.max(1, max - min);
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		String first = Format.coins(points.get(0)[1]);
		String last = Format.coins(points.get(points.size() - 1)[1]);
		int labelW = Math.max(small.stringWidth(first), small.stringWidth(last)) + 8;
		int lx = x + labelW;
		int lw = cw - labelW * 2;
		Path2D line = new Path2D.Float();
		Path2D area = new Path2D.Float();
		float lastX = lx;
		float lastY = y;
		for (int i = 0; i < points.size(); i++)
		{
			float px = (float) (lx + lw * (points.get(i)[0] - t0) / (double) (t1 - t0));
			float py = (float) (y + 4 + (WORTH_H - 8) * (1 - (points.get(i)[1] - min) / span));
			if (i == 0)
			{
				line.moveTo(px, py);
				area.moveTo(px, y + WORTH_H);
			}
			else
			{
				line.lineTo(px, py);
			}
			area.lineTo(px, py);
			lastX = px;
			lastY = py;
		}
		area.lineTo(lastX, y + WORTH_H);
		area.closePath();
		Color tone = change >= 0 ? Theme.GOLD : Theme.BLOOD;
		g.setPaint(new GradientPaint(0, y, Theme.alpha(tone, 90), 0, y + WORTH_H, Theme.alpha(tone, 0)));
		g.fill(area);
		g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(change >= 0 ? Theme.GOLD_BRIGHT : Theme.mix(Theme.BLOOD, Theme.TEXT, 0.3));
		g.draw(line);
		g.setStroke(new BasicStroke(1f));
		ShareCard.diamond(g, lastX, lastY, 3.5f, Theme.GOLD_BRIGHT);
		float firstY = (float) (y + 4 + (WORTH_H - 8) * (1 - (points.get(0)[1] - min) / span));
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(first, x, Math.round(firstY) + small.getAscent() / 2);
		g.setColor(change >= 0 ? Theme.GOLD_BRIGHT : Theme.mix(Theme.BLOOD, Theme.TEXT, 0.3));
		g.drawString(last, x + cw - small.stringWidth(last), Math.round(lastY) + small.getAscent() / 2);
		return y + WORTH_H;
	}

	/** One thing the week went on: its icon, name, play time and a bar for its share of the week. */
	private static void activity(Graphics2D g, Week.Activity a, long total, BufferedImage icon, int x, int y, int cw)
	{
		int tx = x + 24;
		if (icon != null)
		{
			g.drawImage(icon, x + (18 - icon.getWidth()) / 2, y + Math.max(0, (16 - icon.getHeight()) / 2), null);
		}
		else
		{
			ShareCard.diamond(g, x + 9, y + 8, 4, Theme.KILLS);
		}
		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		String time = Format.duration(a.getPlayedMs());
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int timeW = small.stringWidth(time);
		g.setFont(Theme.body());
		Ui.shadowed(g, Ui.clip(a.getName(), fm, cw - 24 - timeW - 8), tx, y + fm.getAscent() - 2, Theme.TEXT);
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(time, x + cw - timeW, y + fm.getAscent() - 2);
		double share = total <= 0 ? 0 : Math.min(1, a.getPlayedMs() / (double) total);
		int by = y + 18;
		g.setColor(Theme.INK_DEEP);
		g.fill(new RoundRectangle2D.Float(tx, by, cw - 24, 4, 4, 4));
		g.setPaint(new GradientPaint(tx, 0, Theme.GOLD_DIM, x + cw, 0, Theme.GOLD_BRIGHT));
		g.fill(new RoundRectangle2D.Float(tx, by, (float) Math.max(4, (cw - 24) * share), 4, 4, 4));
	}
}
