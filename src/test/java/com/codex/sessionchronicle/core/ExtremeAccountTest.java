package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The accounts at the ends, timed on what Session Chronicle does on the client thread each time it
 * shows a change: copying the open session, summing it against the journal, the records and the
 * week. A maxed account with a full journal of 100 rich sessions and an open session at every cap,
 * Codex Ledger's book at 10,000 points, and a brand-new account. Best of several runs, printed.
 */
public class ExtremeAccountTest
{
	private static final Gson GSON = new GsonBuilder().create();
	private static final ZoneId ZONE = ZoneOffset.UTC;
	private static final long HOUR = 3_600_000L;
	private static final long NOW = 1_790_000_000_000L;
	private static final double BUDGET_MS = 10;
	private static final String[] SKILLS = {"Attack", "Strength", "Defence", "Ranged", "Prayer", "Magic", "Runecraft",
		"Construction", "Hitpoints", "Agility", "Herblore", "Thieving", "Crafting", "Fletching", "Slayer", "Hunter", "Mining",
		"Smithing", "Fishing", "Cooking", "Firemaking", "Woodcutting", "Farming", "Sailing"};

	/** A session at every cap a maxed player could fill: every skill, hundreds of drops and kill lines, a full log. */
	static Session rich(long start, int drops, int lines, int moments)
	{
		Session s = new Session(start, "Zezima");
		for (String skill : SKILLS)
		{
			Session.SkillLine line = new Session.SkillLine(skill, 199_000_000L, 99);
			line.xp = 200_000_000L;
			s.skills.put(skill, line);
		}
		for (int i = 0; i < drops; i++)
		{
			s.loot.put(1_000 + i, new Session.Drop(1_000 + i, "Item " + i, 1 + i, 1_000L * i));
		}
		for (int i = 0; i < lines; i++)
		{
			Session.KillLine k = new Session.KillLine("Monster " + i);
			k.looted = 10 + i;
			s.kills.put("monster " + i, k);
		}
		for (int i = 0; i < moments; i++)
		{
			s.moments.add(new Moment(i % 7 == 0 ? Moment.Kind.RECORD : Moment.Kind.DROP, start + i * 60_000L,
				"Monster " + (i % 30) + " record: " + i, "", 0, 1_000_000L * i));
		}
		for (int i = 0; i < 200; i++)
		{
			s.trips.add(new Trip(start + i * 60_000L, start + i * 60_000L + 50_000L, 3, 100_000L, 20_000L));
		}
		s.playedMs = 6 * HOUR;
		s.lastSeenAt = start + s.playedMs;
		s.endedAt = s.lastSeenAt;
		return s;
	}

	@Test
	public void aMaxedAccountWithAFullJournalShowsItsChangesQuickly()
	{
		List<Session> journal = new ArrayList<>();
		for (int i = 100; i >= 1; i--)
		{
			journal = Journal.file(journal, rich(NOW - i * 7 * HOUR, 300, 60, 200), 100);
		}
		assertEquals(100, journal.size());
		// The journal as saved and read back at login.
		String saved = GSON.toJson(journal);
		List<Session> read = Journal.read(GSON, saved);
		assertEquals(100, read.size());
		double login = best(() -> Journal.read(GSON, saved));
		Session open = rich(NOW - HOUR, 1_500, 200, Chronicle.MAX_MOMENTS);
		List<Session> j = read;
		double[] parts = new double[4];
		parts[0] = best(() -> GSON.fromJson(GSON.toJson(open), Session.class));
		parts[1] = best(() -> Summary.of(open, Summary.State.LIVE, j));
		parts[2] = best(() -> Journal.records(j, open, NOW, ZONE));
		parts[3] = best(() -> Week.of(j, open, NOW, ZONE, null));
		print("maxed, 100 sessions, " + saved.length() + " characters", login, parts[0], parts[1], parts[2], parts[3]);
		assertTrue("copy " + parts[0], parts[0] < BUDGET_MS);
		assertTrue("summary " + parts[1], parts[1] < BUDGET_MS);
		assertTrue("records " + parts[2], parts[2] < BUDGET_MS);
		assertTrue("week " + parts[3], parts[3] < BUDGET_MS);
		// Read once at login, a journal far larger than one filed by this version (filing trims each session).
		assertTrue("login " + login, login < 10 * BUDGET_MS);
		DamagedSavesTest.use(saved, GSON.toJson(open), null);
	}

	@Test
	public void ledgersBookAtTenThousandPoints()
	{
		StringBuilder ledger = new StringBuilder("{\"total\":5000000000,\"since\":1,\"history\":[");
		for (int i = 0; i < 10_000; i++)
		{
			ledger.append(i == 0 ? "" : ",").append("{\"t\":").append(NOW - (10_000 - i) * 600_000L).append(",\"v\":")
				.append(4_000_000_000L + i * 100_000L).append('}');
		}
		ledger.append("],\"days\":[]}");
		String json = ledger.toString();
		LedgerWorth[] book = new LedgerWorth[1];
		double read = best(() -> book[0] = LedgerWorth.read(GSON, json));
		double week = best(() -> Week.of(new ArrayList<>(), null, NOW, ZONE, book[0]));
		double at = best(() -> book[0].at(NOW - 3 * HOUR));
		print("10,000 points", read, week, at);
		assertTrue(book[0].isReady());
		assertNotNull(Week.of(new ArrayList<>(), null, NOW, ZONE, book[0]));
		// Read when Ledger saves, every few minutes; eight times the points Ledger ever keeps.
		assertTrue("read " + read, read < 3 * BUDGET_MS);
		assertTrue("week " + week, week < BUDGET_MS);
		assertTrue("at " + at, at < BUDGET_MS);
	}

	@Test
	public void aBrandNewAccountHasAnEmptyJournal()
	{
		List<Session> journal = Journal.read(GSON, null);
		assertTrue(journal.isEmpty());
		Session first = new Session(NOW, "Zezima");
		Week week = Week.of(journal, first, NOW, ZONE, LedgerWorth.read(GSON, "{\"total\":25000,\"since\":0,\"history\":[]}"));
		assertTrue(week.getHighlights().isEmpty());
		assertEquals(null, week.highlightsNote());
		Journal.records(journal, first, NOW, ZONE);
		Summary.of(first, Summary.State.LIVE, journal);
		DamagedSavesTest.use(null, GSON.toJson(first), null);
	}

	private static double best(Runnable work)
	{
		// Warmed well first: a test machine's first runs are the compiler's, not the plugin's.
		for (int i = 0; i < 25; i++)
		{
			work.run();
		}
		// Then sampled in a few rounds a moment apart, so a shared test machine busy for a while (a
		// build beside it, a collection) shows the work's own time rather than its neighbours'.
		double best = Double.MAX_VALUE;
		for (int round = 0; round < 4; round++)
		{
			for (int i = 0; i < 7; i++)
			{
				long t = System.nanoTime();
				work.run();
				best = Math.min(best, (System.nanoTime() - t) / 1e6);
			}
			pause();
		}
		return best;
	}

	private static void pause()
	{
		try
		{
			Thread.sleep(25);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	private static void print(String what, double... ms)
	{
		StringBuilder line = new StringBuilder(what).append(":");
		for (double m : ms)
		{
			line.append(String.format(Locale.ENGLISH, " %.2f ms", m));
		}
		System.out.println(line);
	}
}
