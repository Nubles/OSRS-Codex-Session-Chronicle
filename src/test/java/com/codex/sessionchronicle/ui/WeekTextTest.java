package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Chronicle;
import com.codex.sessionchronicle.core.Session;
import com.codex.sessionchronicle.core.Week;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The week as plain text, its headline and date range, and the line breaking the cards use. */
public class WeekTextTest
{
	private static final long MIN = 60_000L;

	private static long at(int month, int day, int hour)
	{
		return LocalDateTime.of(2026, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
	}

	private static Session mining(long start, int minutes)
	{
		Chronicle c = new Chronicle();
		c.begin(start, "Zezima", 15 * MIN);
		c.xp("Mining", 1_000_000, 70, start);
		for (int i = 1; i <= minutes * 20; i++)
		{
			c.tick(start + i * 3_000L);
		}
		c.xp("Mining", 1_150_000, 71, start + minutes * MIN);
		return c.close();
	}

	@Test
	public void tellsTheWeekInAFewLines()
	{
		List<Session> journal = Arrays.asList(mining(at(10, 15, 10), 60), mining(at(10, 18, 10), 90));
		Week w = Week.of(journal, null, at(10, 19, 12), ZoneOffset.UTC, null);
		String text = Recap.week(w, true);
		String[] lines = text.split("\n");
		assertEquals("Zezima's week, 13 to 19 October", lines[0]);
		assertEquals("A week of Mining, 2h 30m played over 2 sessions", lines[1]);
		assertTrue(text, text.contains("+300k xp"));
		assertTrue(text, text.contains("Mostly: Mining 2h 30m"));
		assertTrue(Recap.week(w, false).startsWith("My week, "));
	}

	@Test
	public void setsTheWeekAgainstTheOneBeforeOnlyWhenTheJournalReachesBack()
	{
		Session older = mining(at(10, 1, 10), 30);
		Session lastWeek = mining(at(10, 8, 10), 60);
		List<Session> thisWeek = Arrays.asList(mining(at(10, 15, 10), 60), mining(at(10, 18, 10), 90));
		List<Session> journal = new java.util.ArrayList<>(thisWeek);
		journal.add(lastWeek);
		journal.add(older);
		Week w = Week.of(journal, null, at(10, 19, 12), ZoneOffset.UTC, null);
		assertEquals(1, w.getBefore().getSessions());
		assertEquals("On the week before: +1h 30m played, xp +100%", WeekCard.against(w));
		assertTrue(Recap.week(w, true).contains("On the week before: +1h 30m played, xp +100%\n"));
		// With nothing older than the week before, it may only be where the journal began: no comparison.
		List<Session> begun = new java.util.ArrayList<>(thisWeek);
		begun.add(lastWeek);
		assertEquals(null, WeekCard.against(Week.of(begun, null, at(10, 19, 12), ZoneOffset.UTC, null)));
		// A week off before this one says so.
		List<Session> rested = new java.util.ArrayList<>(thisWeek);
		rested.add(older);
		assertEquals("The week before had no play.", WeekCard.against(Week.of(rested, null, at(10, 19, 12), ZoneOffset.UTC, null)));
		// Less play reads as less.
		Week quieter = Week.of(Arrays.asList(mining(at(10, 15, 10), 30), lastWeek, older), null, at(10, 19, 12), ZoneOffset.UTC, null);
		assertEquals("On the week before: -30m played, xp even", WeekCard.against(quieter));
	}

	@Test
	public void aWeekAcrossAMonthsEndNamesBothMonths()
	{
		Week w = Week.of(Collections.singletonList(mining(at(11, 2, 10), 30)), null, at(11, 3, 12), ZoneOffset.UTC, null);
		assertEquals("28 October to 3 November", WeekCard.range(w));
		assertEquals("A week of Mining", WeekCard.headline(w));
	}

	@Test
	public void columnLabelsStayShort()
	{
		assertEquals("45m", WeekCard.hours(45 * MIN));
		assertEquals("2h", WeekCard.hours(120 * MIN));
		assertEquals("2h 5m", WeekCard.hours(125 * MIN));
		assertEquals("12h", WeekCard.hours(12 * 60 * MIN + 40 * MIN));
	}

	@Test
	public void linesBreakAtSpacesAndEndWithAnEllipsisWhenCut()
	{
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		FontMetrics fm = scratch.createGraphics().getFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, 10));
		int w = fm.stringWidth("From Chambers of");
		List<String> two = Ui.lines("From Chambers of Xeric: Challenge Mode, worth 1.65B", fm, w, 2);
		assertEquals(2, two.size());
		assertEquals("From Chambers of", two.get(0));
		assertTrue(two.get(1), two.get(1).endsWith("..."));
		assertEquals(Collections.singletonList("Short"), Ui.lines("Short", fm, w, 2));
		assertEquals(Arrays.asList("From Chambers of", "Xeric"), Ui.lines("From Chambers of Xeric", fm, w, 3));
		assertTrue(Ui.lines("", fm, w, 2).isEmpty());
	}
}
