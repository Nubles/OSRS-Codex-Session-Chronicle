package com.codex.sessionchronicle.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/** The card shrunk to the panel's width, smoothly, with a soft shadow under it. */
class CardPreview extends JComponent
{
	private final BufferedImage card;
	private Image scaled;
	private int scaledWidth;

	CardPreview(BufferedImage card)
	{
		this.card = card;
		setOpaque(false);
		setPreferredSize(new Dimension(SessionView.WIDTH, SessionView.WIDTH * card.getHeight() / card.getWidth() + 4));
		setToolTipText("Your session card, as it will be saved");
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		// Fit the card inside the box, whichever of width or height runs out first.
		int w = Math.min(getWidth() - 2, (getHeight() - 4) * card.getWidth() / card.getHeight());
		if (w <= 0)
		{
			g.dispose();
			return;
		}
		int left = (getWidth() - w) / 2;
		if (scaled == null || scaledWidth != w)
		{
			scaled = shrink(card, w);
			scaledWidth = w;
		}
		g.setColor(Theme.alpha(java.awt.Color.BLACK, 90));
		g.fillRoundRect(left + 2, 4, w - 2, scaled.getHeight(null) - 2, 10, 10);
		g.drawImage(scaled, left, 0, null);
		g.dispose();
	}

	/** Halves the image step by step so the shrunk card keeps its detail. */
	private static Image shrink(BufferedImage image, int width)
	{
		BufferedImage current = image;
		while (current.getWidth() / 2 >= width)
		{
			current = resize(current, current.getWidth() / 2);
		}
		return current.getWidth() == width ? current : resize(current, width);
	}

	private static BufferedImage resize(BufferedImage image, int width)
	{
		int height = Math.max(1, image.getHeight() * width / image.getWidth());
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(image, 0, 0, width, height, null);
		g.dispose();
		return out;
	}
}
