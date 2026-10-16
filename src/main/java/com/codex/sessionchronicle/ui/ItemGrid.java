package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Session;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JComponent;

/**
 * Loot laid out like bank slots: each item in a dark well with its stack written the way the
 * game writes it, and its name and worth on hover.
 */
public class ItemGrid extends JComponent
{
	static final int SLOT_H = 34;
	static final int GAP = 3;

	private final List<Session.Drop> drops;
	private final ItemIcons icons;
	private final int columns;

	public ItemGrid(List<Session.Drop> drops, ItemIcons icons, int columns)
	{
		this.drops = drops;
		this.icons = icons;
		this.columns = columns;
		setOpaque(false);
		int rows = (drops.size() + columns - 1) / columns;
		setPreferredSize(new Dimension(10, Math.max(0, rows * (SLOT_H + GAP) - GAP)));
		setToolTipText("");
	}

	private int slotWidth()
	{
		return Math.max(20, (getWidth() - (columns - 1) * GAP) / columns);
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		int index = indexAt(e.getX(), e.getY());
		if (index < 0)
		{
			return null;
		}
		Session.Drop d = drops.get(index);
		String worth = d.value() > 0 ? ", worth " + Format.coins(d.value()) : "";
		return d.getQuantity() > 1 ? d.getName() + " x " + String.format("%,d", d.getQuantity()) + worth : d.getName() + worth;
	}

	private int indexAt(int x, int y)
	{
		int sw = slotWidth();
		int left = (getWidth() - (columns * (sw + GAP) - GAP)) / 2;
		int col = (x - left) / (sw + GAP);
		int row = y / (SLOT_H + GAP);
		if (x < left || col >= columns)
		{
			return -1;
		}
		int index = row * columns + col;
		return index < drops.size() ? index : -1;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int sw = slotWidth();
		int left = (getWidth() - (columns * (sw + GAP) - GAP)) / 2;
		for (int i = 0; i < drops.size(); i++)
		{
			int x = left + (i % columns) * (sw + GAP);
			int y = (i / columns) * (SLOT_H + GAP);
			Session.Drop d = drops.get(i);
			BufferedImage icon = icons == null ? null : icons.icon(d.getId(), this::repaint);
			paintSlot(g, x, y, sw, SLOT_H, icon, d.getQuantity(), d.value());
		}
		g.dispose();
	}

	/** One slot: the well, the item, and the stack in the game's colours. Shared with the card. */
	static void paintSlot(Graphics2D g, int x, int y, int w, int h, BufferedImage icon, long quantity, long value)
	{
		RoundRectangle2D well = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, 7, 7);
		boolean rich = value >= 1_000_000;
		g.setPaint(new GradientPaint(x, y, Theme.INK_DEEP, x, y + h, Theme.mix(Theme.INK_DEEP, rich ? Theme.GOLD_DIM : Theme.CARD, rich ? 0.45 : 0.6)));
		g.fill(well);
		g.setColor(rich ? Theme.alpha(Theme.GOLD, 130) : Theme.STROKE_SOFT);
		g.draw(well);
		if (icon != null)
		{
			g.drawImage(icon, x + (w - icon.getWidth()) / 2, y + (h - icon.getHeight()) / 2 + 2, null);
		}
		if (quantity > 1)
		{
			g.setFont(Theme.small());
			FontMetrics fm = g.getFontMetrics();
			Ui.shadowed(g, Format.stack(quantity), x + 3, y + fm.getAscent(), Format.stackColor(quantity));
		}
	}
}
