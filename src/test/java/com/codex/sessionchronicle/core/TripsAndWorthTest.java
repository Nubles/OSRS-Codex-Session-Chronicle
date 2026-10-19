package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TripsAndWorthTest
{
	private static final long MIN = 60_000;
	private static final long GAP = 15 * MIN;
	private static final List<Session.Drop> NONE = Collections.emptyList();

	private static void play(Chronicle c, long from, long to)
	{
		for (long t = from; t <= to; t += 600)
		{
			c.tick(t);
		}
	}

	@Test
	public void aTripRunsFromLeavingTheBankToComingBack()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		assertNull("opening the bank with no trip out finishes nothing", c.bank(true));
		c.bank(false);
		play(c, 0, 10 * MIN);
		c.loot("Vorkath", Collections.singletonList(new Session.Drop(995, "Coins", 300_000, 1)), 1, false, 0, 5 * MIN);
		c.loot("Vorkath", NONE, 1, false, 0, 9 * MIN);
		Trip first = c.bank(true);
		assertNotNull(first);
		assertEquals(2, first.getKills());
		assertEquals(300_000, first.getLoot());
		assertEquals(10 * MIN, first.getMs());

		// Closing and opening the bank again to fetch something is no trip.
		c.bank(false);
		play(c, 10 * MIN, 10 * MIN + 6_000);
		assertNull(c.bank(true));

		c.bank(false);
		play(c, 10 * MIN + 6_000, 18 * MIN);
		c.loot("Vorkath", NONE, 1, false, 0, 17 * MIN);
		assertNotNull(c.bank(true));

		Summary s = Summary.of(c.current(), Summary.State.LIVE);
		assertEquals(2, s.getTripCount());
		assertEquals(2, s.getTrips().size());
		assertEquals((10 * MIN + 8 * MIN - 6_000 + 600) / 2, s.averageTripMs(), 1_000);
		assertTrue(s.getBestTripMs() < 10 * MIN);
		assertEquals(1.5, s.perTrip(Goal.Kind.KILLS), 1e-9);
		assertEquals(-1, s.getOutFor());
		c.bank(false);
		play(c, 18 * MIN, 21 * MIN);
		assertEquals(3 * MIN, Summary.of(c.current(), Summary.State.LIVE).getOutFor(), 1_000);
	}

	@Test
	public void theJournalKeepsWhatTripsCameToAndAListStaysBounded()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.xp("Woodcutting", 1_000_000, 70, 0);
		long t = 0;
		long xp = 1_000_000;
		for (int i = 0; i < Chronicle.MAX_TRIPS + 10; i++)
		{
			c.bank(false);
			play(c, t, t + MIN);
			t += MIN;
			xp += 5_000;
			c.xp("Woodcutting", xp, 70, t);
			assertNotNull(c.bank(true));
		}
		Session live = c.current();
		assertEquals(Chronicle.MAX_TRIPS, live.trips.size());
		assertEquals(Chronicle.MAX_TRIPS + 10, live.tripCount);
		Session filed = Journal.compact(live);
		// Every filed session is saved in the journal, so it carries no empty list for trips.
		assertFalse(new Gson().toJson(filed).contains("\"trips\""));
		Summary s = Summary.of(filed, Summary.State.ENDED);
		assertEquals(Chronicle.MAX_TRIPS + 10, s.getTripCount());
		assertEquals(MIN, s.averageTripMs(), 1_000);
	}

	@Test
	public void worthComesFromLedgerAndOnlyWhenItCanBeTrusted()
	{
		long start = 1_790_000_000_000L;
		Chronicle c = new Chronicle();
		c.begin(start, "Zezima", GAP);
		assertFalse("a ledger that has not seen the bank says nothing", c.worth(new LedgerWorth(5_000_000, 0, null)));
		assertNull(c.current().worthChange());

		// A fresh session takes Ledger's total, saved as the player logged out, as its start.
		assertTrue(c.worth(new LedgerWorth(800_000_000, start - 86_400_000L, null)));
		assertEquals(0, (long) c.current().worthChange());
		c.tick(start + 600);
		assertTrue(c.worth(new LedgerWorth(803_200_000, start - 86_400_000L, null)));
		assertEquals(3_200_000, (long) c.current().worthChange());
		assertEquals(3_200_000, (long) Summary.of(c.current(), Summary.State.LIVE).getWorthChange());
		assertEquals(3_200_000, (long) Journal.compact(c.current()).worthChange());

		// A session already under way when Ledger is first read looks its start up in the history...
		Chronicle late = new Chronicle();
		late.begin(start, "Zezima", GAP);
		play(late, start, start + 30 * MIN);
		List<LedgerWorth.Point> history = Arrays.asList(new LedgerWorth.Point(start - 20 * MIN, 790_000_000),
			new LedgerWorth.Point(start - 5 * MIN, 795_000_000), new LedgerWorth.Point(start + 10 * MIN, 797_000_000));
		late.worth(new LedgerWorth(799_000_000, start - 86_400_000L, history));
		assertEquals(4_000_000, (long) late.current().worthChange());

		// ...and goes without when the history does not reach back to it.
		Chronicle none = new Chronicle();
		none.begin(start, "Zezima", GAP);
		play(none, start, start + 30 * MIN);
		none.worth(new LedgerWorth(799_000_000, start + 10 * MIN, history));
		assertNull(none.current().worthChange());
	}

	@Test
	public void ledgersSavedShapeIsReadAsLedgerWritesIt()
	{
		// Pinned against Codex Ledger's own saved form: top-level total, since and history [{t, v}].
		String saved = "{\"stores\":{\"BANK\":{\"seen\":1,\"items\":{\"995\":5}}},\"prices\":{\"995\":1},"
			+ "\"history\":[{\"t\":100,\"v\":40},{\"t\":200,\"v\":45}],\"days\":[],\"total\":45,\"since\":100,"
			+ "\"exchange\":{},\"book\":{},\"slotsKnown\":true,\"lost\":{},\"lostUntil\":0}";
		LedgerWorth w = new Gson().fromJson(saved, LedgerWorth.class);
		assertTrue(w.isReady());
		assertEquals(45, w.getTotal());
		assertEquals(40, w.at(150));
		assertEquals(-1, w.at(50));
	}
}
