package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 * {@code chronicle-journal.json} is the journal this plugin's own code writes for two sessions: a
 * Vorkath trip, partly on a Blue dragons task, with Codex Ledger's net worth at either end, then an
 * hour of Mining. Codex Gear Roadmap (where the player fights, and on which task), Codex Ledger
 * (what pays) and Codex Navigator (play time for goal dates) keep byte-for-byte copies of it and read
 * them with their own readers, so a change to
 * what is written here fails this test first, and the copies must be refreshed with it.
 */
public class JournalFixtureTest
{
	static final long T0 = 1_790_000_000_000L;
	private static final long TICK = 600;
	private static final Type JOURNAL = new TypeToken<List<Session>>()
	{
	}.getType();
	private static final List<Session.Drop> NONE = Collections.emptyList();

	private static long play(Chronicle c, long from, long ms)
	{
		long t = from;
		for (long end = from + ms; t < end; )
		{
			t += TICK;
			c.tick(t);
		}
		return t;
	}

	private static LedgerWorth worth(long total)
	{
		List<LedgerWorth.Point> history = new ArrayList<>();
		history.add(new LedgerWorth.Point(T0 - 3_600_000L, total));
		return new LedgerWorth(total, T0 - 86_400_000L, history);
	}

	/** The journal as the plugin writes it: sessions filed through {@link Journal#file}, newest first. */
	static String written()
	{
		Chronicle c = new Chronicle();
		c.begin(T0, "Zezima", 15 * 60_000L);
		c.worth(worth(1_000_000_000L));
		c.xp("Ranged", 13_000_000, 92, T0);
		// Two Vorkath kills on a Blue dragons task, which covers Vorkath, then two with no task.
		c.slayer("Blue dragons", 40, 120, true, T0 + TICK);
		long t = play(c, T0, 10 * 60_000L);
		c.slayer("Blue dragons", 39, 120, false, t);
		c.loot("Vorkath", NONE, 1, false, 0, t);
		c.killCount("Vorkath", 101);
		t = play(c, t, 5 * 60_000L);
		c.slayer("Blue dragons", 38, 120, false, t);
		c.loot("Vorkath", NONE, 1, false, 0, t);
		c.killCount("Vorkath", 102);
		t = play(c, t, 60_000L);
		c.slayer(null, 0, 0, false, t);
		t = play(c, t, 15_000L);
		t = play(c, t, 5 * 60_000L);
		c.loot("Vorkath", NONE, 1, false, 0, t);
		c.killCount("Vorkath", 103);
		t = play(c, t, 5 * 60_000L);
		c.loot("Vorkath", NONE, 1, false, 0, t);
		c.killCount("Vorkath", 104);
		c.xp("Ranged", 13_028_000, 92, t);
		c.worth(worth(1_002_500_000L));
		List<Session> journal = Journal.file(Collections.emptyList(), c.close(), 100);

		long t1 = T0 + 86_400_000L;
		c.begin(t1, "Zezima", 15 * 60_000L);
		c.xp("Mining", 2_000_000, 80, t1);
		t = play(c, t1, 60 * 60_000L);
		c.xp("Mining", 2_060_000, 80, t);
		journal = Journal.file(journal, c.close(), 100);
		return new Gson().toJson(journal, JOURNAL);
	}

	@Test
	public void theJournalWrittenIsTheFixtureOtherPluginsRead() throws Exception
	{
		String fixture;
		try (InputStream in = JournalFixtureTest.class.getResourceAsStream("chronicle-journal.json"))
		{
			assertNotNull(in);
			fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
		}
		assertEquals(fixture, written());
	}
}
