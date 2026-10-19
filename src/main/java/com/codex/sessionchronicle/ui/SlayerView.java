package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.SlayerTask;
import com.codex.sessionchronicle.core.Summary;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The slayer task under way and the ones finished this session, with how long the rest should take. */
final class SlayerView
{
	private SlayerView()
	{
	}

	/** The Slayer section, or nothing when the session saw no task. */
	static void add(StackPanel stack, Summary s)
	{
		SlayerTask task = s.getSlayer();
		List<SlayerTask> done = s.getSlayerDone();
		if (task == null && done.isEmpty())
		{
			return;
		}
		String right = done.isEmpty() ? task.getLeft() + " left"
			: done.size() + (done.size() == 1 ? " task done" : " tasks done");
		stack.add(new SectionHeader("Slayer", right));
		if (task != null)
		{
			stack.add(card(task, s.getState() == Summary.State.LIVE));
		}
		if (!done.isEmpty())
		{
			List<String> names = new ArrayList<>();
			for (int i = done.size() - 1; i >= 0 && names.size() < 4; i--)
			{
				names.add(done.get(i).getTotal() + " " + done.get(i).getName());
			}
			String more = done.size() > names.size() ? " and " + (done.size() - names.size()) + " more" : "";
			stack.add(Ui.wrapped("Done this session: " + String.join(", ", names) + more + ".", Theme.small(), Theme.TEXT_FAINT,
				SessionView.WIDTH - 10));
		}
	}

	private static Card card(SlayerTask task, boolean live)
	{
		Card card = new Card(new BorderLayout(0, 5));
		card.accent(Theme.KILLS);
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		JPanel names = new JPanel();
		names.setOpaque(false);
		names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
		names.add(Ui.label("TASK", Theme.small(), Theme.KILLS));
		JLabel name = Ui.label(task.getName(), Theme.body(), Theme.TEXT);
		name.setToolTipText(task.getName());
		names.add(name);
		head.add(names, BorderLayout.CENTER);
		card.add(head, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		double share = task.progress();
		GoalView.Bar bar = new GoalView.Bar(share, false);
		bar.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		body.add(bar);
		JPanel numbers = new JPanel(new BorderLayout());
		numbers.setOpaque(false);
		numbers.setBorder(new EmptyBorder(3, 0, 0, 0));
		numbers.add(Ui.label((task.getTotal() - task.getLeft()) + " of " + task.getTotal() + " killed", Theme.small(), Theme.TEXT),
			BorderLayout.WEST);
		numbers.add(Ui.label(Math.round(share * 100) + "%", Theme.small(), Theme.progressColor(share)), BorderLayout.EAST);
		numbers.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		body.add(numbers);
		JLabel pace = Ui.label(pace(task, live), Theme.small(), Theme.TEXT_MUTED);
		pace.setBorder(new EmptyBorder(2, 0, 0, 0));
		pace.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		body.add(pace);
		card.add(body, BorderLayout.CENTER);
		card.setToolTipText("<html>Your slayer task as the game counts it. The time left is at this session's pace<br>"
			+ "on it, travel and banking included.</html>");
		return card;
	}

	/** "33 this session, about 52m more" or how to get a pace. */
	static String pace(SlayerTask task, boolean live)
	{
		int here = task.killedHere();
		String killed = here == 0 ? "None yet this session" : here + " this session";
		long eta = task.etaMs();
		if (!live || eta < 0)
		{
			return killed + (live && here > 0 ? ", pace after 3" : "");
		}
		return here + " this session, about " + Format.duration(Math.max(60_000, eta)) + " left";
	}
}
