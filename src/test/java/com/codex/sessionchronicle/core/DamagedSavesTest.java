package com.codex.sessionchronicle.core;

import com.codex.sessionchronicle.ui.Recap;
import com.codex.sessionchronicle.ui.ShareCard;
import com.codex.sessionchronicle.ui.WeekCard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What Session Chronicle reads back from the profile, its journal and the session saved while open
 * ({@code codexsessionchronicle/journal} and {@code current}) and Codex Ledger's book, must survive
 * whatever is found there: empty, cut short, the wrong type, numbers too big or below zero, a later
 * version's fields, or garbage. None of it may throw, and one broken session must cost only itself:
 * the journal is saved again with the next session, so whatever is dropped at reading is gone.
 */
public class DamagedSavesTest
{
	private static final Gson GSON = new GsonBuilder().create();
	private static final JsonParser PARSER = new JsonParser();
	private static final ZoneId ZONE = ZoneOffset.UTC;
	private static final long HOUR = 3_600_000L;
	private static final long DAY = 24 * HOUR;
	private static final long NOW = 1_790_000_000_000L;
	private static final String[] BAD = {
		"null", "\"\"", "\"garbage\"", "true", "0", "-1", "-9223372036854775808", "9223372036854775807",
		"1e30", "1.5", "[]", "[null]", "[\"x\"]", "{}", "{\"x\":1}", "[{}]", "{\"a\":null}",
	};
	private static final String[] WHOLE = {
		null, "", " ", "null", "{", "}", "[", "]", "42", "\"text\"", "true", "garbage{{", "\u0000",
	};
	/** Every field a saved session has, top level. */
	private static final String[] FIELDS = {
		"startedAt", "lastSeenAt", "endedAt", "playedMs", "player", "skills", "loot", "kills", "moments", "deaths",
		"otherLoot", "otherDrops", "goal", "trips", "out", "tripCount", "tripMs", "bestTripMs", "slayer", "slayerDone",
		"activity", "worthStart", "worthNow",
	};

	private static Session vorkath(long start, int kills)
	{
		Session s = new Session(start, "Zezima");
		Session.SkillLine r = new Session.SkillLine("Ranged", 13_000_000, 95);
		r.xp += 7_000L * kills;
		s.skills.put("Ranged", r);
		Session.KillLine k = new Session.KillLine("Vorkath");
		k.looted = kills;
		k.firstKc = 400;
		k.lastKc = 400 + kills - 1;
		s.kills.put("vorkath", k);
		s.loot.put(1751, new Session.Drop(1751, "Blue dragonhide", 50L * kills, 1_900));
		s.moments.add(new Moment(Moment.Kind.DROP, start + HOUR, "Draconic visage!", "From Vorkath", 11286, 3_000_000));
		s.playedMs = 2 * HOUR;
		s.lastSeenAt = start + s.playedMs;
		s.endedAt = s.lastSeenAt;
		s.activity = "Vorkath";
		s.worthStart = 1_000_000_000L;
		s.worthNow = 1_004_000_000L;
		return s;
	}

	private static JsonArray journal()
	{
		List<Session> list = new ArrayList<>();
		for (int i = 4; i >= 1; i--)
		{
			list.add(vorkath(NOW - i * DAY, 20 + i));
		}
		return PARSER.parse(GSON.toJson(list)).getAsJsonArray();
	}

