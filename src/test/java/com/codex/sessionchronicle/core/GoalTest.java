package com.codex.sessionchronicle.core;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GoalTest
{
	private static final long MIN = 60_000L;
	private static final long HOUR = 60 * MIN;
	private static final long DAY = 24 * HOUR;

	@Test
	public void goalIsReachedOnceAndBecomesAMoment()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", 15 * MIN);
		c.xp("Mining", 1_000_000, 70, 0);
		c.goal(new Goal(Goal.Kind.XP, 100_000, 0));
		c.tick(600);
		c.xp("Mining", 1_060_000, 70, 600);
		assertNull(c.checkGoal(600));
		c.tick(1_200);
		c.xp("Mining", 1_100_000, 70, 1_200);
		Moment m = c.checkGoal(1_200);
		assertNotNull(m);
		assertEquals(Moment.Kind.GOAL, m.getKind());
		assertEquals("Goal reached: 100k xp", m.getTitle());
		assertTrue(c.current().getGoal().isReached());
		assertNull("told once", c.checkGoal(1_800));
	}

	@Test
	public void goalProgressRateAndEta()
	{
		Session s = PaceTest.skill(0, "Mining", 200_000, 30 * MIN);
		s.goal = new Goal(Goal.Kind.XP, 300_000, 0);
		Summary summary = Summary.of(s, Summary.State.LIVE, Collections.emptyList());
		assertEquals(100_000, summary.getGoalProgress());
		assertEquals(200_000, summary.goalPerHour());
		assertEquals(HOUR, summary.goalEtaMs());
		assertTrue("presets are only offered without a goal", summary.getPresets().isEmpty());

		s.goal = new Goal(Goal.Kind.TIME, 2 * HOUR, 0);
		assertEquals(90 * MIN, Summary.of(s, Summary.State.LIVE, Collections.emptyList()).goalEtaMs());
		Session young = PaceTest.skill(0, "Mining", 200_000, 2 * MIN);
		young.goal = new Goal(Goal.Kind.XP, 300_000, 0);
		assertEquals(-1, Summary.of(young, Summary.State.LIVE, Collections.emptyList()).goalEtaMs());
	}

	@Test
	public void presetsComeFromTheJournalAndStayAhead()
	{
		List<Session> journal = Arrays.asList(
			PaceTest.skill(DAY, "Mining", 230_000, HOUR),
			PaceTest.skill(2 * DAY, "Mining", 410_000, HOUR),
			PaceTest.skill(3 * DAY, "Mining", 1_234_000, HOUR));
		Session now = PaceTest.skill(4 * DAY, "Mining", 200_000, 30 * MIN);
		List<Goal.Preset> presets = Goal.presets(Goal.Kind.XP, now, journal);
		assertEquals(3, presets.size());
		assertEquals("Usual", presets.get(0).getLabel());
		assertEquals(410_000, presets.get(0).getValue());
		assertEquals("Best", presets.get(1).getLabel());
		assertEquals(1_300_000, presets.get(1).getValue());
		assertEquals("Beat best", presets.get(2).getLabel());
		assertEquals(1_500_000, presets.get(2).getValue());

		// Already past the usual: only goals still ahead, topped up with round numbers.
		Session far = PaceTest.skill(4 * DAY, "Mining", 1_000_000, HOUR);
		List<Goal.Preset> ahead = Goal.presets(Goal.Kind.XP, far, journal);
		assertEquals(3, ahead.size());
		for (Goal.Preset p : ahead)
		{
			assertTrue(p.getValue() > 1_000_000);
		}
	}

	@Test
	public void roundNumbersWithoutHistory()
	{
		List<Goal.Preset> time = Goal.presets(Goal.Kind.TIME, PaceTest.skill(0, "Mining", 1, 45 * MIN), new ArrayList<>());
		assertEquals(3, time.size());
		assertEquals(HOUR, time.get(0).getValue());
		assertNull(time.get(0).getLabel());
		assertEquals(Arrays.asList(10L, 25L, 50L), values(Goal.presets(Goal.Kind.KILLS, null, Collections.emptyList())));
	}

	private static List<Long> values(List<Goal.Preset> presets)
	{
		List<Long> v = new ArrayList<>();
		for (Goal.Preset p : presets)
		{
			v.add(p.getValue());
		}
		return v;
	}

	@Test
	public void roundsToTwoFigures()
	{
		assertEquals(1_300_000, Goal.round(Goal.Kind.XP, 1_234_000));
		assertEquals(99, Goal.round(Goal.Kind.KILLS, 99));
		assertEquals(130, Goal.round(Goal.Kind.KILLS, 123));
		assertEquals(30 * MIN, Goal.round(Goal.Kind.TIME, 17 * MIN));
	}

	@Test
	public void describes()
	{
		assertEquals("500k xp", Goal.describe(Goal.Kind.XP, 500_000));
		assertEquals("5M loot", Goal.describe(Goal.Kind.LOOT, 5_000_000));
		assertEquals("1 kill", Goal.describe(Goal.Kind.KILLS, 1));
		assertEquals("1h 30m played", Goal.describe(Goal.Kind.TIME, 90 * MIN));
		assertEquals("45m played", Goal.describe(Goal.Kind.TIME, 45 * MIN));
	}

	@Test
	public void goalSavesWithTheSessionAndOldSavesHaveNone()
	{
		Gson gson = new Gson();
		Session s = PaceTest.skill(0, "Mining", 200_000, HOUR);
		s.goal = new Goal(Goal.Kind.LOOT, 5_000_000, 7);
		Session back = gson.fromJson(gson.toJson(s), Session.class);
		assertEquals(Goal.Kind.LOOT, back.getGoal().getKind());
		assertEquals(5_000_000, back.getGoal().getTarget());
		assertEquals(5_000_000, Journal.compact(back).getGoal().getTarget());
		assertNull(gson.fromJson("{\"startedAt\":5}", Session.class).getGoal());
		assertNull("a goal with nothing to reach is dropped", gson.fromJson("{\"goal\":{\"kind\":\"XP\"}}", Session.class).getGoal());
		assertFalse(Summary.of(back, Summary.State.ENDED, Collections.emptyList()).getGoal().isReached());
	}
}
