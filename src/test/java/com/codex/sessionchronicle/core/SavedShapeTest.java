package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Codex Gear Roadmap reads the saved journal (key "journal", a list of sessions) and the live session
 * (key "current", one), read only, to learn where the player fights: each session's "startedAt" and its
 * "kills", an object of kill lines keyed by the folded name, each with "name", "looted", "firstKc",
 * "lastKc", "counters" ({counter: [first, last]}), "event" (true for loot from something opened) and "tasks"
 * ({slayer task name or "": kills looted under it}).
 * Renaming or reshaping any of them breaks it without a compile error, so the shape is pinned here.
 */
public class SavedShapeTest
{
	private static final Type JOURNAL = new TypeToken<List<Session>>()
	{
	}.getType();
	private static final List<Session.Drop> NONE = Collections.emptyList();

	@Test
	public void theKillsGearRoadmapReadsKeepTheirShape()
	{
		long t0 = 1_790_000_000_000L;
		Chronicle c = new Chronicle();
		c.begin(t0, "Zezima", 15 * 60_000L);
		// The Grotesque Guardians drop their loot as Dusk, and the game counts them by their own name.
		c.loot("Dusk", NONE, 1, false, 0, t0 + 60_000);
		c.killCount("Grotesque Guardians", 41);
		c.loot("Dusk", NONE, 1, false, 0, t0 + 120_000);
		c.killCount("Grotesque Guardians", 42);
		c.loot("Bird nest", NONE, 3, true, 0, t0 + 180_000);
		c.slayer("Abyssal demons", 50, 120, true, t0 + 200_000);
		c.loot("Abyssal demon", NONE, 1, false, 0, t0 + 240_000);
		Gson gson = new Gson();

		JsonObject live = new JsonParser().parse(gson.toJson(c.current())).getAsJsonObject();
		assertEquals(t0, live.get("startedAt").getAsLong());
		List<Session> journal = Journal.file(Collections.emptyList(), c.close(), 100);
		String saved = gson.toJson(journal, JOURNAL);
		JsonArray sessions = new JsonParser().parse(saved).getAsJsonArray();
		JsonObject session = sessions.get(0).getAsJsonObject();
		assertEquals(t0, session.get("startedAt").getAsLong());
		JsonObject kills = session.getAsJsonObject("kills");

		JsonObject guardians = kills.getAsJsonObject("grotesque guardians");
		assertEquals("named as the loot first named it; the key is the folded name", "Dusk", guardians.get("name").getAsString());
		assertEquals(2, guardians.get("looted").getAsInt());
		assertEquals(41, guardians.get("firstKc").getAsInt());
		assertEquals(42, guardians.get("lastKc").getAsInt());
		JsonArray range = guardians.getAsJsonObject("counters").getAsJsonArray("grotesque guardians");
		assertEquals(41, range.get(0).getAsInt());
		assertEquals(42, range.get(1).getAsInt());
		assertFalse(guardians.get("event").getAsBoolean());

		JsonObject nest = kills.getAsJsonObject("bird nest");
		assertTrue("opened, not killed", nest.get("event").getAsBoolean());
		assertEquals(3, nest.get("looted").getAsInt());
		assertEquals(1, kills.getAsJsonObject("abyssal demon").get("looted").getAsInt());
		// Kills by the slayer task of the moment ("" for none), so Gear Roadmap weighs each as it was fought.
		assertEquals(1, kills.getAsJsonObject("abyssal demon").getAsJsonObject("tasks").get("Abyssal demons").getAsInt());
		assertEquals(2, guardians.getAsJsonObject("tasks").get("").getAsInt());
		assertFalse("opened loot has no task", nest.has("tasks"));
	}
}
