package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.AgilityCoach;
import com.codex.sessionchronicle.core.AgilityLog;
import com.codex.sessionchronicle.core.Graceful;
import com.codex.sessionchronicle.core.LapClock;
import com.codex.sessionchronicle.core.Rooftop;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
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
 * The Agility tab: the rooftop coach. The lap under way raced against the player's best lap on the
 * course, obstacle by obstacle; the Graceful outfit and the marks still to find, with how long that
 * takes at the player's own pace; each course they have run, with best and typical laps, experience
 * and marks an hour; and which course is best for them now, from their own laps where they have
 * them and the wiki's figures where they do not, always saying which.
 */
public final class AgilityView
{
	private AgilityView()
	{
	}

	public static JComponent build(AgilityCoach coach, ItemIcons items)
	{
		StackPanel stack = new StackPanel(8, 8);
		if (coach == null)
		{
			stack.add(SessionView.note("Rooftop coach", "Log in and run a rooftop course: every lap is timed, your best lap becomes a ghost to race, "
				+ "and your marks of grace are counted toward a Graceful outfit."));
			return stack;
		}
		LapClock.Split split = coach.getSplit();
		if (split != null)
		{
			stack.add(new SectionHeader("This lap", split.getCourse().getShortName()));
			stack.add(new LapRow(split));
		}

		stack.add(new SectionHeader("Graceful", coach.outfitDone() ? "complete" : coach.getOwned().size() + " of " + Graceful.values().length + " pieces"));
		stack.add(new OutfitRow(coach, items));

		List<AgilityCoach.Course> run = coach.run();
		if (!run.isEmpty())
		{
			stack.add(new SectionHeader("Your courses", Format.count(coach.getLaps()) + (coach.getLaps() == 1 ? " lap" : " laps")));
			for (AgilityCoach.Course c : run)
			{
				stack.add(new CourseRow(c));
			}
		}
		else if (split == null)
		{
			stack.add(SessionView.note("No laps timed yet", "Run any rooftop course. A lap is timed from one lap count message to the next, "
				+ "and each obstacle's experience marks a split for the ghost of your best lap."));
		}

		List<AgilityCoach.Course> ranked = coach.ranked();
		if (!ranked.isEmpty())
		{
			stack.add(new SectionHeader("Best for you now", "level " + coach.getLevel()));
			stack.add(new RankRow(coach, ranked));
		}
		stack.add(Ui.wrapped("Your figures come from your own laps once a course has " + AgilityCoach.OWN_LAPS + "; before that they are the "
				+ "Old School wiki's best-case rates. Expected marks follow the wiki's mark rules: a timer of two to three minutes after a mark, "
				+ "then a set chance each lap, 80% lower 20 levels over the course (not at Canifis). Graceful counts pieces and marks seen in "
				+ "your bank, worn or carried" + (coach.isBankSeen() ? "." : "; open your bank once so the ones in it count."),
			Theme.small(), Theme.TEXT_FAINT, SessionView.WIDTH - 10));
		return stack;
	}

	/** Green ahead, ember behind, gold level or unknown. */
	public static Color gapColorOf(Integer ticks)
	{
		return gapColor(ticks);
	}

	static Color gapColor(Integer ticks)
	{
		if (ticks == null)
		{
			return Theme.GOLD;
		}
		return ticks < 0 ? Theme.GREEN : ticks > 0 ? Theme.EMBER : Theme.GOLD;
	}

	/** "+1.2s behind", "0.6s ahead" or "level with your best": the gap at the last obstacle in words. */
	public static String gapWords(LapClock.Split s)
	{
		Integer d = s.delta();
		if (!s.hasGhost())
		{
			return "First timed lap here: it becomes your ghost";
		}
		if (d == null)
		{
			Integer p = s.projected();
			return p != null ? "Slower to the first obstacle than your best" : "Racing your best lap of " + LapClock.time(s.getBestTicks());
		}
		String at = " at obstacle " + s.getObstacles();
		if (d == 0)
		{
			return "Level with your best" + at;
		}
		return LapClock.gap(d).substring(1) + (d > 0 ? " behind" : " ahead") + at;
	}

	/** The lap under way: its clock, the gap to the best lap, and the obstacles as a track with the ghost on it. */
	static final class LapRow extends JComponent
	{
		static final int HEIGHT = 86;
		private final LapClock.Split split;

