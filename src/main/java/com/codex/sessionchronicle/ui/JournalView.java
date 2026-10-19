package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Journal;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.Summary;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The Journal tab: records, a chart of recent sessions and every session kept, newest first. */
final class JournalView
{
	static final String[] METRICS = {"Experience", "Loot", "Time"};
	private static final int CHART = 14;
	private static final int RECORDS = 6;

	private JournalView()
	{
	}

	/**
	 * @param sessions finished sessions, newest first
	 * @param open called with an index into sessions to open one
	 */
	static JComponent build(List<Summary> sessions, Journal.Records records, Segmented metrics, IntConsumer open)
	{
		StackPanel stack = new StackPanel(8, 8);
		Journal.Today today = records.getToday();
		if (today.getSessions() > 0)
		{
			stack.add(new SectionHeader("Today", Format.duration(today.getPlayedMs()).replace("under a minute", "0m")));
			stack.add(today(today));
		}
		JPanel tiles = new JPanel(new GridLayout(2, 2, 6, 6));
		tiles.setOpaque(false);
		tiles.add(new StatTile(Format.duration(records.getWeekPlayedMs()).replace("under a minute", "0m"), "This week",
			records.getWeekSessions() + (records.getWeekSessions() == 1 ? " session" : " sessions"), Theme.TEAL));
		int streak = records.getStreakDays();
		int longest = records.getLongestStreak();
		String streakNote = streak > 1 && streak >= longest ? "longest yet" : longest > Math.max(1, streak) ? "best " + longest + " days"
			: streak == 1 ? "play tomorrow too" : "no play yesterday";
		tiles.add(new StatTile(streak == 0 ? "-" : streak + (streak == 1 ? " day" : " days"), "Streak", streakNote, Theme.EMBER));
		Session xp = records.getMostXp();
		tiles.add(new StatTile(xp == null ? "-" : Format.xp(xp.totalXp()), "Most XP", xp == null ? "" : Format.day(xp.getStartedAt()), Theme.XP));
		Session loot = records.getMostLoot();
		tiles.add(new StatTile(loot == null ? "-" : Format.coins(loot.lootValue()), "Most loot", loot == null ? "" : Format.day(loot.getStartedAt()), Theme.LOOT));
		stack.add(tiles);

		if (sessions.isEmpty())
		{
			stack.add(SessionView.note("The journal is empty", "Each session is filed here when it ends: when you log out and stay away longer than the break allowance in the settings."));
			return stack;
		}

		int metric = metrics.getSelected();
		stack.add(new SectionHeader("Recent sessions", "last " + Math.min(CHART, sessions.size())));
		stack.add(metrics);
		List<BarChart.Bar> bars = new ArrayList<>();
		int count = Math.min(CHART, sessions.size());
		for (int i = count - 1; i >= 0; i--)
		{
			Summary s = sessions.get(i);
			long value = metric == 0 ? s.getTotalXp() : metric == 1 ? s.getLootValue() : s.getPlayedMs();
			String shown = metric == 0 ? Format.xp(value) + " xp" : metric == 1 ? Format.coins(value) + " loot" : Format.duration(value);
			String label = Format.dayOfMonth(s.getStartedAt());
			bars.add(new BarChart.Bar(label, Format.day(s.getStartedAt()) + ", " + Format.time(s.getStartedAt()) + ": " + shown, value));
		}
		Color color = metric == 0 ? Theme.XP : metric == 1 ? Theme.LOOT : Theme.EMBER;
		stack.add(new BarChart(bars, color, index -> open.accept(count - 1 - index)));

		List<Journal.Best> bests = records.getBests();
		if (!bests.isEmpty())
		{
			stack.add(new SectionHeader("Your activities", "pace by session"));
			for (int i = 0; i < bests.size() && i < RECORDS; i++)
			{
				stack.add(best(bests.get(i)));
			}
		}

		stack.add(new SectionHeader("Sessions", String.valueOf(sessions.size())));
		for (int i = 0; i < sessions.size(); i++)
		{
			int index = i;
			stack.add(row(sessions.get(i)).onClick(() -> open.accept(index)));
		}
		return stack;
	}

