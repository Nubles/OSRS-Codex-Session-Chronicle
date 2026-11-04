package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.AgilityCoach;
import com.codex.sessionchronicle.core.DiveWatch;
import com.codex.sessionchronicle.core.Underwater;
import com.codex.sessionchronicle.core.UnderwaterCoach;
import com.codex.sessionchronicle.core.UnderwaterLog;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * Underwater Agility and Thieving on the Agility tab: the dive under way, the reward the player is
 * saving mermaid's tears for and how long it takes at their own pace, their tears and experience an
 * hour with glistening tears paid the way they last chose, and their recent dives. It marks nothing
 * to click; it only keeps the player's own record.
 */
public final class UnderwaterView
{
	/** Dives listed. */
	static final int LISTED = 6;

	private UnderwaterView()
	{
	}

	/** Adds the underwater sections to a stack. */
	public static void addTo(StackPanel stack, UnderwaterCoach u, ItemIcons items)
	{
		if (u == null)
		{
			return;
		}
		UnderwaterLog.Dive live = u.getLive();
		boolean any = live != null || u.getTotal().getCount() > 0;
		stack.add(new SectionHeader(live != null ? "This dive" : "Underwater", live != null ? Format.lap(live.getMs()) : "Fossil Island"));
		if (live != null)
		{
			stack.add(new DiveRow(u));
		}
		else if (!any)
		{
			stack.add(SessionView.note("No dives logged yet", "Dive at Fossil Island's Underwater Agility and Thieving. Each dive is logged "
				+ "from its first tear or search to its last: tears an hour, clams and chests searched, and tears lost when the air runs out."));
		}
		if (u.getReward() != null)
		{
			stack.add(new RewardRow(u, items));
		}
		stack.add(new PaceRow(u));
		if (!u.getRecent().isEmpty())
		{
			int n = u.getTotal().getCount() - (live == null ? 0 : 1);
			stack.add(new SectionHeader("Your dives", Format.count(n) + (n == 1 ? " dive" : " dives")));
			stack.add(new DivesRow(u.getRecent(), u.getBestRate()));
		}
		stack.add(Ui.wrapped("From the Old School wiki: about " + Underwater.WIKI_TEARS_LOW + " to " + Underwater.WIKI_TEARS_HIGH
				+ " tears an hour, 4.5 Agility and Thieving a search, and Mairin pays 0.018 x Agility² and 0.066 x Thieving² "
				+ "a glistening tear for both skills, half as much again for one. Your own pace replaces the wiki's after ten minutes of dives. "
				+ "Owned tears are those carried and in your bank" + (u.getBankTears() >= 0 ? "." : "; open your bank once so they count."),
			Theme.small(), Theme.TEXT_FAINT, SessionView.WIDTH - 10));
	}

	/** "212", tears an hour, whole. */
	static String perHour(double v)
	{
		return String.format(Locale.ENGLISH, "%,d", Math.round(Math.max(0, v)));
	}

	/** The dive under way: tears found against the clock, the pace, and what was searched and lost. */
	static final class DiveRow extends JComponent
	{
		static final int HEIGHT = 80;
		private final UnderwaterCoach u;

