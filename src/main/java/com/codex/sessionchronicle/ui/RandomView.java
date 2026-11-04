package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.RandomEvents;
import com.codex.sessionchronicle.core.RandomLog;
import com.codex.sessionchronicle.core.RandomReport;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JComponent;

/**
 * Random events on the Journal tab: the player's own events an hour against the wiki's timer, how
 * their events ended, when the next could come by that timer (an estimate), their recent events with
 * what each gave, events by activity, and each event costume's pieces owned and still to come. It
 * marks nothing in the game and says nothing about where to click; it only keeps the player's record.
 */
public final class RandomView
{
	/** Events listed. */
	static final int LISTED = 5;
	/** Activities listed. */
	static final int ACTIVITIES = 4;

	private RandomView()
	{
	}

	/** Adds the random event sections to a stack. */
	public static void addTo(StackPanel stack, RandomReport r, ItemIcons items)
	{
		if (r == null)
		{
			return;
		}
		int n = r.getEvents();
		stack.add(new SectionHeader("Random events", n == 0 ? "none yet" : Format.count(n) + " in " + Format.duration(r.getWatchedMs())));
		stack.add(new RateRow(r));
		List<RandomLog.Seen> recent = r.recent(LISTED);
		if (!recent.isEmpty())
		{
			stack.add(new EventsRow(recent, items));
		}
		stack.add(new SectionHeader("Event costumes", r.piecesOwned() + " / " + r.piecesAll() + " pieces"));
		for (RandomReport.CostumeLine c : r.getCostumes())
		{
			stack.add(new CostumeRow(c, items));
		}
		List<Map.Entry<String, RandomLog.Tally>> spent = r.activities(ACTIVITIES);
		if (n > 0 && !spent.isEmpty())
		{
			stack.add(new SectionHeader("By activity", "bar: events an hour"));
			stack.add(new ActivitiesRow(spent));
		}
		stack.add(Ui.wrapped("From the Old School wiki: the game picks a moment \"between 1 and 2 hours\" after your last event, "
				+ "on a real-time clock, and checks \"every 5 minutes\"; events skip most banks, boss areas and minigames, and an event "
				+ "left alone \"disappears after a minute\". The rest of the timer is not public, so the next event's window is an estimate. "
				+ "A costume piece counts once seen in your bank, worn or carried.",
			Theme.small(), Theme.TEXT_FAINT, SessionView.WIDTH - 10));
	}

	/** "1 every 1h 31m", or "-" before an event. */
	static String every(long ms)
	{
		return ms <= 0 ? "-" : "1 every " + Format.duration(Math.max(60_000, ms));
	}

	/** "0.66" events an hour. */
	static String perHour(double v)
	{
		return String.format(Locale.ENGLISH, "%.2f", Math.max(0, v));
	}

	/** The line under the rate: how it sits against the wiki's timer, or how much play it still needs. */
	static String versus(RandomReport r)
	{
		if (r.getEvents() == 0)
		{
			return r.getWatchedMs() < 60_000 ? "Watching from now on" : "None in " + Format.duration(r.getWatchedMs()) + " of play";
		}
		if (!r.isOwnRate())
		{
			return "Early: " + Format.duration(r.getWatchedMs()) + " of play so far";
		}
		switch (r.versusWiki())
		{
			case -1:
				return "Fewer than the timer allows";
			case 1:
				return "More than the timer allows";
			default:
				return "Within the wiki's timer";
		}
	}

	/** "From 14:32, due by 15:32" by the wiki's timer, or why it cannot be told. */
	static String next(RandomReport r)
	{
		if (r.getCurrent() != null)
		{
			return r.getCurrent().getName() + " is under way";
		}
		long from = r.nextFrom();
		if (from <= 0)
		{
			return "Your first event starts the clock";
		}
		long now = r.getNow();
		if (now < from)
		{
			return "Next from " + Format.time(from) + ", due by " + Format.time(r.nextBy());
		}
		if (now < r.nextBy())
		{
			return "Could come now, due by " + Format.time(r.nextBy());
		}
		return "Overdue: likely missed in a bank or minigame";
	}

	/** "Lamp" or "2 x Uncut sapphire, Kebab" for what an event gave; "" for nothing seen. */
	static String reward(RandomLog.Seen s)
	{
		StringBuilder b = new StringBuilder();
		for (RandomLog.Gain g : s.getReward())
		{
			if (b.length() > 0)
			{
				b.append(", ");
			}
			if (g.getQty() > 1)
			{
				b.append(Format.count(g.getQty())).append(" x ");
			}
			b.append(g.getName());
		}
		return b.toString();
	}