	/** The day so far: what every session begun today brought, and the skill most of it went to. */
	private static Card today(Journal.Today t)
	{
		Card card = new Card(new BorderLayout(0, 2));
		card.accent(Theme.GOLD);
		JPanel values = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		values.setOpaque(false);
		if (t.getXp() > 0)
		{
			values.add(value(Format.xp(t.getXp()) + " xp", Theme.XP));
		}
		if (t.getLoot() > 0)
		{
			values.add(value(Format.coins(t.getLoot()) + " gp", Theme.LOOT));
		}
		if (t.getKills() > 0)
		{
			values.add(value(t.getKills() + (t.getKills() == 1 ? " kill" : " kills"), Theme.KILLS));
		}
		if (values.getComponentCount() == 0)
		{
			values.add(value("Nothing gained yet", Theme.TEXT_FAINT));
		}
		card.add(values, BorderLayout.NORTH);
		StringBuilder line = new StringBuilder();
		line.append(t.getSessions()).append(t.getSessions() == 1 ? " session" : " sessions");
		String top = t.getTopSkill();
		if (top != null)
		{
			line.append(", most in ").append(top).append(" (").append(Format.xp(t.getTopSkillXp())).append(')');
		}
		if (t.getLevels() > 0)
		{
			line.append(", ").append(t.getLevels()).append(t.getLevels() == 1 ? " level" : " levels");
		}
		if (t.getDeaths() > 0)
		{
			line.append(", ").append(t.getDeaths()).append(t.getDeaths() == 1 ? " death" : " deaths");
		}
		line.append('.');
		card.add(Ui.wrapped(line.toString(), Theme.small(), Theme.TEXT_MUTED, SessionView.WIDTH - 30), BorderLayout.CENTER);
		card.setToolTipText("Every session begun today, the one under way included. A session that runs past midnight counts toward the day it began.");
		return card;
	}

	/** One activity: its latest pace, every session's pace as a line, and the trend. */
	private static Card best(Journal.Best b)
	{
		Card card = new Card(new BorderLayout());
		card.setBorder(new EmptyBorder(6, 10, 4, 10));
		card.accent(PaceRow.color(b.getKind()));
		ActivityRow row = new ActivityRow(b);
		card.add(row, BorderLayout.CENTER);
		card.setToolTipText(row.getToolTipText());
		return card;
	}

	private static Card row(Summary s)
	{
		Card card = new Card(new BorderLayout(0, 2));
		card.accent(s.getLootValue() > s.getTotalXp() / 10 ? Theme.LOOT : Theme.XP);
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(Ui.label(Format.day(s.getStartedAt()) + ", " + Format.time(s.getStartedAt()), Theme.small(), Theme.GOLD), BorderLayout.WEST);
		top.add(Ui.label(Format.duration(s.getPlayedMs()), Theme.small(), Theme.TEXT_MUTED), BorderLayout.EAST);
		card.add(top, BorderLayout.NORTH);
		card.add(Ui.label(s.headline(), Theme.body(), Theme.TEXT), BorderLayout.CENTER);
		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		bottom.setOpaque(false);
		// Only what the session has: a skilling session shows no empty loot and kill columns.
		if (s.getTotalXp() > 0)
		{
			bottom.add(value(Format.xp(s.getTotalXp()) + " xp", Theme.XP));
		}
		if (s.getLootValue() > 0)
		{
			bottom.add(value(Format.coins(s.getLootValue()) + " gp", Theme.LOOT));
		}
		if (s.getKills() > 0)
		{
			bottom.add(value(s.getKills() + (s.getKills() == 1 ? " kill" : " kills"), Theme.KILLS));
		}
		if (bottom.getComponentCount() == 0)
		{
			bottom.add(value("Nothing gained", Theme.TEXT_FAINT));
		}
		card.add(bottom, BorderLayout.SOUTH);
		return card;
	}

	private static JLabel value(String text, Color color)
	{
		JLabel label = Ui.label(text, Theme.small(), color);
		label.setBorder(new EmptyBorder(0, 0, 0, 10));
		return label;
	}
}
