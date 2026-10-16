package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Moment;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.Summary;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The Session tab: the session's headline and totals, then its skills, loot, kills and moments. */
final class SessionView
{
	static final int WIDTH = 200;
	private static final int SKILLS = 8;
	private static final int DROPS = 20;
	private static final int KILLS = 8;

	private SessionView()
	{
	}

	/**
	 * @param backToLive shown as a button when an older session is open; null for the live one
	 */
	static JComponent build(Summary s, ItemIcons items, Icons icons, Runnable backToLive)
	{
		StackPanel stack = new StackPanel(8, 8);
		if (backToLive != null)
		{
			Card banner = new Card(new BorderLayout(6, 0));
			banner.accent(Theme.GOLD_DIM);
			banner.add(Ui.label("From the journal", Theme.small(), Theme.TEXT_MUTED), BorderLayout.CENTER);
			banner.add(new CodexButton("Back to live", false, backToLive), BorderLayout.EAST);
			stack.add(banner);
		}
		if (s == null)
		{
			stack.add(note("No session yet", "Log in and Session Chronicle starts writing. Every skill you train, every drop and every kill lands here, and the whole session can be saved as a card to share."));
			return stack;
		}

		Card head = new Card(new BorderLayout());
		head.highlighted(true);
		JPanel lines = new JPanel();
		lines.setOpaque(false);
		lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
		JLabel headline = Ui.label(s.headline(), Theme.title(), Theme.GOLD_BRIGHT);
		lines.add(headline);
		lines.add(Ui.label(Format.duration(s.getPlayedMs()) + " played, " + Format.time(s.getStartedAt()) + " to "
			+ (s.getState() == Summary.State.LIVE ? "now" : Format.time(s.getEndedAt())), Theme.small(), Theme.TEXT_MUTED));
		head.add(lines, BorderLayout.CENTER);
		stack.add(head);

		JPanel tiles = new JPanel(new GridLayout(2, 2, 6, 6));
		tiles.setOpaque(false);
		tiles.add(new StatTile(s.getTotalXp() > 0 ? Format.xp(s.getTotalXp()) : "0", "Experience", Format.rate(s.xpPerHour()), Theme.XP));
		tiles.add(new StatTile(Format.coins(s.getLootValue()), "Loot", Format.rate(s.lootPerHour()), Theme.LOOT));
		Session.SkillLine levelled = null;
		for (Session.SkillLine line : s.getSkills())
		{
			if (line.getLevel() > line.getStartLevel() && (levelled == null || line.getLevel() - line.getStartLevel() > levelled.getLevel() - levelled.getStartLevel()))
			{
				levelled = line;
			}
		}
		tiles.add(new StatTile("+" + s.getLevels(), "Levels", levelled == null ? "" : levelled.getSkill() + " " + levelled.getLevel(), Theme.GREEN));
		List<Session.KillLine> killLines = s.getKillLines();
		tiles.add(new StatTile(String.valueOf(s.getKills()), "Kills", killLines.isEmpty() ? "" : killLines.get(0).getName(), Theme.KILLS));
		stack.add(tiles);

		List<Session.SkillLine> skills = s.getSkills();
		if (!skills.isEmpty())
		{
			stack.add(new SectionHeader("Experience", Format.rate(s.xpPerHour())));
			long top = skills.get(0).gained();
			for (int i = 0; i < skills.size() && i < SKILLS; i++)
			{
				Session.SkillLine line = skills.get(i);
				long rate = s.getPlayedMs() < 5 * 60_000L ? 0 : Math.round(line.gained() * 3_600_000.0 / s.getPlayedMs());
				stack.add(new SkillRow(line, icons == null ? null : icons.skill(line.getSkill()), line.gained() / (double) top, rate));
			}
			if (skills.size() > SKILLS)
			{
				stack.add(Ui.label("and " + (skills.size() - SKILLS) + " more skills", Theme.small(), Theme.TEXT_FAINT));
			}
		}

		List<Session.Drop> drops = s.getDrops();
		if (!drops.isEmpty())
		{
			stack.add(new SectionHeader("Loot", Format.coins(s.getLootValue())));
			stack.add(new ItemGrid(drops.subList(0, Math.min(DROPS, drops.size())), items, 5));
			int more = drops.size() - DROPS + s.getOtherDrops();
			if (more > 0)
			{
				stack.add(Ui.label("and " + more + " more kinds of item", Theme.small(), Theme.TEXT_FAINT));
			}
		}

		if (!killLines.isEmpty())
		{
			stack.add(new SectionHeader("Kills", String.valueOf(s.getKills())));
			int top = killLines.get(0).count();
			for (int i = 0; i < killLines.size() && i < KILLS; i++)
			{
				stack.add(new KillRow(killLines.get(i), killLines.get(i).count() / (double) top));
			}
		}

		List<Moment> moments = s.getMoments();
		if (!moments.isEmpty())
		{
			stack.add(new SectionHeader("Moments", String.valueOf(moments.size())));
			JPanel timeline = new JPanel(new GridLayout(0, 1, 0, 0));
			timeline.setOpaque(false);
			for (int i = 0; i < moments.size(); i++)
			{
				Moment m = moments.get(i);
				BufferedImage icon = m.getItemId() > 0 && items != null ? items.icon(m.getItemId(), timeline::repaint) : null;
				timeline.add(new MomentRow(m, icon, i == 0, i == moments.size() - 1));
			}
			stack.add(timeline);
		}
		return stack;
	}

	static Card note(String title, String text)
	{
		Card card = new Card(new BorderLayout(0, 4));
		card.accent(Theme.GOLD_DIM);
		card.add(Ui.label(title, Theme.body(), Theme.GOLD), BorderLayout.NORTH);
		card.add(Ui.wrapped(text, Theme.small(), Theme.TEXT_MUTED, WIDTH - 30), BorderLayout.CENTER);
		return card;
	}
}