		LapRow(LapClock.Split split)
		{
			this.split = split;
			setOpaque(false);
			setPreferredSize(new Dimension(10, HEIGHT));
			setToolTipText("<html>Laps are timed in game ticks, start to start. The ghost is your best lap on this course; "
				+ "each obstacle's experience is a split against it.</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			paint(g, split, 0, 0, getWidth(), HEIGHT);
			g.dispose();
		}

		static void paint(Graphics2D g, LapClock.Split s, int x, int y, int w, int h)
		{
			Integer d = s.delta();
			Color tint = gapColor(d);
			plate(g, x, y, w, h, tint);
			int px = x + 10;
			int pw = w - 20;
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			int base = y + 6 + bold.getAscent();
			Ui.shadowed(g, LapClock.time(s.getElapsed()), px, base, Theme.TEXT);
			if (d != null)
			{
				String gap = LapClock.gap(d);
				Ui.shadowed(g, gap, px + pw - bold.stringWidth(gap), base, tint);
			}
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			int line = base + small.getHeight() + 1;
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(Ui.clip(gapWords(s), small, pw), px, line);

			// The obstacles as a row of stones: yours lit gold, the ghost's place as a teal ring.
			int stones = Math.max(Math.max(s.getGhostObstacles(), s.getObstacles()), 1);
			int ty = line + 10;
			float step = stones <= 1 ? 0 : (pw - 8) / (float) (stones - 1);
			g.setColor(Theme.alpha(Theme.STROKE, 200));
			g.setStroke(new BasicStroke(2f));
			g.drawLine(px + 4, ty, px + pw - 4, ty);
			int ghostAt = s.hasGhost() ? s.ghostObstaclesNow() : -1;
			for (int i = 0; i < stones; i++)
			{
				float cx = px + 4 + step * i;
				boolean done = i < s.getObstacles();
				g.setColor(done ? Theme.GOLD : Theme.INK_DEEP);
				g.fill(new Ellipse2D.Float(cx - 3.5f, ty - 3.5f, 7, 7));
				g.setStroke(new BasicStroke(1f));
				g.setColor(done ? Theme.GOLD_BRIGHT : Theme.GOLD_DIM);
				g.draw(new Ellipse2D.Float(cx - 3.5f, ty - 3.5f, 7, 7));
				if (i == ghostAt - 1)
				{
					g.setColor(Theme.alpha(Theme.TEAL, 90));
					g.fill(new Ellipse2D.Float(cx - 7, ty - 7, 14, 14));
					g.setColor(Theme.TEAL);
					g.draw(new Ellipse2D.Float(cx - 6, ty - 6, 12, 12));
				}
			}

			int foot = y + h - 7;
			String best = s.hasGhost() ? "Best " + LapClock.time(s.getBestTicks()) : "No best lap yet";
			g.setColor(Theme.TEXT_FAINT);
			g.drawString(best, px, foot);
			Integer p = s.projected();
			if (p != null)
			{
				String on = "on pace for " + LapClock.time(p);
				g.setColor(gapColor(p - s.getBestTicks()));
				g.drawString(on, px + pw - small.stringWidth(on), foot);
			}
		}
	}

	/** The six pieces, lit where owned, and the marks still to find with how long that takes. */
	static final class OutfitRow extends JComponent
	{
		static final int HEIGHT = 112;
		private final AgilityCoach coach;
		private final ItemIcons items;