	@Test
	public void theJournal()
	{
		String whole = journal().toString();
		assertEquals(4, use(whole, null, null).size());
		List<String> saves = new ArrayList<>();
		Collections.addAll(saves, WHOLE);
		for (int at : new int[]{1, whole.length() / 3, whole.length() / 2, whole.length() - 1})
		{
			saves.add(whole.substring(0, at));
		}
		for (String save : saves)
		{
			use(save, null, null);
		}
		for (String bad : BAD)
		{
			JsonArray j = journal();
			j.set(1, PARSER.parse(bad));
			assertEquals("[1] = " + bad, 3, use(j.toString(), null, null).size());
			for (String field : FIELDS)
			{
				j = journal();
				j.get(2).getAsJsonObject().add(field, PARSER.parse(bad));
				// A session with one broken field is kept, the field mended, or at worst left out alone.
				assertTrue(field + " = " + bad, use(j.toString(), null, null).size() >= 3);
			}
			for (String field : new String[]{"name", "looted", "firstKc", "lastKc", "counters", "event", "tasks"})
			{
				j = journal();
				j.get(2).getAsJsonObject().getAsJsonObject("kills").getAsJsonObject("vorkath").add(field, PARSER.parse(bad));
				assertTrue(field + " = " + bad, use(j.toString(), null, null).size() >= 3);
			}
			for (String field : new String[]{"skill", "startXp", "xp", "startLevel", "level"})
			{
				j = journal();
				j.get(2).getAsJsonObject().getAsJsonObject("skills").getAsJsonObject("Ranged").add(field, PARSER.parse(bad));
				assertTrue(field + " = " + bad, use(j.toString(), null, null).size() >= 3);
			}
			for (String field : new String[]{"kind", "at", "title", "detail", "itemId", "value"})
			{
				j = journal();
				j.get(2).getAsJsonObject().getAsJsonArray("moments").get(0).getAsJsonObject().add(field, PARSER.parse(bad));
				assertTrue(field + " = " + bad, use(j.toString(), null, null).size() >= 3);
			}
		}
		// A later version's fields, and kinds of moment this one does not know, are shrugged.
		JsonArray j = journal();
		j.get(0).getAsJsonObject().addProperty("version", 9);
		j.get(0).getAsJsonObject().add("sailing", PARSER.parse("{\"knots\":[1]}"));
		j.get(0).getAsJsonObject().getAsJsonArray("moments").get(0).getAsJsonObject().addProperty("kind", "VOYAGE");
		assertEquals(4, use(j.toString(), null, null).size());
	}

	@Test
	public void numbersNoSessionCouldHave()
	{
		long[] extremes = {Long.MAX_VALUE, Long.MIN_VALUE, -1, NOW + 1000 * DAY};
		for (String field : new String[]{"startedAt", "lastSeenAt", "endedAt", "playedMs", "otherLoot", "tripMs", "bestTripMs",
			"worthStart", "worthNow"})
		{
			for (long n : extremes)
			{
				JsonArray j = journal();
				for (int i = 0; i < j.size(); i++)
				{
					j.get(i).getAsJsonObject().addProperty(field, n);
				}
				use(j.toString(), j.get(0).toString(), null);
			}
		}
		for (String field : new String[]{"quantity", "price"})
		{
			for (long n : extremes)
			{
				JsonArray j = journal();
				j.get(1).getAsJsonObject().getAsJsonObject("loot").getAsJsonObject("1751").addProperty(field, n);
				use(j.toString(), null, null);
			}
		}
		for (String field : new String[]{"startXp", "xp"})
		{
			for (long n : extremes)
			{
				JsonArray j = journal();
				j.get(1).getAsJsonObject().getAsJsonObject("skills").getAsJsonObject("Ranged").addProperty(field, n);
				use(j.toString(), null, null);
			}
		}
	}

