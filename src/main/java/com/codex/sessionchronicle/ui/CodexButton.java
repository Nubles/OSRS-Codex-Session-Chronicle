package com.codex.sessionchronicle.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/** A gilt-edged button; the primary style is filled, the quiet style is just an outline. */
public class CodexButton extends JComponent
{
	/** How long a button that asks for a second click waits for it. */
	private static final int CONFIRM_MS = 3_000;

	private String text;
	private final boolean primary;
	private boolean hover;
	private boolean pressed;
	private Color warn;

	public CodexButton(String text, boolean primary, Runnable action)
	{
		this.text = text;
		this.primary = primary;
		setFont(Theme.small());
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		size(text);
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = false;
				pressed = false;
				repaint();
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				pressed = true;
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				boolean fire = pressed && contains(e.getPoint());
				pressed = false;
				repaint();
				if (fire)
				{
					action.run();
				}
			}
		});
	}

	/**
	 * A quiet button for something that cannot be undone: the first click asks again in its own
	 * label, and only a second click within a few seconds does it.
	 */
	public static CodexButton confirming(String text, String confirm, Runnable action)
	{
		CodexButton[] self = new CodexButton[1];
		Timer reset = new Timer(CONFIRM_MS, e -> self[0].arm(null, text));
		reset.setRepeats(false);
		self[0] = new CodexButton(text, false, () ->
		{
			if (self[0].warn == null)
			{
				self[0].arm(Theme.BLOOD, confirm);
				reset.restart();
			}
			else
			{
				reset.stop();
				self[0].arm(null, text);
				action.run();
			}
		});
		// Keep the button the width of its longer label so the row does not jump.
		FontMetrics fm = self[0].getFontMetrics(self[0].getFont());
		self[0].size(fm.stringWidth(confirm) > fm.stringWidth(text) ? confirm : text);
		return self[0];
	}

	private void arm(Color color, String label)
	{
		warn = color;
		text = label;
		repaint();
	}

	private void size(String widest)
	{
		FontMetrics fm = getFontMetrics(getFont());
		Dimension d = new Dimension(fm.stringWidth(widest) + 20, 22);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		int dy = pressed ? 1 : 0;
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f + dy, w - 1, h - 2, 8, 8);
		Color text;
		if (primary)
		{
			Color top = hover ? Theme.GOLD_BRIGHT : Theme.GOLD;
			g.setPaint(new GradientPaint(0, 0, top, 0, h, Theme.GOLD_DIM));
			g.fill(shape);
			text = Theme.INK_DEEP;
		}
		else
		{
			Color tint = warn != null ? warn : Theme.GOLD;
			g.setColor(hover ? Theme.alpha(tint, 40) : Theme.alpha(tint, 14));
			g.fill(shape);
			text = warn != null ? Theme.mix(warn, Theme.TEXT, 0.35) : hover ? Theme.GOLD_BRIGHT : Theme.GOLD;
		}
		g.setColor(Theme.alpha(warn != null ? warn : Theme.GOLD, primary ? 220 : 140));
		g.draw(shape);
		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		g.setColor(text);
		g.drawString(this.text, (w - fm.stringWidth(this.text)) / 2, (h + fm.getAscent()) / 2 - 2 + dy);
		g.dispose();
	}
}