		DiveRow(UnderwaterCoach u)
		{
			this.u = u;
			setOpaque(false);
			setPreferredSize(new Dimension(10, HEIGHT));
			setToolTipText("<html>A dive runs from its first tear or search to its last.<br>A search is told a clam or a chest "
				+ "by what you clicked; Thieving experience is how a search is seen.</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			paint(g, u, 0, 0, getWidth(), HEIGHT);
			g.dispose();
		}

		static void paint(Graphics2D g, UnderwaterCoach u, int x, int y, int w, int h)
		{
			UnderwaterLog.Dive d = u.getLive();
			AgilityView.plate(g, x, y, w, h, Theme.TEAL);
			waves(g, x + 4, y + h - 30, w - 8, 22);
			int px = x + 10;
			int pw = w - 20;
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			int base = y + 6 + bold.getAscent();
			String tears = Format.count(d.getTears());
			Ui.shadowed(g, tears, px, base, Theme.GOLD_BRIGHT);
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(d.getTears() == 1 ? "tear" : "tears", px + bold.stringWidth(tears) + 4, base);
			double rate = d.tearsPerHour();
			String pace = d.getTicks() < DiveWatch.MIN_TICKS ? "timing" : perHour(rate) + "/hr";
			g.setFont(Theme.title());
			Ui.shadowed(g, pace, px + pw - bold.stringWidth(pace), base, rateColor(rate, u));

			g.setFont(Theme.small());
			int line = base + small.getHeight() + 1;
			String glist = Format.count(d.getGlistening()) + " glistening";
			g.setColor(Theme.TEAL);
			g.drawString(glist, px, line);
			String vs = versus(rate, u, d.getTicks());
			if (!vs.isEmpty())
			{
				g.setColor(Theme.TEXT_MUTED);
				g.drawString(Ui.clip(vs, small, pw - small.stringWidth(glist) - 8), px + pw - Math.min(small.stringWidth(vs),
					pw - small.stringWidth(glist) - 8), line);
			}

			int foot = y + h - 8;
			String searched = searched(d);
			String lost = d.getLost() > 0 ? Format.count(d.getLost()) + " lost" : "";
			int room = pw - (lost.isEmpty() ? 0 : small.stringWidth(lost) + 8);
			if (small.stringWidth(searched) > room && searched.endsWith(" searched"))
			{
				searched = searched.substring(0, searched.length() - " searched".length());
			}
			Ui.shadowed(g, Ui.clip(searched, small, room), px, foot, Theme.TEXT);
			if (!lost.isEmpty())
			{
				Ui.shadowed(g, lost, px + pw - small.stringWidth(lost), foot, Theme.EMBER);
			}
		}

		/** "14 clams, 9 chests" or "23 searches". */
		static String searched(UnderwaterLog.Dive d)
		{
			StringBuilder b = new StringBuilder();
			if (d.getClams() > 0)
			{
				b.append(Format.count(d.getClams())).append(d.getClams() == 1 ? " clam" : " clams");
			}
			if (d.getChests() > 0)
			{
				b.append(b.length() > 0 ? ", " : "").append(Format.count(d.getChests())).append(d.getChests() == 1 ? " chest" : " chests");
			}
			if (d.getOthers() > 0)
			{
				b.append(b.length() > 0 ? ", " : "").append(Format.count(d.getOthers())).append(" more");
			}
			return b.length() == 0 ? "Nothing searched yet" : b.append(" searched").toString();
		}

		/** "18 over your usual" against the player's pace, or "" before there is one of their own. */
		static String versus(double rate, UnderwaterCoach u, long ticks)
		{
			if (ticks < DiveWatch.MIN_TICKS || u.getSource() != AgilityCoach.Source.YOURS)
			{
				return "";
			}
			long diff = Math.round(rate - u.getTearsPerHour());
			if (diff == 0)
			{
				return "your usual pace";
			}
			return Format.count(Math.abs(diff)) + (diff > 0 ? " over" : " under") + " your pace";
		}

		static Color rateColor(double rate, UnderwaterCoach u)
		{
			if (u.getSource() != AgilityCoach.Source.YOURS || rate <= 0)
			{
				return Theme.TEXT;
			}
			return rate >= u.getTearsPerHour() ? Theme.GREEN : Theme.EMBER;
		}

		/** Three soft swells along the foot of the plate. */
		static void waves(Graphics2D g, int x, int y, int w, int h)
		{
			for (int i = 0; i < 3; i++)
			{
				Path2D p = new Path2D.Float();
				float amp = 2.2f + i * 0.8f;
				float base = y + 6 + i * 6;
				p.moveTo(x, base);
				for (int s = 1; s <= w; s += 3)
				{
					p.lineTo(x + s, base + amp * Math.sin((s + i * 17) / 14.0));
				}
				g.setStroke(new BasicStroke(1.2f));
				g.setColor(Theme.alpha(Theme.TEAL, 34 - i * 9));
				g.draw(p);
			}
			g.setStroke(new BasicStroke(1f));
		}
	}

