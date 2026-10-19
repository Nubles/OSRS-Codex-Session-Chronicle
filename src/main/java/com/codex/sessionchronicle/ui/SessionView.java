package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Goal;
import com.codex.sessionchronicle.core.Moment;
import com.codex.sessionchronicle.core.Pace;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.Summary;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.BiConsumer;
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
	 * @param remove takes the open older session out of the journal; null for the live one
	 * @param end closes the live session now; null for an older one
	 */
	static JComponent build(Summary s, ItemIcons items, Icons icons, Runnable backToLive, Runnable remove, Runnable end)
	{
		return build(s, items, icons, backToLive, remove, end, null, null, null);
	}

	/**
	 * @param goalKinds the strip that picks which kind of goal to offer
	 * @param setGoal sets a goal on the live session; null for an older one
	 * @param clearGoal clears the live session's goal; null for an older one
	 */
	static JComponent build(Summary s, ItemIcons items, Icons icons, Runnable backToLive, Runnable remove, Runnable end,
		Segmented goalKinds, BiConsumer<Goal.Kind, Long> setGoal, Runnable clearGoal)
	{
		StackPanel stack = new StackPanel(8, 8);
		if (backToLive != null)
		{
			Card banner = new Card(new BorderLayout(0, 6));
			banner.accent(Theme.GOLD_DIM);
			banner.add(Ui.label("From the journal", Theme.small(), Theme.TEXT_MUTED), BorderLayout.NORTH);
			JPanel buttons = new JPanel(new BorderLayout(6, 0));
			buttons.setOpaque(false);
			buttons.add(new CodexButton("Back to live", false, backToLive), BorderLayout.WEST);
			if (remove != null)
			{
				buttons.add(CodexButton.confirming("Remove", "Remove it?", remove), BorderLayout.EAST);
			}
			banner.add(buttons, BorderLayout.CENTER);
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
		tiles.add(new StatTile("+" + s.getLevels(), "Levels", levelled == null ? "none this time" : levelled.getSkill() + " " + levelled.getLevel(), Theme.GREEN));
		List<Session.KillLine> killLines = s.getKillLines();
		tiles.add(new StatTile(String.valueOf(s.getKills()), "Kills", killLines.isEmpty() ? "" : killLines.get(0).getName(), Theme.KILLS));
		stack.add(tiles);

		JComponent goal = goalKinds == null && s.getGoal() == null ? null : GoalView.build(s, goalKinds, setGoal, clearGoal);
		if (goal != null)
		{
			stack.add(goal);
		}

		List<Pace.Line> pace = s.getPace();
		if (!pace.isEmpty())
		{
			stack.add(new SectionHeader("Pace", "vs your past"));
			for (Pace.Line line : pace)
			{
				stack.add(new PaceRow(line));
			}
		}

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

		List<Session.KillLine> opened = s.getOpenedLines();
		if (!opened.isEmpty())
		{
			// Nests, crates and caskets are no kills; they are listed apart so the Kills figure stays honest.
			int total = 0;
			for (Session.KillLine line : opened)
			{
				total += line.count();
			}
			stack.add(new SectionHeader("Opened", String.valueOf(total)));
			int top = opened.get(0).count();
			for (int i = 0; i < opened.size() && i < KILLS; i++)
			{
				stack.add(new KillRow(opened.get(i), opened.get(i).count() / (double) top));
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

		if (end != null && s.getState() != Summary.State.ENDED)
		{
			stack.space(4);
			JPanel row = new JPanel(new BorderLayout(8, 0));
			row.setOpaque(false);
			row.add(Ui.wrapped("Changing activity? File this session now.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 90),
				BorderLayout.CENTER);
			JPanel holder = new JPanel(new BorderLayout());
			holder.setOpaque(false);
			holder.add(CodexButton.confirming("End session", "End it now?", end), BorderLayout.NORTH);
			row.add(holder, BorderLayout.EAST);
			stack.add(row);
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
