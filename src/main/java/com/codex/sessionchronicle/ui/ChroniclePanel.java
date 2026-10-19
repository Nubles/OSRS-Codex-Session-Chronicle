package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Journal;
import com.codex.sessionchronicle.core.Pace;
import com.codex.sessionchronicle.core.Summary;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/**
 * The Session Chronicle side panel: the crest, then three pages. Session is the session being
 * lived (or one opened from the journal), Journal is every session kept with its records, and
 * Card is the session as a picture to save or copy.
 */
public class ChroniclePanel extends PluginPanel
{
	private static final String[] TABS = {"session", "journal", "card"};

	private final ItemIcons items;
	private final Icons icons;
	private final CardActions actions;
	private final SessionActions sessionActions;
	private final Crest crest = new Crest();
	private final Segmented tabs = new Segmented(true, "Session", "Journal", "Card");
	private final Segmented metrics = new Segmented(false, JournalView.METRICS);
	private final Segmented goalKinds = new Segmented(false, GoalView.KINDS);
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane session = scroller();
	private final JScrollPane journal = scroller();
	private final JScrollPane card = scroller();

	private Summary live;
	private List<Summary> past = Collections.emptyList();
	private Journal.Records records;
	private String playerName;
	private boolean showName = true;
	private int open = -1;
	private int tab;
	private String cardNote;
	private BufferedImage cardImage;
	/** Set when data arrived while the panel was closed; it is drawn once the panel opens. */
	private boolean stale;
	/** What the Session and Card tabs last drew, so an unchanged session is not built again. */
	private String sessionDrawn;
	private String cardDrawn;

	public ChroniclePanel(ItemIcons items, Icons icons, CardActions actions, SessionActions sessionActions)
	{
		super(false);
		this.items = items;
		this.icons = icons;
		this.actions = actions;
		this.sessionActions = sessionActions;
		setLayout(new BorderLayout());
		setBackground(Theme.INK);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(crest, BorderLayout.NORTH);
		JPanel tabHolder = new JPanel(new BorderLayout());
		tabHolder.setOpaque(false);
		tabHolder.setBorder(new EmptyBorder(8, 8, 2, 8));
		tabHolder.add(tabs, BorderLayout.CENTER);
		north.add(tabHolder, BorderLayout.SOUTH);
		add(north, BorderLayout.NORTH);

		content.setOpaque(false);
		content.add(session, TABS[0]);
		content.add(journal, TABS[1]);
		content.add(card, TABS[2]);
		add(content, BorderLayout.CENTER);

		tabs.onChange(this::showTab);
		metrics.onChange(i -> rebuildJournal());
		goalKinds.onChange(i -> rebuildSession(true));
		rebuild();
	}

	private static JScrollPane scroller()
	{
		JScrollPane scroll = new JScrollPane();
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		return scroll;
	}

	/**
	 * Called on the Swing thread whenever the chronicle changes.
	 *
	 * @param live the session being lived, or null
	 * @param past finished sessions, newest first
	 */
	public void update(Summary live, List<Summary> past, Journal.Records records, String playerName)
	{
		boolean journalChanged = past != this.past;
		if (journalChanged && open >= 0)
		{
			// The journal moved under the open session; find it again by when it started.
			long started = this.past.get(open).getStartedAt();
			open = -1;
			for (int i = 0; i < past.size(); i++)
			{
				if (past.get(i).getStartedAt() == started)
				{
					open = i;
				}
			}
		}
		this.live = live;
		this.past = past;
		this.records = records;
		this.playerName = playerName;
		if (!isShowing())
		{
			// Nothing is drawn while the panel is closed; it catches up when it opens.
			stale = true;
			return;
		}
		refresh(journalChanged || stale);
	}

	@Override
	public void onActivate()
	{
		if (stale)
		{
			refresh(true);
		}
	}

	private void refresh(boolean journalChanged)
	{
		stale = false;
		crest.update(shown(), playerName);
		if (open < 0)
		{
			rebuildSession(false);
		}
		if (journalChanged)
		{
			rebuildJournal();
		}
		if (tab == 2)
		{
			rebuildCard(false);
		}
	}

	/** Builds the Session tab again, unless what it shows has not changed. */
	private void rebuildSession(boolean force)
	{
		Summary s = shown();
		String drawn = (open >= 0 ? "past:" : "live:") + Summary.signature(s);
		if (!force && drawn.equals(sessionDrawn))
		{
			return;
		}
		sessionDrawn = drawn;
		replace(session, open >= 0 ? SessionView.build(s, items, icons, this::backToLive, this::removeOpen, null)
			: SessionView.build(s, items, icons, null, null, sessionActions::endSession, goalKinds, sessionActions::setGoal,
				sessionActions::clearGoal));
	}

	private void removeOpen()
	{
		if (open >= 0 && open < past.size())
		{
			sessionActions.removeSession(past.get(open).getStartedAt());
			backToLive();
		}
	}