	/** The reward from Mairin's Market being saved for: the tears owned against its price, and the time to go at the player's pace. */
	static final class RewardRow extends JComponent
	{
		static final int HEIGHT = 84;
		private static final int ICON = 32;
		private final UnderwaterCoach u;
		private final ItemIcons items;

		RewardRow(UnderwaterCoach u, ItemIcons items)
		{
			this.u = u;
			this.items = items;
			setOpaque(false);
			setPreferredSize(new Dimension(10, HEIGHT));
			Underwater.Reward r = u.getReward();
			setToolTipText("<html>" + Ui.escape(r.getName()) + " costs " + r.getCost() + " mermaid's tears at Mairin's Market.<br>"
				+ "Choose what you are saving for in Session Chronicle's settings.</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			BufferedImage icon = items == null ? null : items.icon(u.getReward().getItemId(), this::repaint);
			paint(g, u, icon, 0, 0, getWidth(), HEIGHT);
			g.dispose();
		}

		static void paint(Graphics2D g, UnderwaterCoach u, BufferedImage icon, int x, int y, int w, int h)
		{
			Underwater.Reward r = u.getReward();
			boolean enough = u.tearsNeeded() == 0;
			AgilityView.plate(g, x, y, w, h, enough ? Theme.GREEN : Theme.GOLD);
			int px = x + 10;
			int pw = w - 20;
			RoundRectangle2D well = new RoundRectangle2D.Float(px, y + 8, ICON + 4, ICON + 4, 6, 6);
			g.setColor(Theme.INK_DEEP);
			g.fill(well);
			g.setColor(Theme.STROKE_SOFT);
			g.draw(well);
			if (icon != null)
			{
				g.drawImage(icon, px + 2, y + 10, ICON, ICON, null);
			}
			int tx = px + ICON + 10;
			int tw = pw - ICON - 10;
			g.setFont(Theme.body());
			FontMetrics body = g.getFontMetrics();
			int base = y + 8 + body.getAscent();
			Ui.shadowed(g, Ui.clip(r.getName(), body, tw), tx, base, Theme.GOLD_BRIGHT);

			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			int sub = base + small.getHeight() + 1;
			String head = enough ? "Enough: visit Mairin" : Format.count(u.tearsNeeded()) + " to go";
			g.setColor(enough ? Theme.GREEN : Theme.TEXT);
			g.drawString(head, tx, sub);
			String owned = Format.count(u.getOwned()) + " / " + Format.count(r.getCost());
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(owned, px + pw - small.stringWidth(owned), sub);

			int by = y + 8 + ICON + 4 + 6;
			double share = Math.min(1, u.getOwned() / (double) r.getCost());
			g.setColor(Theme.INK_DEEP);
			g.fill(new RoundRectangle2D.Float(px, by, pw, 6, 6, 6));
			float fill = (float) Math.max(6, pw * share);
			g.setPaint(new GradientPaint(px, 0, Theme.mix(Theme.TEAL, Theme.INK, 0.45), px + fill, 0, enough ? Theme.GREEN : Theme.GOLD));
			g.fill(new RoundRectangle2D.Float(px, by, fill, 6, 6, 6));
			g.setColor(Theme.STROKE_SOFT);
			g.draw(new RoundRectangle2D.Float(px, by, pw, 6, 6, 6));

			g.setColor(Theme.TEXT_FAINT);
			g.drawString(Ui.clip(eta(u), small, pw), px, y + h - 7);
		}

		/** "About 41m at your pace, 2 dives like your usual" or why it cannot be told. */
		static String eta(UnderwaterCoach u)
		{
			if (u.tearsNeeded() == 0)
			{
				return "Mairin sells it underwater.";
			}
			double hours = u.hoursToReward();
			if (hours < 0)
			{
				return "Dive once to learn your pace.";
			}
			String at = u.getSource() == AgilityCoach.Source.YOURS ? " at your pace" : " at the wiki's pace";
			int dives = u.divesToReward();
			return "About " + Format.duration(Math.max(60_000, Math.round(hours * 3_600_000))) + at
				+ (dives > 0 ? ", " + dives + (dives == 1 ? " dive" : " dives") : "");
		}
	}