		OutfitRow(AgilityCoach coach, ItemIcons items)
		{
			this.coach = coach;
			this.items = items;
			setOpaque(false);
			setPreferredSize(new Dimension(10, HEIGHT));
			setToolTipText("<html>Graceful pieces cost 35 to 60 marks of grace each from Grace in the Rogues' Den, 260 for the set.<br>"
				+ "A piece counts as owned once seen in your bank, worn or carried.</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			paint(g, coach, items, this::repaint, 0, 0, getWidth(), HEIGHT);
			g.dispose();
		}

		static void paint(Graphics2D g, AgilityCoach coach, ItemIcons items, Runnable onLoaded, int x, int y, int w, int h)
		{
			boolean done = coach.outfitDone();
			plate(g, x, y, w, h, done ? Theme.GREEN : Theme.GOLD);
			int px = x + 8;
			int pw = w - 16;
			Graceful[] pieces = Graceful.values();
			float cell = pw / (float) pieces.length;
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			for (int i = 0; i < pieces.length; i++)
			{
				Graceful piece = pieces[i];
				boolean owned = coach.getOwned().contains(piece);
				int cx = Math.round(px + cell * i + cell / 2);
				RoundRectangle2D well = new RoundRectangle2D.Float(cx - cell / 2 + 1.5f, y + 7, cell - 3, 34, 6, 6);
				g.setColor(owned ? Theme.alpha(Theme.GOLD, 40) : Theme.alpha(Theme.INK_DEEP, 200));
				g.fill(well);
				g.setColor(owned ? Theme.alpha(Theme.GOLD, 140) : Theme.STROKE_SOFT);
				g.draw(well);
				BufferedImage icon = items == null ? null : items.icon(piece.getIconId(), onLoaded);
				if (icon != null)
				{
					int iw = Math.min(28, icon.getWidth());
					int ih = icon.getHeight() * iw / Math.max(1, icon.getWidth());
					Composite old = g.getComposite();
					if (!owned)
					{
						g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.32f));
					}
					g.drawImage(icon, cx - iw / 2, y + 7 + (34 - ih) / 2, iw, ih, null);
					g.setComposite(old);
				}
				int ly = y + 41 + small.getAscent() + 2;
				if (owned)
				{
					// A gilt tick for a piece owned; its price no longer matters.
					Path2D tick = new Path2D.Float();
					tick.moveTo(cx - 4, ly - 4);
					tick.lineTo(cx - 1, ly - 1);
					tick.lineTo(cx + 4, ly - 7);
					g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.setColor(Theme.GOLD_BRIGHT);
					g.draw(tick);
					g.setStroke(new BasicStroke(1f));
				}
				else
				{
					String label = String.valueOf(piece.getCost());
					g.setColor(Theme.TEXT_MUTED);
					g.drawString(label, cx - small.stringWidth(label) / 2, ly);
				}
			}

			int barY = y + 62;
			int need = Graceful.costOfMissing(coach.getOwned());
			int have = Math.min(need, coach.getMarksOwned());
			double share = need <= 0 ? 1 : have / (double) need;
			g.setColor(Theme.INK_DEEP);
			g.fill(new RoundRectangle2D.Float(px, barY, pw, 6, 6, 6));
			float fill = (float) Math.max(6, pw * share);
			g.setPaint(new GradientPaint(px, 0, Theme.mix(Theme.GOLD, Theme.INK, 0.45), px + fill, 0, Theme.progressColor(share)));
			g.fill(new RoundRectangle2D.Float(px, barY, fill, 6, 6, 6));

			g.setFont(Theme.body());
			FontMetrics body = g.getFontMetrics();
			int line = barY + 8 + body.getAscent();
			String head;
			if (done)
			{
				head = "The full set is yours";
			}
			else if (coach.marksNeeded() == 0)
			{
				head = "Enough marks for the rest";
			}
			else
			{
				head = Format.count(coach.marksNeeded()) + " marks to go";
			}
			Ui.shadowed(g, head, px, line, done || coach.marksNeeded() == 0 ? Theme.GREEN : Theme.GOLD_BRIGHT);
			String owned = Format.count(coach.getMarksOwned()) + " owned";
			g.setFont(Theme.small());
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(owned, px + pw - small.stringWidth(owned), line);
			g.setColor(Theme.TEXT_FAINT);
			g.drawString(Ui.clip(eta(coach), small, pw), px, y + h - 7);
		}

		/** "About 6h 40m at Ardougne, your best for marks" or why it cannot be told. */
		static String eta(AgilityCoach coach)
		{
			if (coach.outfitDone() || coach.marksNeeded() == 0)
			{
				return coach.outfitDone() ? "Marks now buy recolours or amylase." : "Visit Grace in the Rogues' Den.";
			}
			AgilityCoach.Course best = coach.getBestMarks();
			double hours = coach.hoursToOutfit(best);
			if (best == null || hours < 0)
			{
				return "Unlock a rooftop course to start.";
			}
			return "About " + Format.duration(Math.round(hours * 3_600_000)) + " at " + best.getCourse().getShortName()
				+ (best.getPaceSource() == AgilityCoach.Source.YOURS ? ", your pace" : ", wiki pace");
		}
	}

	/** One course the player has run: laps, best and typical lap, the recent laps as a line, and the rates. */
	static final class CourseRow extends JComponent
	{
		static final int HEIGHT = 74;
		private final AgilityCoach.Course course;