	static Color outcomeColor(RandomLog.Outcome o)
	{
		switch (o)
		{
			case DONE:
				return Theme.GREEN;
			case TAKEN:
				return Theme.TEAL;
			case DISMISSED:
				return Theme.EMBER;
			default:
				return Theme.TEXT_FAINT;
		}
	}

	/** "Drill Demon gives the rest, about 2 more" or "Complete". */
	static String costumeLine(RandomReport.CostumeLine c)
	{
		if (c.isDone())
		{
			return "Complete";
		}
		int left = c.completionsLeft();
		return c.getCostume().getEvent().getName() + ": about " + left + " more"
			+ (c.getCompleted() > 0 ? ", " + c.getCompleted() + " done" : "");
	}

	/** The player's rate against the wiki's timer, how their events ended, and when the next could come. */
	static final class RateRow extends JComponent
	{
		static final int HEIGHT = 112;
		private final RandomReport r;

		RateRow(RandomReport r)
		{
			this.r = r;
			setOpaque(false);
			setPreferredSize(new Dimension(10, HEIGHT));
			setToolTipText("<html>Your events an hour of logged-in play watched here, set against the wiki's timer:<br>"
				+ "a moment between one and two hours after the last event. Events missed in banks, boss areas and minigames<br>"
				+ "are not made up, so fewer is usual. The next event's window is that timer from your last event: an estimate.</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			paint(g, r, 0, 0, getWidth(), HEIGHT);
			g.dispose();
		}

		static void paint(Graphics2D g, RandomReport r, int x, int y, int w, int h)
		{
			boolean own = r.isOwnRate();
			AgilityView.plate(g, x, y, w, h, own ? Theme.VIOLET : Theme.GOLD_DIM);
			int px = x + 10;
			int pw = w - 18;
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			int base = y + 6 + bold.getAscent();
			String every = r.getEvents() == 0 ? "No events yet" : every(r.msPerEvent());
			Ui.shadowed(g, Ui.clip(every, bold, pw - 30), px, base, own ? Theme.GOLD_BRIGHT : Theme.TEXT);
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			AgilityView.RankRow.tag(g, own ? "yours" : "early", px + pw - small.stringWidth(own ? "yours" : "early") - 6, y + 8,
				own ? Theme.VIOLET : Theme.TEXT_FAINT);

			int line = base + small.getHeight() + 1;
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(Ui.clip(versus(r), small, pw), px, line);
			line += small.getHeight();
			String wiki = "Wiki timer: 1 every 1h to 2h";
			g.setColor(Theme.TEXT_FAINT);
			g.drawString(Ui.clip(wiki, small, pw), px, line);

			// How the player's events ended, as one bar split by outcome.
			int by = line + 7;
			RandomLog.Tally t = r.getLog().getTotal();
			int all = t.getSeen();
			g.setColor(Theme.INK_DEEP);
			g.fill(new RoundRectangle2D.Float(px, by, pw, 6, 6, 6));
			if (all > 0)
			{
				int[] parts = {t.getDone(), t.getTaken(), t.getDismissed(), t.getIgnored()};
				RandomLog.Outcome[] kinds = RandomLog.Outcome.values();
				float at = px;
				java.awt.Shape clip = g.getClip();
				g.clip(new RoundRectangle2D.Float(px, by, pw, 6, 6, 6));
				for (int i = 0; i < parts.length; i++)
				{
					float len = pw * parts[i] / (float) all;
					if (len > 0)
					{
						g.setColor(outcomeColor(kinds[i]));
						g.fill(new java.awt.geom.Rectangle2D.Float(at, by, len + 0.5f, 6));
						at += len;
					}
				}
				g.setClip(clip);
			}
			g.setColor(Theme.STROKE_SOFT);
			g.draw(new RoundRectangle2D.Float(px, by, pw, 6, 6, 6));

			int ly = by + 8 + small.getAscent();
			legend(g, small, px, ly, pw, t);

			g.setColor(Theme.TEXT);
			String next = next(r);
			int foot = y + h - 7;
			Ui.shadowed(g, Ui.clip(next, small, pw), px, foot, r.getCurrent() != null ? Theme.GREEN : Theme.TEXT);
		}

		/** "3 done  1 taken  2 dismissed  4 ignored" with a dot of each colour, leaving out the empty ones. */
		static void legend(Graphics2D g, FontMetrics small, int x, int y, int w, RandomLog.Tally t)
		{
			int[] parts = {t.getDone(), t.getTaken(), t.getDismissed(), t.getIgnored()};
			String[] words = {"done", "no reward", "dismissed", "ignored"};
			RandomLog.Outcome[] kinds = RandomLog.Outcome.values();
			int at = x;
			boolean any = false;
			for (int i = 0; i < parts.length; i++)
			{
				if (parts[i] == 0)
				{
					continue;
				}
				String s = parts[i] + " " + words[i];
				int need = small.stringWidth(s) + 8;
				if (at + need > x + w && at > x)
				{
					// The rest go on a second line.
					at = x;
					y += small.getHeight();
				}
				g.setColor(outcomeColor(kinds[i]));
				g.fillOval(at, y - 5, 5, 5);
				g.setColor(Theme.TEXT_MUTED);
				g.drawString(s, at + 7, y);
				at += need + 5;
				any = true;
			}
			if (!any)
			{
				g.setColor(Theme.TEXT_FAINT);
				g.drawString("Each event is logged as it ends", x, y);
			}
		}
	}