	/** Tears and experience an hour at the player's pace, and what the glistening tears carried would pay now. */
	static final class PaceRow extends JComponent
	{
		static final int HEIGHT = 104;
		private final UnderwaterCoach u;

		PaceRow(UnderwaterCoach u)
		{
			this.u = u;
			setOpaque(false);
			setPreferredSize(new Dimension(10, HEIGHT));
			setToolTipText("<html>Experience an hour: your glistening tears an hour, paid by Mairin at your levels the way you last chose, "
				+ "with your searches' and obstacles' experience on top.<br>Mairin takes at most "
				+ Underwater.EXCHANGE_CAP + " glistening tears at once; the rest are discarded.</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			paint(g, u, 0, 0, getWidth(), HEIGHT);
			g.dispose();
		}

		static void paint(Graphics2D g, UnderwaterCoach u, int x, int y, int w, int h)
		{
			boolean yours = u.getSource() == AgilityCoach.Source.YOURS;
			AgilityView.plate(g, x, y, w, h, yours ? Theme.TEAL : Theme.GOLD_DIM);
			int px = x + 10;
			int pw = w - 18;
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			String head = "Your pace";
			Ui.shadowed(g, head, px, y + 6 + small.getAscent(), Theme.TEXT);
			AgilityView.RankRow.tag(g, yours ? "yours" : "wiki", px + small.stringWidth(head) + 5, y + 6, yours ? Theme.TEAL : Theme.TEXT_FAINT);
			String levels = u.getAgilityLevel() > 0 ? "at " + u.getAgilityLevel() + " / " + u.getThievingLevel() : "";
			g.setColor(Theme.TEXT_FAINT);
			g.drawString(Ui.clip(levels, small, pw / 2), px + pw - Math.min(small.stringWidth(levels), pw / 2), y + 6 + small.getAscent());

			// Three figures side by side: tears, Agility and Thieving an hour.
			int cy = y + 22;
			int cw = pw / 3;
			column(g, px, cy, cw, perHour(u.getTearsPerHour()), "tears/hr", Theme.GOLD_BRIGHT);
			column(g, px + cw, cy, cw, u.getAgilityPerHour() > 0 ? Format.amount(u.getAgilityPerHour()) : "-", "Agility/hr", Theme.XP);
			column(g, px + 2 * cw, cy, cw, u.getThievingPerHour() > 0 ? Format.amount(u.getThievingPerHour()) : "-", "Thieving/hr", Theme.XP);

			int line = cy + 36 + small.getAscent();
			String paid = u.getMode().getShortLabel() + (u.isModeKnown() ? ", as you last chose" : ", until you hand in");
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(Ui.clip(paid, small, pw), px, line);

			int foot = y + h - 7;
			String[] carried = carried(u);
			g.setColor(u.isOverCap() ? Theme.EMBER : Theme.TEAL);
			g.drawString(Ui.clip(carried[0], small, pw), px, foot - (carried[1].isEmpty() ? 0 : small.getHeight()));
			if (!carried[1].isEmpty())
			{
				g.setColor(Theme.TEXT);
				g.drawString(Ui.clip(carried[1], small, pw), px, foot);
			}
		}

		static void column(Graphics2D g, int x, int y, int w, String value, String label, Color color)
		{
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			Ui.shadowed(g, Ui.clip(value, bold, w - 4), x, y + bold.getAscent(), color);
			g.setFont(Theme.small());
			g.setColor(Theme.TEXT_FAINT);
			g.drawString(label, x, y + bold.getAscent() + g.getFontMetrics().getHeight());
		}

		/** Two lines: the glistening tears carried, and what Mairin would pay for them now, or the cap. */
		static String[] carried(UnderwaterCoach u)
		{
			int n = u.getGlistening();
			if (n <= 0)
			{
				return new String[]{"No glistening tears carried", ""};
			}
			String head = Format.count(n) + " glistening " + (n == 1 ? "tear" : "tears") + " carried";
			if (u.isOverCap())
			{
				return new String[]{head, "Over " + Underwater.EXCHANGE_CAP + ": Mairin discards the rest"};
			}
			if (u.getAgilityLevel() <= 0)
			{
				return new String[]{head, ""};
			}
			StringBuilder b = new StringBuilder("Worth ");
			if (u.carriedAgilityXp() > 0)
			{
				b.append(Format.amount(u.carriedAgilityXp())).append(" Agility");
			}
			if (u.carriedThievingXp() > 0)
			{
				b.append(u.carriedAgilityXp() > 0 ? ", " : "").append(Format.amount(u.carriedThievingXp())).append(" Thieving");
			}
			return new String[]{head, b.toString()};
		}
	}