	public void setShowName(boolean showName)
	{
		this.showName = showName;
		if (tab == 2)
		{
			rebuildCard(true);
		}
	}

	/** A line under the card's buttons, such as where it was saved. */
	public void cardNote(String note)
	{
		cardNote = note;
		rebuildCard(true);
	}

	/** Opens one tab, for previews and tests. */
	public void showTab(int index)
	{
		tab = index;
		tabs.setSelected(index, false);
		cards.show(content, TABS[index]);
		if (index == 2)
		{
			cardNote = null;
			rebuildCard(true);
		}
	}

	/** Opens a session from the journal on the Session tab. */
	public void openPast(int index)
	{
		if (index < 0 || index >= past.size())
		{
			return;
		}
		open = index;
		crest.update(shown(), playerName);
		rebuildSession(true);
		showTab(0);
		SwingUtilities.invokeLater(() -> session.getVerticalScrollBar().setValue(0));
	}

	private void backToLive()
	{
		open = -1;
		crest.update(shown(), playerName);
		rebuildSession(true);
	}

	private Summary shown()
	{
		return open >= 0 && open < past.size() ? past.get(open) : live;
	}

	private void rebuild()
	{
		crest.update(shown(), playerName);
		rebuildSession(true);
		rebuildJournal();
	}

	private void rebuildJournal()
	{
		if (records == null)
		{
			replace(journal, SessionView.note("The journal is empty", "Sessions are filed here as they end."));
			return;
		}
		replace(journal, JournalView.build(past, records, metrics, this::openPast));
	}

	private void rebuildCard(boolean force)
	{
		Summary s = shown();
		if (s == null && !past.isEmpty())
		{
			s = past.get(0);
		}
		// The card is a large picture; only paint it again when the session on it has moved on.
		String drawn = s == null ? "none" : showName + "/" + s.getState() + "/" + s.getStartedAt() + "/" + s.getPlayedMs() / 60_000
			+ "/" + Format.xp(s.getTotalXp()) + "/" + Format.coins(s.getLootValue()) + "/" + s.getKills() + "/" + s.getLevels()
			+ "/" + s.getMoments().size() + "/" + s.getDrops().size() + "/" + s.getDeaths() + "/" + open
			+ "/" + (s.getGoal() == null ? "-" : s.getGoal().describe() + s.getGoal().isReached() + Math.round(GoalView.share(s, s.getGoal()) * 100))
			+ "/" + paceKey(s);
		if (!force && drawn.equals(cardDrawn))
		{
			return;
		}
		cardDrawn = drawn;
		StackPanel stack = new StackPanel(8, 8);
		if (s == null)
		{
			stack.add(SessionView.note("Nothing to share yet", "Once your session has some experience, loot or kills in it, its card appears here, ready to save or copy."));
			replace(card, stack);
			return;
		}
		Summary shownSession = s;
		cardImage = ShareCard.render(s, showName, items, icons, () -> SwingUtilities.invokeLater(this::refreshCardImage));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
		buttons.setOpaque(false);
		buttons.add(new CodexButton("Save card", true, () -> actions.save(cardImage, shownSession)));
		buttons.add(new CodexButton("Copy to clipboard", false, () -> actions.copy(cardImage)));
		stack.add(new SectionHeader(open >= 0 ? "From the journal" : s.getState() == Summary.State.LIVE ? "Live session" : "Last session",
			Format.day(s.getStartedAt())));
		stack.add(new CardPreview(cardImage));
		stack.add(buttons);
		String note = cardNote != null ? cardNote : "Save card asks where to put the picture. Copy puts it on the clipboard, ready to paste.";
		stack.add(Ui.wrapped(note, Theme.small(), cardNote != null ? Theme.TEAL : Theme.TEXT_FAINT, SessionView.WIDTH - 10));
		replace(card, stack);
	}

	/** The pace lines as the card shows them, rounded so a moving rate does not repaint it every refresh. */
	private static String paceKey(Summary s)
	{
		StringBuilder key = new StringBuilder();
		for (Pace.Line line : s.getPace())
		{
			key.append(Format.amount(line.getRate())).append(',');
		}
		return key.toString();
	}

	private boolean refreshPending;

	/** Item pictures arrive after the first render; redraw once they are in. */
	private void refreshCardImage()
	{
		if (refreshPending || tab != 2)
		{
			return;
		}
		refreshPending = true;
		SwingUtilities.invokeLater(() ->
		{
			refreshPending = false;
			rebuildCard(true);
		});
	}

	private static void replace(JScrollPane scroll, JComponent view)
	{
		int position = scroll.getVerticalScrollBar().getValue();
		scroll.setViewportView(view);
		scroll.revalidate();
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(position));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		g.setColor(Theme.INK);
		g.fillRect(0, 0, getWidth(), getHeight());
	}
}
