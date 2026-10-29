package com.codex.sessionchronicle.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ChronicleTest
{
	private static final long MIN = 60_000;
	private static final long GAP = 15 * MIN;

	@Test
	public void countsExperienceFromTheFirstReading()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.xp("Slayer", 1_000_000, 80, 0);
		c.xp("Slayer", 1_050_000, 80, 1000);
		assertEquals(50_000, c.current().totalXp());
		// A lower reading is the client catching up, never lost experience.
		c.xp("Slayer", 10, 1, 2000);
		assertEquals(50_000, c.current().totalXp());
	}

	@Test
	public void levelsBecomeMoments()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.xp("Slayer", 1_000_000, 80, 0);
		List<Moment> up = c.xp("Slayer", 1_100_000, 81, 10);
		assertEquals(1, up.size());
		assertEquals("Slayer level 81", up.get(0).getTitle());
		up = c.xp("Slayer", 13_034_431, 99, 20);
		assertEquals(Moment.Kind.MAX, up.get(0).getKind());
		assertEquals(19, c.current().levelsGained());
	}

	@Test
	public void resumesWithinTheBreakAndStartsAfresh()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.xp("Attack", 100, 1, 0);
		c.xp("Attack", 500, 5, 1);
		Session first = c.current();
		c.tick(4000);
		c.pause(8000);
		assertNull(c.begin(8000 + 10 * MIN, "Zezima", GAP));
		assertSame(first, c.current());

		c.pause(8000 + 11 * MIN);
		assertNull(c.expire(8000 + 20 * MIN, GAP));
		Session closed = c.expire(8000 + 30 * MIN, GAP);
		assertSame(first, closed);
		assertEquals(first.getLastSeenAt(), closed.getEndedAt());
		assertNull(c.current());
	}

	@Test
	public void anotherCharacterStartsANewSession()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.loot("Cow", Collections.singletonList(new Session.Drop(526, "Bones", 1, 100)), 1, 0, 0);
		c.pause(MIN);
		Session closed = c.begin(2 * MIN, "Lynx Titan", GAP);
		assertNotNull(closed);
		assertEquals("Lynx Titan", c.current().getPlayer());
	}

	@Test
	public void playTimeIgnoresFrozenClients()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.tick(600);
		c.tick(1200);
		c.tick(60_000);
		assertEquals(1200 + Chronicle.MAX_TICK_GAP_MS, c.current().getPlayedMs());
	}

	@Test
	public void emptySessionsAreNotKept()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.tick(600);
		assertNull(c.close());
	}

	@Test
	public void lootStacksAndValuableDropsStandOut()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		List<Moment> m = c.loot("Vorkath", Arrays.asList(
			new Session.Drop(536, "Dragon bones", 2, 2_500),
			new Session.Drop(11286, "Draconic visage", 1, 4_200_000)), 1, 500_000, 5);
		c.loot("Vorkath", Collections.singletonList(new Session.Drop(536, "Dragon bones", 2, 2_600)), 1, 500_000, 10);
		assertEquals(1, m.size());
		assertEquals("Draconic visage", m.get(0).getTitle());
		assertEquals(4, c.current().loot.get(536).getQuantity());
		assertEquals(4 * 2_600 + 4_200_000, c.current().lootValue());
		assertEquals(2, c.current().totalKills());
	}

	@Test
	public void killCountsAndLootAgreeOnKills()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		for (int kc = 100; kc <= 104; kc++)
		{
			c.loot("Vorkath", Collections.emptyList(), 1, 0, kc);
			c.killCount("Vorkath", kc);
		}
		c.killCount("Barrows chest", 50);
		c.loot("Barrows", Collections.emptyList(), 1, 0, 0);
		c.killCount("Barrows chest", 51);
		Summary s = Summary.of(c.current(), Summary.State.LIVE);
		assertEquals(2, s.getKillLines().size());
		assertEquals(5, s.getKillLines().get(0).count());
		assertEquals(104, s.getKillLines().get(0).getKillCount());
		assertEquals(2, s.getKillLines().get(1).count());
		assertEquals("5 Vorkath kills", s.headline());
	}

	@Test
	public void petsLeadTheHeadline()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.xp("Slayer", 1_000_000, 80, 0);
		c.xp("Slayer", 1_200_000, 82, 0);
		assertEquals("2 Slayer levels", Summary.of(c.current(), Summary.State.LIVE).headline());
		c.moment(ChatParser.moment("You have a funny feeling like you're being followed.", 5, null));
		Summary s = Summary.of(c.current(), Summary.State.LIVE);
		assertEquals("A pet followed you home", s.headline());
		assertEquals(Moment.Kind.PET, s.getHighlights().get(0).getKind());
		assertTrue(s.getMoments().get(0).getAt() <= s.getMoments().get(s.getMoments().size() - 1).getAt());
	}

	@Test
	public void ratesWaitForEnoughTime()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.xp("Mining", 0, 1, 0);
		c.xp("Mining", 10_000, 30, 0);
		for (long t = 600; t <= 2 * MIN; t += 600)
		{
			c.tick(t);
		}
		assertEquals(0, Summary.of(c.current(), Summary.State.LIVE).xpPerHour());
		for (long t = 2 * MIN; t <= 30 * MIN; t += 600)
		{
			c.tick(t);
		}
		assertEquals(20_000, Summary.of(c.current(), Summary.State.LIVE).xpPerHour(), 100);
	}

	@Test
	public void bossNamesFoldTogether()
	{
		assertEquals(Chronicle.key("Nightmare"), Chronicle.key("The Nightmare"));
		assertEquals(Chronicle.key("Tombs of Amascut"), Chronicle.key("Tombs of Amascut: Expert Mode"));
		assertEquals(Chronicle.key("Theatre of Blood"), Chronicle.key("Theatre of Blood: Hard Mode"));
		assertEquals(Chronicle.key("Grotesque Guardians"), Chronicle.key("Dusk"));
		assertEquals(Chronicle.key("Corrupted Gauntlet"), Chronicle.key("The Corrupted Gauntlet"));
		assertEquals(Chronicle.key("Barrows"), Chronicle.key("Barrows chest"));

		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		for (int kc = 10; kc <= 12; kc++)
		{
			c.loot("Dusk", Collections.emptyList(), 1, 0, kc);
			c.killCount("Grotesque Guardians", kc);
		}
		assertEquals(3, c.current().totalKills());
	}

	@Test
	public void aLogSlotAndItsDropAreOneMoment()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.loot("Vorkath", Collections.singletonList(new Session.Drop(11286, "Draconic visage", 1, 4_200_000)), 1, 500_000, 1000);
		c.moment(ChatParser.moment("New item added to your collection log: Draconic visage", 30_000, null));
		assertEquals(1, c.current().moments.size());
		Moment m = c.current().moments.get(0);
		assertEquals(Moment.Kind.COLLECTION, m.getKind());
		assertEquals(11286, m.getItemId());
		assertEquals("New log slot, Vorkath, 4.2M", m.getDetail());

		// A raid announces the slot before the chest's loot arrives.
		c.moment(ChatParser.moment("New item added to your collection log: Twisted bow", 60_000, null));
		c.loot("Chambers of Xeric", Collections.singletonList(new Session.Drop(20997, "Twisted bow", 1, 1_500_000_000)), 1, 500_000, 61_000);
		assertEquals(2, c.current().moments.size());
		assertEquals(20997, c.current().moments.get(1).getItemId());

		// A 99 still leads the highlights over even the richest new slot.
		c.xp("Ranged", 13_000_000, 98, 0);
		c.xp("Ranged", 13_034_431, 99, 62_000);
		assertEquals(Moment.Kind.MAX, Summary.of(c.current(), Summary.State.LIVE).getHighlights().get(0).getKind());

		// Long after, the same name is a new moment.
		c.moment(ChatParser.moment("New item added to your collection log: Draconic visage", 60 * MIN, null));
		assertEquals(4, c.current().moments.size());
	}

	@Test
	public void experienceGainedAwayIsNotThisSessions()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.xp("Fishing", 1_000_000, 80, 0);
		c.xp("Fishing", 1_100_000, 81, 1000);
		c.pause(MIN);
		c.begin(5 * MIN, "Zezima", GAP);
		// Fished on mobile during the break: 50k more and a level.
		c.resume("Fishing", 1_150_000, 82);
		c.resume("Mining", 0, 1);
		Session.SkillLine line = c.current().skills.get("Fishing");
		assertEquals(100_000, line.gained());
		assertEquals(1, line.getLevel() - line.getStartLevel());
		c.xp("Fishing", 1_160_000, 82, 6 * MIN);
		assertEquals(110_000, c.current().totalXp());
		// A reading the client was not ready for changes nothing.
		c.resume("Fishing", 0, 1);
		assertEquals(110_000, c.current().totalXp());
	}

	@Test
	public void deathsNameTheKiller()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.death(5, "Vorkath");
		c.death(6, null);
		assertEquals(2, c.current().getDeaths());
		assertEquals("Killed by Vorkath", c.current().moments.get(0).getDetail());
		assertEquals("Back to the respawn point", c.current().moments.get(1).getDetail());
	}

	@Test
	public void headlinesTellTheSession()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.xp("Mining", 1_000_000, 80, 0);
		c.xp("Mining", 1_050_000, 80, 0);
		assertEquals("+50k Mining xp", Summary.of(c.current(), Summary.State.LIVE).headline());
		for (long t = 600; t <= 30 * MIN; t += 600)
		{
			c.tick(t);
		}
		assertEquals("Mining at 100k/hr", Summary.of(c.current(), Summary.State.LIVE).headline());
		c.xp("Mining", 1_100_000, 81, 0);
		assertEquals("Mining level 81", Summary.of(c.current(), Summary.State.LIVE).headline());
		c.xp("Slayer", 2_000_000, 85, 0);
		c.xp("Slayer", 2_200_000, 86, 0);
		for (int i = 0; i < 12; i++)
		{
			c.loot("Abyssal demon", Collections.emptyList(), 1, 0, 0);
		}
		assertEquals("12 Abyssal demon kills", Summary.of(c.current(), Summary.State.LIVE).headline());
		c.moment(ChatParser.moment("New item added to your collection log: Abyssal whip", 1, null));
		assertEquals("Abyssal whip!", Summary.of(c.current(), Summary.State.LIVE).headline());
		c.moment(ChatParser.moment("New item added to your collection log: Abyssal head", 2, null));
		assertEquals("2 new collection log slots", Summary.of(c.current(), Summary.State.LIVE).headline());
	}

	@Test
	public void signatureFollowsWhatIsShown()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.xp("Mining", 1_000_000, 80, 0);
		String before = Summary.signature(Summary.of(c.current(), Summary.State.LIVE));
		assertEquals(before, Summary.signature(Summary.of(c.current(), Summary.State.LIVE)));
		c.xp("Mining", 1_000_050, 80, 0);
		assertTrue(!before.equals(Summary.signature(Summary.of(c.current(), Summary.State.LIVE))));
		assertEquals("none", Summary.signature(null));
	}

	@Test
	public void countsSayWhatTheyCount()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.loot("Clue Scroll (Medium)", Collections.emptyList(), 12, 0, 0);
		c.loot("Chambers of Xeric", Collections.emptyList(), 1, 0, 0);
		c.killCount("Chambers of Xeric", 40);
		c.killCount("Chambers of Xeric", 42);
		List<Session.KillLine> lines = Summary.of(c.current(), Summary.State.LIVE).getKillLines();
		assertEquals("12 medium clues", Summary.kills(lines.get(0)));
		assertEquals("3 Chambers of Xeric clears", Summary.kills(lines.get(1)));
		assertEquals("3 Chambers of Xeric clears", Summary.of(c.current(), Summary.State.LIVE).headline());
	}

	@Test
	public void raidModesKeepTheirOwnKillCounts()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		// One entry raid and one expert raid: two clears, not the gap between the two counters.
		c.loot("Tombs of Amascut", Collections.emptyList(), 1, 0, 0);
		c.killCount("Tombs of Amascut: Entry Mode", 500);
		c.loot("Tombs of Amascut", Collections.emptyList(), 1, 0, 0);
		c.killCount("Tombs of Amascut: Expert Mode", 21);
		assertEquals(1, c.current().kills.size());
		assertEquals(2, c.current().totalKills());
		c.killCount("Tombs of Amascut: Expert Mode", 22);
		assertEquals(3, c.current().totalKills());
	}

	@Test
	public void challengeModeIsOneClearWithItsLoot()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		// The Loot Tracker names a challenge mode chest "Chambers of Xeric"; the game counts it apart.
		c.killCount("Chambers of Xeric Challenge Mode", 30);
		c.loot("Chambers of Xeric", Collections.emptyList(), 1, 0, 0);
		assertEquals(1, c.current().kills.size());
		assertEquals(1, c.current().totalKills());
	}

	@Test
	public void oldSavedKillCountsStillCount()
	{
		Session.KillLine line = new Session.KillLine("Vorkath");
		line.firstKc = 100;
		line.lastKc = 104;
		assertEquals(5, line.count());
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.current().kills.put("vorkath", line);
		c.killCount("Vorkath", 105);
		assertEquals(6, c.current().totalKills());
		assertEquals(105, line.getKillCount());
	}

	@Test
	public void skillingEventsDoNotOutrankTheSkill()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.xp("Woodcutting", 1_000_000, 80, 0);
		c.xp("Woodcutting", 1_100_000, 81, 0);
		for (int i = 0; i < 12; i++)
		{
			c.loot("Bird nest", Collections.emptyList(), 1, 0, 0);
		}
		assertEquals("Woodcutting level 81", Summary.of(c.current(), Summary.State.LIVE).headline());
	}

	@Test
	public void lapsAndMinigamesAreRoundsNotKills()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.killCount("Seers' Village Rooftop lap", 300);
		c.killCount("Seers' Village Rooftop lap", 311);
		c.killCount("Guardians of the Rift", 1204);
		c.round(Chronicle.GIANTS_FOUNDRY);
		c.round(Chronicle.GIANTS_FOUNDRY);
		c.killCount("Vorkath", 50);

		assertEquals("only Vorkath is a kill", 1, c.current().totalKills());
		Summary s = Summary.of(c.current(), Summary.State.LIVE);
		assertEquals(1, s.getKillLines().size());
		assertEquals(3, s.getRoundLines().size());
		assertEquals("Seers' Village Rooftop lap", s.getRoundLines().get(0).getName());
		assertEquals(12, s.getRoundLines().get(0).count());
		Session.KillLine foundry = s.getRoundLines().get(1);
		assertEquals("Giants' Foundry", foundry.getName());
		assertEquals(2, foundry.count());
		assertTrue(s.getOpenedLines().isEmpty());
	}

	@Test
	public void roundsGetGoalsPaceAndAHeadline()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.tick(0);
		for (int i = 0; i < 12; i++)
		{
			c.round(Chronicle.GIANTS_FOUNDRY);
		}
		c.current().playedMs = 60 * MIN;
		assertEquals(12, Goal.progress(c.current(), Goal.Kind.ROUNDS));
		assertEquals("12 Giants' Foundry swords", Summary.of(c.current(), Summary.State.LIVE).headline());
		Pace.Line pace = null;
		for (Pace.Line line : Pace.of(c.current(), Collections.emptyList()))
		{
			if (line.getKind() == Pace.Kind.ROUNDS)
			{
				pace = line;
			}
		}
		assertNotNull(pace);
		assertEquals(12, pace.getRate());
		assertEquals("swords", Summary.noun(pace.getName()));
	}

	@Test
	public void lapsReadAsLaps()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", GAP);
		c.killCount("Seers' Village Rooftop lap", 100);
		c.killCount("Seers' Village Rooftop lap", 147);
		assertEquals("48 Seers' Village Rooftop laps", Summary.of(c.current(), Summary.State.LIVE).headline());
		assertEquals("laps", Summary.noun("Seers' Village Rooftop lap"));
	}
}
