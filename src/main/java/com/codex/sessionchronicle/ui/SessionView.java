package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Goal;
import com.codex.sessionchronicle.core.Moment;
import com.codex.sessionchronicle.core.Pace;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.Summary;
import com.codex.sessionchronicle.core.Trip;
import java.util.ArrayList;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
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
		// The headline is the session's name; a long one wraps rather than losing its end.
		JLabel headline = Ui.wrapped(s.headline(), Theme.title(), Theme.GOLD_BRIGHT, WIDTH - 22);
		lines.add(headline);
		lines.add(Ui.label(Format.duration(s.getPlayedMs()) + " played, " + Format.time(s.getStartedAt()) + " to "
			+ (s.getState() == Summary.State.LIVE ? "now" : Format.time(s.getEndedAt())), Theme.small(), Theme.TEXT_MUTED));
		head.add(lines, BorderLayout.CENTER);
		stack.add(head);

		JPanel tiles = new JPanel(new GridLayout(2, 2, 6, 6));
		tiles.setOpaque(false);
		tiles.add(new StatTile(s.getTotalXp() > 0 ? Format.xp(s.getTotalXp()) : "0", "Experience", Format.rate(s.xpPerHour()),
			s.getTotalXp() > 0 ? Theme.XP : Theme.TEXT_MUTED));
		tiles.add(new StatTile(Format.coins(s.getLootValue()), "Loot", Format.rate(s.lootPerHour()), s.getLootValue() > 0 ? Theme.LOOT : Theme.TEXT_MUTED));
		Session.SkillLine levelled = null;
		for (Session.SkillLine line : s.getSkills())
		{
			if (line.getLevel() > line.getStartLevel() && (levelled == null || line.getLevel() - line.getStartLevel() > levelled.getLevel() - levelled.getStartLevel()))
			{
				levelled = line;
			}
		}
		tiles.add(new StatTile("+" + s.getLevels(), "Levels", levelled == null ? "none this time" : levelled.getSkill() + " " + levelled.getLevel(),
			s.getLevels() > 0 ? Theme.GREEN : Theme.TEXT_MUTED));
		List<Session.KillLine> killLines = s.getKillLines();
		tiles.add(new StatTile(Format.count(s.getKills()), "Kills", killLines.isEmpty() ? "none this time" : killLines.get(0).getName(),
			s.getKills() > 0 ? Theme.KILLS : Theme.TEXT_MUTED));
		stack.add(tiles);

		JComponent worth = worth(s);
		if (worth != null)
		{
			stack.add(worth);
		}

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

		SlayerView.add(stack, s);

		trips(stack, s);

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
			stack.add(new SectionHeader("Kills", Format.count(s.getKills())));
			int top = killLines.get(0).count();
			for (int i = 0; i < killLines.size() && i < KILLS; i++)
			{
				stack.add(new KillRow(killLines.get(i), killLines.get(i).count() / (double) top, icons));
			}
		}

		List<Session.KillLine> rounds = s.getRoundLines();
		if (!rounds.isEmpty())
		{
			// Laps and skilling minigames the game counts like kills; a skiller's rounds, not kills.
			int total = 0;
			for (Session.KillLine line : rounds)
			{
				total += line.count();
			}
			stack.add(new SectionHeader("Laps and rounds", Format.count(total)));
			int top = rounds.get(0).count();
			for (int i = 0; i < rounds.size() && i < KILLS; i++)
			{
				stack.add(new KillRow(rounds.get(i), rounds.get(i).count() / (double) top, icons));
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
				stack.add(new KillRow(opened.get(i), opened.get(i).count() / (double) top, icons));
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

	/** Codex Ledger's net worth over the session, set against the loot so the cost of the trip shows. */
	static JComponent worth(Summary s)
	{
		Long change = s.getWorthChange();
		if (change == null)
		{
			return null;
		}
		Card card = new Card(new BorderLayout(0, 3));
		card.accent(change >= 0 ? Theme.GREEN : Theme.BLOOD);
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(Ui.label("Net worth", Theme.body(), Theme.TEXT), BorderLayout.CENTER);
		top.add(Ui.label(Format.signedCoins(change), Theme.body(), change >= 0 ? Theme.GREEN : Theme.BLOOD), BorderLayout.EAST);
		card.add(top, BorderLayout.NORTH);
		card.add(Ui.wrapped(worthLine(s.getLootValue(), change, s.worthPerHour()), Theme.small(), Theme.TEXT_MUTED, WIDTH - 30),
			BorderLayout.CENTER);
		card.setToolTipText("From Codex Ledger, as it last saved your net worth (every few minutes). It counts everything: loot, "
			+ "supplies used, trades, deaths and prices moving.");
		return card;
	}

	/** "Loot 4M, so about 800k went on supplies and the rest. 1.9M/hr." */
	static String worthLine(long loot, long change, long perHour)
	{
		String rate = perHour != 0 ? " " + Format.signedCoins(perHour) + "/hr." : "";
		long spent = loot - change;
		if (loot <= 0)
		{
			return "Your worth by Codex Ledger since the session began." + rate;
		}
		if (spent > 0)
		{
			return "Loot " + Format.coins(loot) + ", so about " + Format.coins(spent) + " went on supplies and the rest." + rate;
		}
		return "More than the loot's " + Format.coins(loot) + ": sales or prices helped." + rate;
	}

	/** Trips out from the bank: how many, how long, what each brought, and the one under way. */
	static void trips(StackPanel stack, Summary s)
	{
		int count = s.getTripCount();
		if (count <= 0 && s.getOutFor() < 0)
		{
			return;
		}
		stack.add(new SectionHeader("Trips", count <= 0 ? "first one out" : count + (count == 1 ? " trip" : " trips")
			+ ", " + Format.lap(s.averageTripMs()) + " each"));
		List<Trip> trips = s.getTrips();
		if (trips.size() >= 2)
		{
			boolean loot = s.perTrip(Goal.Kind.LOOT) > 0;
			boolean kills = !loot && s.perTrip(Goal.Kind.KILLS) > 0;
			List<BarChart.Bar> bars = new ArrayList<>();
			int first = Math.max(0, trips.size() - 16);
			int number = count - (trips.size() - first) + 1;
			for (int i = first; i < trips.size(); i++, number++)
			{
				Trip t = trips.get(i);
				long value = loot ? t.getLoot() : kills ? t.getKills() : t.getXp();
				String what = loot ? Format.coins(t.getLoot()) + " loot" : kills ? t.getKills() + (t.getKills() == 1 ? " kill" : " kills")
					: Format.xp(t.getXp()) + " xp";
				bars.add(new BarChart.Bar(String.valueOf(number), "Trip " + number + ": " + Format.lap(t.getMs()) + ", " + what, value));
			}
			stack.add(new BarChart(bars, loot ? Theme.LOOT : kills ? Theme.KILLS : Theme.XP, null));
		}
		StringBuilder line = new StringBuilder();
		if (s.getBestTripMs() > 0 && count > 1)
		{
			line.append("Quickest ").append(Format.lap(s.getBestTripMs())).append(".");
		}
		List<String> parts = new ArrayList<>();
		double kills = s.perTrip(Goal.Kind.KILLS);
		if (kills > 0)
		{
			parts.add(String.format(java.util.Locale.ENGLISH, kills >= 10 ? "%.0f kills" : "%.1f kills", kills));
		}
		double loot = s.perTrip(Goal.Kind.LOOT);
		if (loot > 0)
		{
			parts.add(Format.coins(Math.round(loot)) + " loot");
		}
		else if (s.perTrip(Goal.Kind.XP) > 0)
		{
			parts.add(Format.xp(Math.round(s.perTrip(Goal.Kind.XP))) + " xp");
		}
		if (!parts.isEmpty())
		{
			line.append(line.length() > 0 ? " " : "").append(String.join(" and ", parts)).append(" a trip.");
		}
		if (s.getOutFor() >= 0 && s.getState() == Summary.State.LIVE)
		{
			line.append(line.length() > 0 ? " " : "").append("Out for ").append(Format.lap(s.getOutFor())).append(" now.");
		}
		if (line.length() > 0)
		{
			stack.add(Ui.wrapped(line.toString(), Theme.small(), Theme.TEXT_FAINT, WIDTH - 10));
		}
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