		CourseRow(AgilityCoach.Course course)
		{
			this.course = course;
			setOpaque(false);
			setPreferredSize(new Dimension(10, HEIGHT));
			AgilityLog.CourseLog log = course.getLog();
			setToolTipText("<html>" + Ui.escape(course.getCourse().getGameName()) + " Course: " + log.getLaps() + " laps timed, "
				+ log.getMarks() + " marks of grace found.<br>Best lap " + LapClock.time(log.getBestTicks())
				+ (log.getBestAt() > 0 ? " on " + Format.day(log.getBestAt()) : "") + ".</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			paint(g, course, 0, 0, getWidth(), HEIGHT);
			g.dispose();
		}

		static void paint(Graphics2D g, AgilityCoach.Course c, int x, int y, int w, int h)
		{
			AgilityLog.CourseLog log = c.getLog();
			plate(g, x, y, w, h, Theme.TEAL);
			int px = x + 10;
			int pw = w - 18;
			g.setFont(Theme.body());
			FontMetrics body = g.getFontMetrics();
			int base = y + 4 + body.getAscent();
			String laps = Format.count(log.getLaps()) + (log.getLaps() == 1 ? " lap" : " laps");
			Ui.shadowed(g, laps, px + pw - body.stringWidth(laps), base, Theme.TEXT_MUTED);
			Ui.shadowed(g, Ui.clip(c.getCourse().getShortName(), body, pw - body.stringWidth(laps) - 6), px, base, Theme.TEXT);

			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			int line = base + small.getHeight() + 1;
			String best = "best " + LapClock.time(log.getBestTicks());
			g.setColor(Theme.GOLD);
			g.drawString(best, px, line);
			String typical = "usual " + LapClock.time(log.typicalTicks());
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(typical, px + small.stringWidth(best) + 8, line);

			int sy = line + 4;
			int sh = 16;
			laps(g, log.getRecent(), log.getBestTicks(), px, sy, pw, sh);

			int foot = y + h - 7;
			String xp = Format.rate(c.getXpPerHour()) + " xp";
			g.setColor(Theme.XP);
			g.drawString(xp, px, foot);
			String marks = c.getMarksSeen() >= 0 ? marks(c.getMarksSeen()) + " marks/hr found"
				: Format.count(log.getMarks()) + (log.getMarks() == 1 ? " mark" : " marks") + ", ~" + marks(c.getMarksExpected()) + "/hr";
			g.setColor(Theme.GOLD);
			g.drawString(Ui.clip(marks, small, pw - small.stringWidth(xp) - 8), px + pw - Math.min(small.stringWidth(marks),
				pw - small.stringWidth(xp) - 8), foot);
		}

		/** Recent laps as small bars, the shortest tallest, the best lap's height as a gold line. */
		static void laps(Graphics2D g, List<Integer> recent, int best, int x, int y, int w, int h)
		{
			g.setColor(Theme.alpha(Theme.INK_DEEP, 200));
			g.fill(new RoundRectangle2D.Float(x, y, w, h, 5, 5));
			if (recent.isEmpty() || best <= 0)
			{
				return;
			}
			int worst = best;
			for (int t : recent)
			{
				worst = Math.max(worst, t);
			}
			// A bar's height is how close the lap came to the best: the best lap fills it, the slowest shown barely shows.
			float slot = (w - 4) / (float) Math.max(recent.size(), 12);
			for (int i = 0; i < recent.size(); i++)
			{
				int t = recent.get(i);
				double close = worst == best ? 1 : 1 - (t - best) / (double) (worst - best) * 0.8;
				float bh = (float) Math.max(2, (h - 4) * close);
				float bx = x + 2 + slot * i;
				g.setColor(t == best ? Theme.GOLD_BRIGHT : Theme.alpha(Theme.TEAL, 170));
				g.fill(new RoundRectangle2D.Float(bx + 0.5f, y + h - 2 - bh, Math.max(1.5f, slot - 1.5f), bh, 2, 2));
			}
		}
	}

	/** The unlocked courses ranked by experience an hour for this player now, with marks an hour beside. */
	static final class RankRow extends JComponent
	{
		static final int LINE = 36;
		private final AgilityCoach coach;
		private final List<AgilityCoach.Course> ranked;

