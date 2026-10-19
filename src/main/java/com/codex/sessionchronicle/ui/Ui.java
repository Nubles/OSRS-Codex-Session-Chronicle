package com.codex.sessionchronicle.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import javax.swing.JLabel;

/** Small helpers shared by the panel's components. */
public final class Ui
{
	private Ui()
	{
	}

	public static JLabel label(String text, Font font, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	/**
	 * A label that wraps inside the side panel's narrow width. Swing's HTML renderer
	 * treats CSS pixels as points, so the width is converted to keep it in screen pixels.
	 */
	public static JLabel wrapped(String text, Font font, Color color, int width)
	{
		int css = Math.round(width * 0.75f);
		return label("<html><div style='width:" + css + "px'>" + escape(text) + "</div></html>", font, color);
	}

	public static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Lets a clickable card keep its hover and click while the pointer is over its children. */
	static void forwardMouse(Container root, MouseAdapter adapter)
	{
		for (Component child : root.getComponents())
		{
			child.addMouseListener(adapter);
			if (child instanceof Container)
			{
				forwardMouse((Container) child, adapter);
			}
		}
	}

	/** Text with the one pixel black shadow the game puts under its own lettering. */
	public static void shadowed(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	/**
	 * Breaks text into lines that fit a width, at spaces, keeping at most a number of lines; the
	 * last one kept ends with an ellipsis when the text runs on. For pictures drawn by hand.
	 */
	public static java.util.List<String> lines(String text, java.awt.FontMetrics fm, int width, int max)
	{
		java.util.List<String> out = new java.util.ArrayList<>();
		if (text == null || text.isEmpty())
		{
			return out;
		}
		String[] words = text.split(" ");
		StringBuilder line = new StringBuilder();
		int i = 0;
		while (i < words.length)
		{
			String next = line.length() == 0 ? words[i] : line + " " + words[i];
			if (fm.stringWidth(next) <= width || line.length() == 0)
			{
				line.setLength(0);
				line.append(next);
				i++;
				continue;
			}
			if (out.size() == max - 1)
			{
				break;
			}
			out.add(line.toString());
			line.setLength(0);
		}
		if (i < words.length)
		{
			StringBuilder rest = new StringBuilder(line);
			for (; i < words.length; i++)
			{
				rest.append(' ').append(words[i]);
			}
			out.add(clip(rest.toString(), fm, width));
		}
		else if (line.length() > 0)
		{
			out.add(clip(line.toString(), fm, width));
		}
		return out;
	}

	/** Cuts text to fit a width, ending it with an ellipsis. */
	public static String clip(String text, java.awt.FontMetrics fm, int width)
	{
		if (text == null)
		{
			return "";
		}
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + "...") > width)
		{
			end--;
		}
		return text.substring(0, end) + "...";
	}
}
