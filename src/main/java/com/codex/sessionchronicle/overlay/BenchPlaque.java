package com.codex.sessionchronicle.overlay;

import com.codex.sessionchronicle.core.Bench;
import com.codex.sessionchronicle.ui.BenchRow;
import com.codex.sessionchronicle.ui.Format;
import com.codex.sessionchronicle.ui.Theme;
import com.codex.sessionchronicle.ui.Ui;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The optional batch plaque in the game view: what is being made, how many more the pack holds and the
 * time left at the player's own pace, the load as a thin bar, and the pack and bank together. It only
 * informs: it sends no notification and makes no sound (Codex Guardian's idle rule does that). It draws
 * what the plugin works out on the client thread, so rendering never touches game state.
 */
public class BenchPlaque extends Overlay
{
	private static final int PAD = 7;
	private static final int MIN_WIDTH = 132;

	private volatile Bench.Progress progress;
	private volatile boolean enabled;

	public BenchPlaque()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/** The batch to show, or null for none. */
	public void show(Bench.Progress progress)
	{
		this.progress = progress;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/** What is shown now, for tests: a batch under way, or a load just finished; never one paused part way. */
	public Bench.Progress shown()
	{
		Bench.Progress p = progress;
		return enabled && p != null && (p.isRunning() || p.getLeft() <= 0) ? p : null;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Bench.Progress p = shown();
		return p == null ? null : draw(g, p);
	}

	/** Draws the plaque at the origin; the screenshot test uses the same drawing. */
	public static Dimension draw(Graphics2D g, Bench.Progress p)
	{
		Theme.smooth(g);
		FontMetrics bold = g.getFontMetrics(Theme.title());
		FontMetrics small = g.getFontMetrics(Theme.small());
		String head = p.getLeft() > 0 ? Format.count(p.getLeft()) + " left" : "Load done";
		String time = p.isRunning() && p.getLoadMs() >= 0 ? Format.lap(p.getLoadMs()) : "";
		String name = p.getName();
		String under = under(p);
		int top = bold.stringWidth(head) + (time.isEmpty() ? 0 : 12 + bold.stringWidth(time));
		int width = PAD + Math.min(220, Math.max(Math.max(top, Math.max(small.stringWidth(name), small.stringWidth(under))), MIN_WIDTH)) + PAD;
		int height = PAD + small.getHeight() + bold.getAscent() + 4 + 5 + 4 + small.getHeight() + PAD - 4;
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 230), 0, height, Theme.alpha(Theme.INK_DEEP, 230)));
		g.fill(shape);
		g.setColor(Theme.alpha(Theme.GOLD, 170));
		g.draw(shape);

		g.setFont(Theme.small());
		int y = PAD + small.getAscent() - 2;
		Ui.shadowed(g, Ui.clip(name, small, width - 2 * PAD), PAD, y, Theme.GOLD);
		g.setFont(Theme.title());
		y += bold.getAscent() + 2;
		Ui.shadowed(g, head, PAD, y, p.getLeft() > 0 ? Theme.TEXT : Theme.TEXT_MUTED);
		if (!time.isEmpty())
		{
			Ui.shadowed(g, time, width - PAD - bold.stringWidth(time), y, p.getLeft() == 1 ? Theme.EMBER : Theme.TEAL);
		}
		y += 5;
		BenchRow.bar(g, p, PAD, y, width - 2 * PAD, 5);
		y += 5 + 3 + small.getAscent();
		g.setFont(Theme.small());
		Ui.shadowed(g, Ui.clip(under, small, width - 2 * PAD), PAD, y, Theme.TEXT_MUTED);
		return new Dimension(width, height);
	}

	/** The quiet line under the bar: the last one, the pace still being learnt, or the pack and bank together. */
	static String under(Bench.Progress p)
	{
		if (p.isRunning() && p.getLeft() == 1)
		{
			return "Last one in the pack";
		}
		if (p.getLoadMs() < 0 && p.isRunning())
		{
			return "Learning your pace";
		}
		if (p.getTotal() < 0)
		{
			return "Bank not seen yet";
		}
		if (p.getTotal() <= p.getLeft())
		{
			return "None left in the bank";
		}
		return "Pack and bank " + Format.count(p.getTotal()) + (p.getTotalMs() >= 0 ? ", " + Format.duration(p.getTotalMs()) : "");
	}
}