		RankRow(AgilityCoach coach, List<AgilityCoach.Course> ranked)
		{
			this.coach = coach;
			this.ranked = ranked;
			setOpaque(false);
			setPreferredSize(new Dimension(10, height(ranked.size())));
			setToolTipText("<html>Yours: your typical lap here and the experience your laps gave.<br>Wiki: the Old School wiki's "
				+ "best-case experience an hour for the course.<br>Marks an hour: those you found once you have run it 20 minutes, "
				+ "else what the wiki's mark rules give at that pace.</html>");
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
			paint(g, coach, ranked, 0, 0, getWidth(), getHeight());
			g.dispose();
		}

		static void paint(Graphics2D g, AgilityCoach coach, List<AgilityCoach.Course> ranked, int x, int y, int w, int h)
		{
			plate(g, x, y, w, h, Theme.GOLD);
			int px = x + 10;
			int pw = w - 18;
			long top = 1;
			for (AgilityCoach.Course c : ranked)
			{
				top = Math.max(top, c.getXpPerHour());
			}
			AgilityCoach.Course bestMarks = coach.getBestMarks();
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			int ry = y + 6;
			for (int i = 0; i < ranked.size(); i++)
			{
				AgilityCoach.Course c = ranked.get(i);
				boolean first = i == 0;
				boolean yours = c.getPaceSource() == AgilityCoach.Source.YOURS;
				int base = ry + small.getAscent() + 1;
				String name = c.getCourse().getShortName();
				Ui.shadowed(g, name, px, base, first ? Theme.GOLD_BRIGHT : Theme.TEXT);
				int tagX = px + small.stringWidth(name) + 5;
				tag(g, yours ? "yours" : "wiki", tagX, ry + 1, yours ? Theme.TEAL : Theme.TEXT_FAINT);
				String rate = Format.rate(c.getXpPerHour());
				g.setColor(Theme.XP);
				g.drawString(rate, px + pw - small.stringWidth(rate), base);

				int by = ry + 14;
				g.setColor(Theme.INK_DEEP);
				g.fill(new RoundRectangle2D.Float(px, by, pw, 4, 4, 4));
				float fill = (float) Math.max(4, pw * c.getXpPerHour() / (double) top);
				g.setPaint(new GradientPaint(px, 0, Theme.mix(Theme.TEAL, Theme.INK, 0.5), px + fill, 0, first ? Theme.GOLD_BRIGHT : Theme.TEAL));
				g.fill(new RoundRectangle2D.Float(px, by, fill, 4, 4, 4));

				String marks = "~" + marks(c.marksPerHour()) + " marks/hr" + (c.hasPenalty() ? ", -80%" : "");
				g.setColor(c.hasPenalty() ? Theme.EMBER : c == bestMarks ? Theme.GOLD : Theme.TEXT_FAINT);
				g.drawString(marks, px, by + 6 + small.getAscent());
				String most = first && c == bestMarks ? "most xp and marks" : first ? "most xp" : c == bestMarks ? "most marks" : null;
				if (most != null)
				{
					g.setColor(Theme.GOLD_BRIGHT);
					g.drawString(most, px + pw - small.stringWidth(most), by + 6 + small.getAscent());
				}
				ry += LINE;
			}
		}

		static void tag(Graphics2D g, String text, int x, int y, Color color)
		{
			FontMetrics fm = g.getFontMetrics();
			int tw = fm.stringWidth(text) + 6;
			RoundRectangle2D pill = new RoundRectangle2D.Float(x, y, tw, fm.getAscent() + 2, 6, 6);
			g.setColor(Theme.alpha(color, 40));
			g.fill(pill);
			g.setColor(Theme.alpha(color, 160));
			g.draw(pill);
			g.drawString(text, x + 3, y + fm.getAscent());
		}
	}

	/** "12.4" marks an hour. */
	static String marks(double perHour)
	{
		return String.format(Locale.ENGLISH, "%.1f", Math.max(0, perHour));
	}

	/** The rows' shared plate: a lit card with an accent edge. */
	static void plate(Graphics2D g, int x, int y, int w, int h, Color tint)
	{
		RoundRectangle2D plate = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, y, Theme.CARD_TOP, 0, y + h, Theme.CARD));
		g.fill(plate);
		g.setColor(Theme.alpha(tint, 90));
		g.draw(plate);
		g.setColor(tint);
		g.fill(new RoundRectangle2D.Float(x + 1, y + 6, 2, h - 12, 2, 2));
	}
}