	@Test
	public void whateverCannotBeReadIsSetAsideBeforeItIsWrittenOver()
	{
		// A whole journal reads with nothing to set aside.
		boolean[] lost = {false};
		assertEquals(4, Journal.read(GSON, journal().toString(), () -> lost[0] = true).size());
		assertFalse(lost[0]);
		// One broken session costs only itself at reading, but the next session filed would write over
		// it: the plugin is told, so the save is kept aside first.
		JsonArray j = journal();
		j.get(1).getAsJsonObject().add("kills", PARSER.parse("\"garbage\""));
		assertEquals(4, Journal.read(GSON, j.toString(), () -> lost[0] = true).size());
		assertTrue(lost[0]);
		// So is a moment of a kind only a later version knows, which this one leaves out.
		lost[0] = false;
		j = journal();
		j.get(0).getAsJsonObject().getAsJsonArray("moments").get(0).getAsJsonObject().addProperty("kind", "VOYAGE");
		Journal.read(GSON, j.toString(), () -> lost[0] = true);
		assertTrue(lost[0]);
		// So are a later version's fields, which read without an error and would be gone at the next save.
		lost[0] = false;
		j = journal();
		j.get(2).getAsJsonObject().add("sailing", PARSER.parse("{\"knots\":[1]}"));
		assertEquals(4, Journal.read(GSON, j.toString(), () -> lost[0] = true).size());
		assertTrue(lost[0]);
		lost[0] = false;
		j = journal();
		j.get(2).getAsJsonObject().getAsJsonObject("kills").getAsJsonObject("vorkath").addProperty("perfect", 3);
		Journal.read(GSON, j.toString(), () -> lost[0] = true);
		assertTrue(lost[0]);
		// The journal this version writes, as its own fixture holds it, reads as whole.
		lost[0] = false;
		try (java.io.InputStream in = DamagedSavesTest.class.getResourceAsStream("chronicle-journal.json"))
		{
			Journal.read(GSON, new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), () -> lost[0] = true);
		}
		catch (java.io.IOException e)
		{
			throw new AssertionError(e);
		}
		assertFalse(lost[0]);
		// One copy is kept: the first, then only a save at least as full, never a stub over it.
		String full = journal().toString();
		assertTrue(Saved.setAside(null, full));
		assertFalse(Saved.setAside(full, full));
		assertFalse(Saved.setAside(full, "[{\"startedAt\":1"));
		assertTrue(Saved.setAside("[{\"startedAt\":1", full));
		assertFalse(Saved.setAside(null, "  "));
		StringBuilder huge = new StringBuilder();
		while (huge.length() <= Saved.MAX_ASIDE)
		{
			huge.append(full);
		}
		assertFalse(Saved.setAside(null, huge.toString()));
	}

	@Test
	public void aLongGrindKeepsEveryKill()
	{
		// A day of bursting one task, logged back in within the break each time: 25,000 kills on one
		// line is a real session, not damage, and once counted only 20,000.
		Session s = new Session(NOW - 20 * HOUR, "Zezima");
		Session.KillLine line = new Session.KillLine("Dust devil");
		line.looted = 25_000;
		s.kills.put("dust devil", line);
		s.playedMs = 18 * HOUR;
		s.lastSeenAt = NOW;
		assertEquals(25_000, line.count());
		Session read = Session.read(GSON, GSON.toJson(s));
		assertEquals(25_000, read.totalKills());
		// A count past any session still stops where the sums cannot overflow.
		line.looted = Integer.MAX_VALUE;
		assertEquals(Session.MAX_KILLS, Session.read(GSON, GSON.toJson(s)).totalKills());
	}

	@Test
	public void playTimeIsHeldToTheSessionsLengthButNeverLostWhenThatIsUnknown()
	{
		JsonArray j = journal();
		JsonObject a = j.get(0).getAsJsonObject();
		long start = a.get("startedAt").getAsLong();
		a.addProperty("playedMs", 900 * HOUR);
		JsonObject b = j.get(1).getAsJsonObject();
		b.remove("lastSeenAt");
		b.remove("endedAt");
		List<Session> read = Journal.read(GSON, j.toString());
		assertEquals(2 * HOUR + 60_000L, read.get(0).getPlayedMs());
		assertEquals(start, read.get(0).getStartedAt());
		// Saved without when it was last seen: its two hours stay two hours.
		assertEquals(2 * HOUR, read.get(1).getPlayedMs());
	}

	@Test
	public void theSessionSavedWhileOpen()
	{
		String journal = journal().toString();
		String good = GSON.toJson(vorkath(NOW - HOUR, 5));
		List<String> saves = new ArrayList<>();
		Collections.addAll(saves, WHOLE);
		saves.add(good.substring(0, good.length() / 2));
		saves.add("[" + good + "]");
		for (String save : saves)
		{
			// However the open session is broken, the journal beside it is still read whole.
			assertEquals(save, 4, use(journal, save, null).size());
		}
		for (String field : FIELDS)
		{
			for (String bad : BAD)
			{
				JsonObject o = PARSER.parse(good).getAsJsonObject();
				o.add(field, PARSER.parse(bad));
				assertEquals(field + " = " + bad, 4, use(journal, o.toString(), null).size());
			}
		}
	}

	@Test
	public void codexLedgersBook()
	{
		JsonObject good = new JsonObject();
		good.addProperty("total", 1_004_000_000L);
		good.addProperty("since", NOW - 30 * DAY);
		JsonArray history = new JsonArray();
		for (int i = 10; i >= 0; i--)
		{
			JsonObject p = new JsonObject();
			p.addProperty("t", NOW - i * 12 * HOUR);
			p.addProperty("v", 1_004_000_000L - i * 1_000_000L);
			history.add(p);
		}
		good.add("history", history);
		// Ledger's other fields, damaged, do not matter: only these three are read.
		good.add("days", PARSER.parse("[{\"date\":7}]"));
		String journal = journal().toString();
		assertTrue(LedgerWorth.read(GSON, good.toString()).isReady());
		for (String save : WHOLE)
		{
			use(journal, null, save);
		}
		for (String key : new String[]{"total", "since", "history"})
		{
			for (String bad : BAD)
			{
				JsonObject o = good.deepCopy();
				o.add(key, PARSER.parse(bad));
				use(journal, null, o.toString());
			}
		}
		for (String bad : BAD)
		{
			JsonObject o = good.deepCopy();
			o.getAsJsonArray("history").set(4, PARSER.parse(bad));
			// One broken point leaves the rest of the worth line.
			LedgerWorth read = LedgerWorth.read(GSON, o.toString());
			assertNotNull(bad, read);
			assertEquals(bad, 11, read.since(NOW - 6 * DAY, NOW).size());
		}
		// Points out of order, below nothing or past any account are left out, not drawn.
		JsonObject o = good.deepCopy();
		JsonArray h = o.getAsJsonArray("history");
		JsonObject wild = new JsonObject();
		wild.addProperty("t", NOW - 3 * DAY);
		wild.addProperty("v", Long.MAX_VALUE);
		h.add(wild);
		JsonObject low = new JsonObject();
		low.addProperty("t", NOW - 2 * DAY);
		low.addProperty("v", -50);
		h.add(low);
		LedgerWorth read = LedgerWorth.read(GSON, o.toString());
		for (long[] p : read.since(NOW - 6 * DAY, NOW))
		{
			assertTrue(p[1] >= 0 && p[1] < 10_000_000_000_000L);
		}
		long last = 0;
		for (long[] p : read.since(NOW - 6 * DAY, NOW))
		{
			assertTrue(p[0] >= last);
			last = p[0];
		}
		o = good.deepCopy();
		o.addProperty("total", Long.MAX_VALUE);
		LedgerWorth huge = LedgerWorth.read(GSON, o.toString());
		assertTrue(huge == null || !huge.isReady() || huge.getTotal() < 10_000_000_000_000L);
	}

	/**
	 * Reads the saves as the plugin does at login, then does with them what the plugin does: labels,
	 * restores, expires and files the open session, sums records, the week and every summary, and
	 * draws the cards and their text. Fails on any exception; returns the journal as read.
	 */
	static List<Session> use(String journalSave, String currentSave, String ledgerSave)
	{
		List<Session> journal;
		try
		{
			journal = Journal.read(GSON, journalSave);
			assertNotNull(journal);
			int read = journal.size();
			Journal.label(journal);
			Chronicle chronicle = new Chronicle();
			chronicle.restore(Session.read(GSON, currentSave));
			LedgerWorth ledger = LedgerWorth.read(GSON, ledgerSave);
			Session open = chronicle.current();
			if (open != null && ledger != null)
			{
				chronicle.worth(ledger);
			}
			Session closed = chronicle.expire(NOW, 15 * 60_000L);
			List<Session> filed = closed == null ? journal : Journal.file(journal, closed, 40);
			Journal.records(filed, chronicle.current(), NOW, ZONE);
			Week week = Week.of(filed, chronicle.current(), NOW, ZONE, ledger);
			WeekCard.render(week, true, (id, cb) -> null, name -> null, () -> { });
			Recap.week(week, true);
			for (Session s : filed)
			{
				Summary summary = Summary.of(s, Summary.State.ENDED, filed);
				Recap.text(summary, true);
				Pace.of(s, filed);
			}
			if (!filed.isEmpty())
			{
				ShareCard.render(Summary.of(filed.get(0), Summary.State.ENDED, filed), true, (id, cb) -> null, name -> null, () -> { });
			}
			// Saved again as the plugin saves it, and read back the same.
			assertEquals(filed.size(), Journal.read(GSON, GSON.toJson(filed)).size());
			journal = journal.subList(0, read);
		}
		catch (RuntimeException e)
		{
			throw new AssertionError("journal " + clip(journalSave) + " / current " + clip(currentSave) + " / ledger "
				+ clip(ledgerSave) + " threw " + e, e);
		}
		return journal;
	}

	private static String clip(String s)
	{
		return s == null ? "null" : s.length() > 100 ? s.substring(0, 100) + "..." : s;
	}
}
