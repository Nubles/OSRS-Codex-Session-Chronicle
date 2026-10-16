package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Journal;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.Summary;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The Journal tab: records, a chart of recent sessions and every session kept, newest first. */
final class JournalView
{
	static final String[] METRICS = {"Experience", "Loot", "Time"};
	private static final int CHART = 14;

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
		JPanel tiles = new JPanel(new GridLayout(2, 2, 6, 6));
		tiles.setOpaque(false);
		tiles.add(new StatTile(Format.duration(records.getWeekPlayedMs()).replace("under a minute", "0m"), "This week",
			records.getWeekSessions() + (records.getWeekSessions() == 1 ? " session" : " sessions"), Theme.TEAL));
		int streak = records.getStreakDays();
		tiles.add(new StatTile(streak + (streak == 1 ? " day" : " days"), "Streak", streak > 1 ? "in a row" : "play again tomorrow", Theme.EMBER));
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
			String label = Format.day(s.getStartedAt()).substring(0, 1);
			bars.add(new BarChart.Bar(label, Format.day(s.getStartedAt()) + ", " + Format.time(s.getStartedAt()) + ": " + shown, value));
		}
		Color color = metric == 0 ? Theme.XP : metric == 1 ? Theme.LOOT : Theme.EMBER;
		stack.add(new BarChart(bars, color, index -> open.accept(count - 1 - index)));

		stack.add(new SectionHeader("Sessions", String.valueOf(sessions.size())));
		for (int i = 0; i < sessions.size(); i++)
		{
			int index = i;
			stack.add(row(sessions.get(i)).onClick(() -> open.accept(index)));
		}
		return stack;
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
		JPanel bottom = new JPanel(new GridLayout(1, 3, 4, 0));
		bottom.setOpaque(false);
		bottom.add(value(s.getTotalXp() > 0 ? Format.xp(s.getTotalXp()) + " xp" : "-", Theme.XP));
		bottom.add(value(s.getLootValue() > 0 ? Format.coins(s.getLootValue()) + " gp" : "-", Theme.LOOT));
		bottom.add(value(s.getKills() > 0 ? s.getKills() + " kills" : "-", Theme.KILLS));
		card.add(bottom, BorderLayout.SOUTH);
		return card;
	}

	private static JLabel value(String text, Color color)
	{
		return Ui.label(text, Theme.small(), color);
	}
}
