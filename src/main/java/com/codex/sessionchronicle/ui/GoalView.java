package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Goal;
import com.codex.sessionchronicle.core.Summary;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * The session's goal: a bar toward it and when it should be reached at the pace so far, or,
 * for a live session without one, a few goals to pick with one click, drawn from the player's
 * own usual and best sessions.
 */
final class GoalView
{
	static final String[] KINDS = {"XP", "Loot", "Kills", "Time"};

	private GoalView()
	{
	}

	/**
	 * @param set sets a goal on the live session; null for a session from the journal
	 * @param clear clears the live session's goal; null for a session from the journal
	 * @return the card, or null when there is nothing to show
	 */
	static JComponent build(Summary s, Segmented kinds, BiConsumer<Goal.Kind, Long> set, Runnable clear)
	{
		Goal goal = s.getGoal();
		if (goal != null)
		{
			return progress(s, goal, clear);
		}
		if (set == null || s.getPresets().isEmpty())
		{
			return null;
		}
		Card card = new Card(new BorderLayout(0, 6));
		card.accent(Theme.TEAL);
		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.add(Ui.label("Set a goal", Theme.body(), Theme.GOLD), BorderLayout.WEST);
		head.add(Ui.label("for this session", Theme.small(), Theme.TEXT_FAINT), BorderLayout.EAST);
		card.add(head, BorderLayout.NORTH);
		JPanel body = new JPanel(new BorderLayout(0, 6));
		body.setOpaque(false);
		body.add(kinds, BorderLayout.NORTH);
		Goal.Kind kind = Goal.Kind.values()[Math.max(0, Math.min(Goal.Kind.values().length - 1, kinds.getSelected()))];
		List<Goal.Preset> presets = s.getPresets().get(kind);
		JPanel row = new JPanel(new GridLayout(1, 3, 5, 0));
		row.setOpaque(false);
		for (Goal.Preset p : presets)
		{
			JPanel column = new JPanel(new BorderLayout(0, 2));
			column.setOpaque(false);
			JLabel why = Ui.label(p.getLabel() == null ? " " : p.getLabel(), Theme.small(), p.getLabel() == null ? Theme.TEXT_FAINT : Theme.TEXT_MUTED);
			why.setHorizontalAlignment(SwingConstants.CENTER);
			column.add(why, BorderLayout.NORTH);
			CodexButton button = new CodexButton(shortly(kind, p.getValue()), false, () -> set.accept(kind, p.getValue()));
			button.setToolTipText(tip(kind, p));
			button.setPreferredSize(new Dimension(10, 22));
			button.setMinimumSize(new Dimension(10, 22));
			button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
			column.add(button, BorderLayout.CENTER);
			row.add(column);
		}
		if (presets == null || presets.isEmpty())
		{
			// A six-hour session is past every time on offer; say so rather than show an empty row.
			body.add(Ui.label("Already past every goal on offer", Theme.small(), Theme.TEXT_FAINT), BorderLayout.CENTER);
		}
		else
		{
			body.add(row, BorderLayout.CENTER);
		}
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	private static Card progress(Summary s, Goal goal, Runnable clear)
	{
		boolean done = goal.isReached();
		Card card = new Card(new BorderLayout(0, 5));
		card.accent(done ? Theme.TEAL : Theme.GOLD);
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		JPanel names = new JPanel();
		names.setOpaque(false);
		names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
		names.add(Ui.label(done ? "GOAL REACHED" : "GOAL", Theme.small(), done ? Theme.TEAL : Theme.GOLD));
		names.add(Ui.label(goal.describe(), Theme.body(), Theme.TEXT));
		head.add(names, BorderLayout.CENTER);
		if (clear != null && s.getState() != Summary.State.ENDED)
		{
			JPanel holder = new JPanel(new BorderLayout());
			holder.setOpaque(false);
			holder.add(new CodexButton(done ? "New goal" : "Clear", false, clear), BorderLayout.NORTH);
			head.add(holder, BorderLayout.EAST);
		}
		card.add(head, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		double share = share(s, goal);
		Bar bar = new Bar(share, done);
		bar.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		body.add(bar);
		JPanel numbers = new JPanel(new BorderLayout());
		numbers.setOpaque(false);
		numbers.setBorder(new EmptyBorder(3, 0, 0, 0));
		numbers.add(Ui.label(shortly(goal.getKind(), Math.min(s.getGoalProgress(), goal.getTarget())) + " of "
			+ shortly(goal.getKind(), goal.getTarget()), Theme.small(), done ? Theme.TEAL : Theme.TEXT), BorderLayout.WEST);
		numbers.add(Ui.label(Math.round(share * 100) + "%", Theme.small(), Theme.progressColor(share)), BorderLayout.EAST);
		numbers.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		body.add(numbers);
		JLabel when = Ui.label(when(s, goal), Theme.small(), done ? Theme.TEAL : Theme.TEXT_MUTED);
		when.setBorder(new EmptyBorder(2, 0, 0, 0));
		when.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		body.add(when);
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	static double share(Summary s, Goal goal)
	{
		return goal.isReached() ? 1 : Math.max(0, Math.min(1, s.getGoalProgress() / (double) goal.getTarget()));
	}

	/** When the goal was or should be reached, in a few words. */
	static String when(Summary s, Goal goal)
	{
		if (goal.isReached())
		{
			return "Reached after " + Format.duration(goal.getReachedMs()) + " of play";
		}
		if (s.getState() == Summary.State.ENDED)
		{
			return "Not reached this session";
		}
		long eta = s.goalEtaMs();
		if (eta < 0)
		{
			return "A pace to go by shows after five minutes";
		}
		return goal.getKind() == Goal.Kind.TIME ? Format.duration(eta).replace("under a minute", "Under a minute") + " to go"
			: "About " + Format.duration(Math.max(60_000, eta)) + " more at this pace";
	}

	/** "500k xp", "5M gp", "50 kills", "2h": short enough for a button. */
	static String shortly(Goal.Kind kind, long value)
	{
		switch (kind)
		{
			case LOOT:
				return Format.amount(value) + " gp";
			case KILLS:
				return Format.amount(value) + (value == 1 ? " kill" : " kills");
			case TIME:
				return Format.duration(value).replace(" 0m", "");
			default:
				return Format.amount(value) + " xp";
		}
	}

	private static String tip(Goal.Kind kind, Goal.Preset p)
	{
		String what = Goal.describe(kind, p.getValue());
		if ("Usual".equals(p.getLabel()))
		{
			return what + ": about what a session of yours usually brings";
		}
		if ("Best".equals(p.getLabel()))
		{
			return what + ": your best session yet";
		}
		if ("Beat best".equals(p.getLabel()))
		{
			return what + ": a little past your best session";
		}
		return what;
	}

	/** A slim bar that fills from ember to teal, green once the goal is reached. */
	private static final class Bar extends JComponent
	{
		private final double share;
		private final boolean done;

		Bar(double share, boolean done)
		{
			this.share = share;
			this.done = done;
			setOpaque(false);
			setPreferredSize(new Dimension(10, 6));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			g.setColor(Theme.INK_DEEP);
			g.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
			if (share > 0)
			{
				float fill = (float) Math.max(h, w * share);
				Color end = done ? Theme.GREEN : Theme.progressColor(share);
				g.setPaint(new GradientPaint(0, 0, done ? Theme.GREEN : Theme.progressColor(0), fill, 0, end));
				g.fill(new RoundRectangle2D.Float(0, 0, fill, h, h, h));
			}
			g.setColor(Theme.alpha(Theme.STROKE, 160));
			g.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, h, h));
			g.dispose();
		}
	}
}