	/** Recent dives, one a line: the day, its length, tears, and its pace as a bar against the best dive. */
	static final class DivesRow extends JComponent
	{
		static final int LINE = 30;
		private final List<UnderwaterLog.Dive> dives;
		private final double best;

		DivesRow(List<UnderwaterLog.Dive> dives, double best)
		{
			this.dives = dives.subList(0, Math.min(LISTED, dives.size()));
			this.best = best;
			setOpaque(false);
			setPreferredSize(new Dimension(10, height(this.dives.size())));
			setToolTipText("<html>Your recent dives, newest first. The bar is the dive's tears an hour against your best dive "
				+ "of ten minutes or more.<br>An ember bead marks a dive where tears were lost to the air running out.</html>");
		}

		static int height(int rows)
		{
			return 8 + rows * LINE;
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			paint(g, dives, best, 0, 0, getWidth(), getHeight());
			g.dispose();
		}

		static void paint(Graphics2D g, List<UnderwaterLog.Dive> dives, double best, int x, int y, int w, int h)
		{
			AgilityView.plate(g, x, y, w, h, Theme.TEAL);
			int px = x + 10;
			int pw = w - 18;
			double top = best;
			for (UnderwaterLog.Dive d : dives)
			{
				top = Math.max(top, d.tearsPerHour());
			}
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			int ry = y + 5;
			for (UnderwaterLog.Dive d : dives)
			{
				int base = ry + small.getAscent();
				String when = Format.day(d.getStartAt()) + "  " + Format.duration(d.getMs());
				g.setColor(Theme.TEXT_MUTED);
				g.drawString(Ui.clip(when, small, pw / 2), px, base);
				double rate = d.tearsPerHour();
				String tears = Format.count(d.getTears()) + (rate > 0 ? "  " + perHour(rate) + "/hr" : "");
				boolean isBest = best > 0 && Math.abs(rate - best) < 0.01;
				Ui.shadowed(g, tears, px + pw - small.stringWidth(tears), base, isBest ? Theme.GOLD_BRIGHT : Theme.TEXT);

				int by = base + 4;
				g.setColor(Theme.INK_DEEP);
				g.fill(new RoundRectangle2D.Float(px, by, pw, 4, 4, 4));
				if (rate > 0 && top > 0)
				{
					float fill = (float) Math.max(4, pw * rate / top);
					g.setPaint(new GradientPaint(px, 0, Theme.mix(Theme.TEAL, Theme.INK, 0.5), px + fill, 0, isBest ? Theme.GOLD_BRIGHT : Theme.TEAL));
					g.fill(new RoundRectangle2D.Float(px, by, fill, 4, 4, 4));
				}
				if (d.getLost() > 0)
				{
					// A small ember bead for tears lost to the air.
					g.setColor(Theme.EMBER);
					g.fill(new Ellipse2D.Float(px + pw - 3, by - 1, 6, 6));
				}
				ry += LINE;
			}
		}
	}
}
