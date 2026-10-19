package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Summary;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.Path2D;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The crest at the top of the panel: the session clock, the player, and ledgers for experience, loot and kills. */
public class Crest extends JPanel
{
	private final SessionClock clock = new SessionClock(74);
	private final JLabel title = Ui.label("Session Chronicle", Theme.title(), Theme.GOLD_BRIGHT);
	private final JLabel subtitle = Ui.label("Log in to begin", Theme.small(), Theme.TEXT_MUTED);
	private final Ledger xp = new Ledger("Experience");
	private final Ledger loot = new Ledger("Loot");
	private final Ledger kills = new Ledger("Kills");
	private Color glow = Theme.GOLD_DIM;

	public Crest()
	{
		super(new BorderLayout(10, 0));
		setOpaque(false);
		setBorder(new EmptyBorder(12, 10, 12, 10));

		JPanel clockHolder = new JPanel(new BorderLayout());
		clockHolder.setOpaque(false);
		clockHolder.add(clock, BorderLayout.NORTH);
		add(clockHolder, BorderLayout.WEST);

		JPanel right = new JPanel();
		right.setOpaque(false);
		right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
		title.setAlignmentX(LEFT_ALIGNMENT);
		subtitle.setAlignmentX(LEFT_ALIGNMENT);
		right.add(title);
		right.add(subtitle);
		JPanel ledgers = new JPanel(new GridLayout(3, 1, 0, 2));
		ledgers.setOpaque(false);
		ledgers.setBorder(new EmptyBorder(4, 0, 0, 0));
		ledgers.add(xp);
		ledgers.add(loot);
		ledgers.add(kills);
		ledgers.setAlignmentX(LEFT_ALIGNMENT);
		right.add(ledgers);
		add(right, BorderLayout.CENTER);
		update(null, null);
	}

	/** Shows a session, or the waiting state when there is none. */
	public void update(Summary s, String playerName)
	{
		title.setText(playerName != null ? playerName : s != null && s.getPlayer() != null ? s.getPlayer() : "Session Chronicle");
		if (s == null)
		{
			subtitle.setText(playerName == null ? "Log in to begin" : "Beginning your session...");
			subtitle.setForeground(Theme.TEXT_MUTED);
			clock.set(0, "", Theme.TEXT_MUTED, false);
			xp.set("-", Theme.TEXT);
			loot.set("-", Theme.TEXT);
			kills.set("-", Theme.TEXT);
			glow = Theme.GOLD_DIM;
			repaint();
			return;
		}
		Color state;
		String caption;
		switch (s.getState())
		{
			case LIVE:
				state = Theme.GREEN;
				caption = "LIVE";
				subtitle.setText("Live since " + Format.time(s.getStartedAt()));
				break;
			case PAUSED:
				state = Theme.GOLD;
				caption = "PAUSED";
				subtitle.setText("Paused at " + Format.time(s.getEndedAt()));
				break;
			default:
				state = Theme.TEXT_MUTED;
				caption = "ENDED";
				subtitle.setText("From " + Format.day(s.getStartedAt()));
				break;
		}
		subtitle.setForeground(Theme.mix(Theme.TEXT_MUTED, state, 0.35));
		clock.set(s.getPlayedMs(), caption, state, true);
		xp.set(s.getTotalXp() > 0 ? Format.xp(s.getTotalXp()) : "-", Theme.XP);
		loot.set(s.getLootValue() > 0 ? Format.coins(s.getLootValue()) : "-", Theme.LOOT);
		kills.set(s.getKills() > 0 ? Format.count(s.getKills()) : "-", Theme.KILLS);
		glow = Theme.mix(Theme.GOLD_DIM, state, 0.3);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.INK, glow, 0.2), 0, h, Theme.INK));
		g.fillRect(0, 0, w, h);
		int y = h - 2;
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.GOLD, 0), w / 2f, 0, Theme.alpha(Theme.GOLD, 150), true));
		g.drawLine(0, y, w, y);
		Path2D diamond = new Path2D.Float();
		diamond.moveTo(w / 2f, y - 3);
		diamond.lineTo(w / 2f + 3, y);
		diamond.lineTo(w / 2f, y + 3);
		diamond.lineTo(w / 2f - 3, y);
		diamond.closePath();
		g.setColor(Theme.GOLD);
		g.fill(diamond);
		g.dispose();
		super.paintComponent(graphics);
	}

	/** One labelled value. */
	private static final class Ledger extends JPanel
	{
		private final JLabel value = Ui.label("-", Theme.small(), Theme.TEXT);

		Ledger(String label)
		{
			super(new BorderLayout());
			setOpaque(false);
			add(Ui.label(label, Theme.small(), Theme.TEXT_MUTED), BorderLayout.WEST);
			add(value, BorderLayout.EAST);
			setPreferredSize(new Dimension(10, 15));
		}

		void set(String text, Color color)
		{
			value.setText(text);
			value.setForeground(color);
		}
	}
}
