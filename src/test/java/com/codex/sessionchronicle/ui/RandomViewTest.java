package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.RandomEvents;
import com.codex.sessionchronicle.core.RandomLog;
import com.codex.sessionchronicle.core.RandomReport;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** The words the random event sections use. */
public class RandomViewTest
{
	private static long at(int hour, int minute)
	{
		return ZonedDateTime.of(2026, 11, 4, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	@Test
	public void nextEventWindowIsTheWikisTimer()
	{
		RandomLog log = new RandomLog();
		assertEquals("Your first event starts the clock", RandomView.next(RandomReport.of(log, at(12, 0), null)));
		log.file(new RandomLog.Seen(RandomEvents.Event.GENIE, at(13, 32), null, RandomLog.Outcome.DONE, null));
		assertEquals("Next from 14:32, due by 15:32", RandomView.next(RandomReport.of(log, at(14, 0), null)));
		assertEquals("Could come now, due by 15:32", RandomView.next(RandomReport.of(log, at(15, 0), null)));
		assertEquals("Overdue: likely missed in a bank or minigame", RandomView.next(RandomReport.of(log, at(16, 0), null)));
		assertEquals("Genie is under way", RandomView.next(RandomReport.of(log, at(16, 0), RandomEvents.Event.GENIE)));
	}

	@Test
	public void rateAndRewardWords()
	{
		assertEquals("-", RandomView.every(-1));
		assertEquals("1 every 1h 30m", RandomView.every(5_400_000L));
		RandomLog log = new RandomLog();
		assertEquals("Watching from now on", RandomView.versus(RandomReport.of(log, 0, null)));
		log.played("Mining", 3_600_000L);
		assertEquals("None in 1h 0m of play", RandomView.versus(RandomReport.of(log, 0, null)));
		log.file(new RandomLog.Seen(RandomEvents.Event.EVIL_TWIN, 1, null, RandomLog.Outcome.DONE,
			Arrays.asList(new RandomLog.Gain(ItemID.UNCUT_SAPPHIRE + 1, "Uncut sapphire", 3), new RandomLog.Gain(ItemID.KEBAB, "Kebab", 1))));
		assertEquals("Early: 1h 0m of play so far", RandomView.versus(RandomReport.of(log, 0, null)));
		assertEquals("3 x Uncut sapphire, Kebab", RandomView.reward(log.getEvents().get(0)));
		log.played("Mining", 3_600_000L);
		assertEquals("Within the wiki's timer", RandomView.versus(RandomReport.of(log, 0, null)));
	}

	@Test
	public void costumeLines()
	{
		RandomLog log = new RandomLog();
		log.sawPieces(Arrays.asList(ItemID.MACRO_MIME_MASK, ItemID.MACRO_MIME_TOP));
		RandomReport r = RandomReport.of(log, Instant.now().toEpochMilli(), null);
		// Seven completions for five pieces, three missing: about five more.
		assertEquals("Mime: about 5 more", RandomView.costumeLine(r.getCostumes().get(RandomEvents.Costume.MIME.ordinal())));
		log.sawPieces(Arrays.asList(ItemID.DRILL_HELM, ItemID.DRILL_TOP, ItemID.DRILL_BOTTOMS));
		r = RandomReport.of(log, 0, null);
		assertEquals("Complete", RandomView.costumeLine(r.getCostumes().get(RandomEvents.Costume.CAMOUFLAGE.ordinal())));
	}
}
