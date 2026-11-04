package com.codex.sessionchronicle.overlay;

import com.codex.sessionchronicle.core.DiveWatch;
import com.codex.sessionchronicle.core.UnderwaterCoach;
import com.codex.sessionchronicle.core.UnderwaterLog;
import com.codex.sessionchronicle.ui.Format;
import com.codex.sessionchronicle.ui.Theme;
import com.codex.sessionchronicle.ui.Ui;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The optional dive plaque in the game view: the tears found this dive against its clock, the pace
 * an hour, and the reward being saved for with the time to go at the player's own pace. Clams,
 * chests and tears are not highlighted: it only keeps the record. It draws what the plugin works out
 * on the client thread each tick, so rendering never touches game state.
 */
public class DivePlaque extends Overlay
{
	private static final int PAD = 7;
	private static final int MIN_WIDTH = 128;

	private volatile UnderwaterCoach coach;
	private volatile boolean enabled;

	public DivePlaque()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/** The dive to show, or null for none. Only a coach with a dive under way is drawn. */
	public void show(UnderwaterCoach coach)
	{
		this.coach = coach;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/** What is shown now, for tests. */
	public UnderwaterCoach shown()
	{
		UnderwaterCoach c = coach;
		return enabled && c != null && c.getLive() != null ? c : null;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		UnderwaterCoach c = shown();
		return c == null ? null : draw(g, c);
	}

	/** Draws the plaque at the origin; the screenshot test uses the same drawing. */
	public static Dimension draw(Graphics2D g, UnderwaterCoach c)
	{
		Theme.smooth(g);
		UnderwaterLog.Dive d = c.getLive();
		FontMetrics bold = g.getFontMetrics(Theme.title());
		FontMetrics small = g.getFontMetrics(Theme.small());
		String tears = Format.count(d.getTears()) + (d.getTears() == 1 ? " tear" : " tears");
		String clock = Format.lap(d.getMs());
		String pace = d.getTicks() < DiveWatch.MIN_TICKS ? "Timing your pace"
			: String.format(Locale.ENGLISH, "%,d an hour", Math.round(d.tearsPerHour()));
		String goal = goal(c);
		int top = bold.stringWidth(tears) + 12 + bold.stringWidth(clock);
		int text = Math.max(small.stringWidth(pace), goal.isEmpty() ? 0 : small.stringWidth(goal));
		int width = PAD + Math.min(230, Math.max(Math.max(top, text), MIN_WIDTH)) + PAD;
		int height = PAD + bold.getAscent() + small.getHeight() + (goal.isEmpty() ? 0 : small.getHeight()) + PAD - 2;
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 230), 0, height, Theme.alpha(Theme.INK_DEEP, 230)));
		g.fill(shape);
		g.setColor(Theme.alpha(Theme.TEAL, 170));
		g.draw(shape);
		g.setFont(Theme.title());
		int base = PAD + bold.getAscent() - 2;
		Ui.shadowed(g, tears, PAD, base, Theme.GOLD_BRIGHT);
		Ui.shadowed(g, clock, width - PAD - bold.stringWidth(clock), base, Theme.TEXT);
		g.setFont(Theme.small());
		int line = base + small.getHeight();
		Ui.shadowed(g, Ui.clip(pace, small, width - 2 * PAD), PAD, line, Theme.TEAL);
		if (!goal.isEmpty())
		{
			line += small.getHeight();
			Ui.shadowed(g, Ui.clip(goal, small, width - 2 * PAD), PAD, line, c.tearsNeeded() == 0 ? Theme.GREEN : Theme.TEXT_MUTED);
		}
		return new Dimension(width, height);
	}

	/** "Merfolk trident: 150 to go, 41m" or "" with no reward chosen. */
	static String goal(UnderwaterCoach c)
	{
		if (c.getReward() == null)
		{
			return "";
		}
		String name = c.getReward().getShortName();
		if (c.tearsNeeded() == 0)
		{
			return name + ": enough tears";
		}
		double hours = c.hoursToReward();
		return name + ": " + Format.count(c.tearsNeeded()) + " to go" + (hours > 0 ? ", " + Format.duration(Math.max(60_000, Math.round(hours * 3_600_000))) : "");
	}
}