	/** Recent events, newest first: the event's picture, its name and how it ended, when, doing what, and what it gave. */
	static final class EventsRow extends JComponent
	{
		static final int LINE = 40;
		private static final int ICON = 26;
		private final List<RandomLog.Seen> events;
		private final ItemIcons items;

		EventsRow(List<RandomLog.Seen> events, ItemIcons items)
		{
			this.events = events;
			this.items = items;
			setOpaque(false);
			setPreferredSize(new Dimension(10, 8 + events.size() * LINE));
			setToolTipText("<html>Your recent random events, newest first, with what you were doing when each came.<br>"
				+ "A reward is what your pack gained from the event; with a full pack, or for experience, none is seen.<br>"
				+ "Where none was seen, the wiki's usual reward is shown faint.</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			paint(g, events, items, this::repaint, 0, 0, getWidth(), getHeight());
			g.dispose();
		}

		static void paint(Graphics2D g, List<RandomLog.Seen> events, ItemIcons items, Runnable onLoaded, int x, int y, int w, int h)
		{
			AgilityView.plate(g, x, y, w, h, Theme.VIOLET);
			int px = x + 9;
			int pw = w - 16;
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			int ry = y + 5;
			for (RandomLog.Seen s : events)
			{
				RandomEvents.Event e = s.getEvent();
				RoundRectangle2D well = new RoundRectangle2D.Float(px, ry + 3, ICON + 2, ICON + 2, 6, 6);
				g.setColor(Theme.INK_DEEP);
				g.fill(well);
				g.setColor(Theme.STROKE_SOFT);
				g.draw(well);
				int iconId = !s.getReward().isEmpty() ? s.getReward().get(0).getId() : e.getIconId();
				BufferedImage icon = items == null || iconId < 0 ? null : items.icon(iconId, onLoaded);
				if (icon != null)
				{
					Composite old = g.getComposite();
					if (s.getReward().isEmpty())
					{
						g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
					}
					g.drawImage(icon, px + 1, ry + 4, ICON, ICON * icon.getHeight() / Math.max(1, icon.getWidth()), null);
					g.setComposite(old);
				}
				else
				{
					sigil(g, px + 1 + ICON / 2f, ry + 4 + ICON / 2f);
				}
				int tx = px + ICON + 8;
				int tw = pw - ICON - 8;
				int base = ry + small.getAscent() + 1;
				RandomLog.Outcome o = s.getOutcome();
				String tag = o.getLabel().toLowerCase(Locale.ROOT);
				int tagW = small.stringWidth(tag) + 6;
				Ui.shadowed(g, Ui.clip(e.getName(), small, tw - tagW - 4), tx, base, Theme.GOLD_BRIGHT);
				AgilityView.RankRow.tag(g, tag, tx + tw - tagW, ry + 2, outcomeColor(o));
				g.setColor(Theme.TEXT_MUTED);
				String when = Format.day(s.getAt()) + " " + Format.time(s.getAt());
				String doing = s.getActivity() == null ? "" : ", " + s.getActivity();
				g.drawString(Ui.clip(when + doing, small, tw), tx, base + small.getHeight());
				String got = reward(s);
				boolean seen = !got.isEmpty();
				if (!seen)
				{
					got = o == RandomLog.Outcome.DONE || o == RandomLog.Outcome.TAKEN ? "Wiki: " + e.getReward() : "No reward";
				}
				g.setColor(seen ? Theme.TEXT : Theme.TEXT_FAINT);
				g.drawString(Ui.clip(got, small, tw), tx, base + 2 * small.getHeight());
				ry += LINE;
			}
		}

		/** A small gilt question mark in a ring, for an event with no picture of its own. */
		static void sigil(Graphics2D g, float cx, float cy)
		{
			g.setColor(Theme.alpha(Theme.VIOLET, 140));
			g.setStroke(new BasicStroke(1.4f));
			g.draw(new java.awt.geom.Ellipse2D.Float(cx - 8, cy - 8, 16, 16));
			g.setStroke(new BasicStroke(1f));
			g.setFont(Theme.small());
			FontMetrics fm = g.getFontMetrics();
			g.setColor(Theme.GOLD);
			g.drawString("?", cx - fm.stringWidth("?") / 2f, cy + fm.getAscent() / 2f - 1);
		}
	}

	/** One costume: its pieces as pictures, dim where missing, the count owned, and the event that gives the rest. */
	static final class CostumeRow extends JComponent
	{
		static final int HEIGHT = 66;
		private final RandomReport.CostumeLine c;
		private final ItemIcons items;

		CostumeRow(RandomReport.CostumeLine c, ItemIcons items)
		{
			this.c = c;
			this.items = items;
			setOpaque(false);
			setPreferredSize(new Dimension(10, HEIGHT));
			StringBuilder tip = new StringBuilder("<html>").append(Ui.escape(c.getCostume().getName())).append(" comes from ")
				.append(Ui.escape(c.getCostume().getEvent().getName())).append(". The wiki: it needs ").append(c.getCostume().getCompletions())
				.append(" completions for every piece").append(c.getCostume() == RandomEvents.Costume.ROYAL_FROG ? " (frog tokens, at Thessalia in Varrock)"
					: c.getCostume() == RandomEvents.Costume.CAMOUFLAGE || c.getCostume() == RandomEvents.Costume.ZOMBIE
					|| c.getCostume() == RandomEvents.Costume.MIME ? " and its emotes" : "").append(".<br>");
			if (!c.getMissing().isEmpty())
			{
				tip.append("Missing: ");
				for (int i = 0; i < c.getMissing().size(); i++)
				{
					tip.append(i > 0 ? ", " : "").append(Ui.escape(c.getMissing().get(i).getName()));
				}
				tip.append(".<br>\"About\" shares the wiki's count over the pieces missing; emotes are not seen here, so it is an estimate.");
			}
			else
			{
				tip.append("Every piece seen owned.");
			}
			setToolTipText(tip.append("</html>").toString());
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			paint(g, c, items, this::repaint, 0, 0, getWidth(), HEIGHT);
			g.dispose();
		}

		static void paint(Graphics2D g, RandomReport.CostumeLine c, ItemIcons items, Runnable onLoaded, int x, int y, int w, int h)
		{
			boolean done = c.isDone();
			AgilityView.plate(g, x, y, w, h, done ? Theme.GREEN : c.getOwned().isEmpty() ? Theme.GOLD_DIM : Theme.GOLD);
			int px = x + 9;
			int pw = w - 16;
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			int base = y + 5 + small.getAscent();
			RandomEvents.Piece[] pieces = c.getCostume().getPieces();
			String count = c.getOwned().size() + "/" + pieces.length;
			Ui.shadowed(g, Ui.clip(c.getCostume().getName(), small, pw - small.stringWidth(count) - 6), px, base,
				done ? Theme.GOLD_BRIGHT : Theme.TEXT);
			g.setColor(done ? Theme.GREEN : Theme.TEXT_MUTED);
			g.drawString(count, px + pw - small.stringWidth(count), base);

			int cell = 26;
			int iy = base + 4;
			for (int i = 0; i < pieces.length; i++)
			{
				RandomEvents.Piece p = pieces[i];
				boolean owned = c.isOwned(p);
				int cx = px + i * (cell + 3);
				RoundRectangle2D well = new RoundRectangle2D.Float(cx, iy, cell, 22, 5, 5);
				g.setColor(owned ? Theme.alpha(Theme.GOLD, 40) : Theme.alpha(Theme.INK_DEEP, 200));
				g.fill(well);
				g.setColor(owned ? Theme.alpha(Theme.GOLD, 140) : Theme.STROKE_SOFT);
				g.draw(well);
				BufferedImage icon = items == null ? null : items.icon(p.getItemId(), onLoaded);
				if (icon != null)
				{
					int iw = Math.min(20, icon.getWidth());
					int ih = icon.getHeight() * iw / Math.max(1, icon.getWidth());
					Composite old = g.getComposite();
					if (!owned)
					{
						g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
					}
					g.drawImage(icon, cx + (cell - iw) / 2, iy + (22 - ih) / 2, iw, ih, null);
					g.setComposite(old);
				}
				if (owned)
				{
					// A small gilt tick in the corner of a piece owned.
					Path2D tick = new Path2D.Float();
					tick.moveTo(cx + cell - 8, iy + 18);
					tick.lineTo(cx + cell - 6, iy + 20);
					tick.lineTo(cx + cell - 2, iy + 15);
					g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.setColor(Theme.GOLD_BRIGHT);
					g.draw(tick);
					g.setStroke(new BasicStroke(1f));
				}
			}
			int right = px + pieces.length * (cell + 3);
			if (right < px + pw - 10)
			{
				// The share owned as a slim upright gauge after the pieces.
				int gh = 22;
				int gx = px + pw - 5;
				g.setColor(Theme.INK_DEEP);
				g.fill(new RoundRectangle2D.Float(gx, iy, 5, gh, 4, 4));
				float fill = gh * c.getOwned().size() / (float) pieces.length;
				if (fill > 0)
				{
					g.setPaint(new GradientPaint(0, iy + gh, Theme.GOLD_DIM, 0, iy, done ? Theme.GREEN : Theme.GOLD_BRIGHT));
					g.fill(new RoundRectangle2D.Float(gx, iy + gh - fill, 5, fill, 4, 4));
				}
				g.setColor(Theme.STROKE_SOFT);
				g.draw(new RoundRectangle2D.Float(gx, iy, 5, gh, 4, 4));
			}
			g.setColor(done ? Theme.GREEN : Theme.TEXT_FAINT);
			g.drawString(Ui.clip(costumeLine(c), small, pw), px, y + h - 6);
		}
	}

	/** Events an hour by what the player was doing, each a line with a bar against the busiest. */
	static final class ActivitiesRow extends JComponent
	{
		static final int LINE = 26;
		private final List<Map.Entry<String, RandomLog.Tally>> spent;

		ActivitiesRow(List<Map.Entry<String, RandomLog.Tally>> spent)
		{
			this.spent = spent;
			setOpaque(false);
			setPreferredSize(new Dimension(10, 8 + spent.size() * LINE));
			setToolTipText("<html>What you were doing is the skill that gained experience in the two minutes before the event,<br>"
				+ "or the monster you were fighting. Events an hour count your play at each, watched here.</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			paint(g, spent, 0, 0, getWidth(), getHeight());
			g.dispose();
		}

		static void paint(Graphics2D g, List<Map.Entry<String, RandomLog.Tally>> spent, int x, int y, int w, int h)
		{
			AgilityView.plate(g, x, y, w, h, Theme.TEAL);
			int px = x + 10;
			int pw = w - 18;
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			double top = 0;
			for (Map.Entry<String, RandomLog.Tally> e : spent)
			{
				top = Math.max(top, rate(e.getValue()));
			}
			int ry = y + 5;
			for (Map.Entry<String, RandomLog.Tally> e : spent)
			{
				RandomLog.Tally t = e.getValue();
				int base = ry + small.getAscent();
				String right = t.getSeen() + " in " + Format.duration(t.getMs());
				g.setColor(Theme.TEXT);
				g.drawString(Ui.clip(e.getKey(), small, pw - small.stringWidth(right) - 6), px, base);
				g.setColor(Theme.TEXT_MUTED);
				g.drawString(right, px + pw - small.stringWidth(right), base);
				int by = base + 4;
				g.setColor(Theme.INK_DEEP);
				g.fill(new RoundRectangle2D.Float(px, by, pw, 4, 4, 4));
				double rate = rate(t);
				if (rate > 0 && top > 0)
				{
					float fill = (float) Math.max(4, pw * rate / top);
					g.setPaint(new GradientPaint(px, 0, Theme.mix(Theme.TEAL, Theme.INK, 0.5), px + fill, 0, Theme.TEAL));
					g.fill(new RoundRectangle2D.Float(px, by, fill, 4, 4, 4));
				}
				ry += LINE;
			}
		}

		/** Events an hour at an activity; 0 under ten minutes of it. */
		static double rate(RandomLog.Tally t)
		{
			return t.getMs() < 600_000 ? 0 : t.getSeen() * 3_600_000.0 / t.getMs();
		}
	}
}
