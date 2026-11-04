package com.codex.sessionchronicle.ui;

import com.codex.sessionchronicle.core.Underwater;
import com.codex.sessionchronicle.core.UnderwaterCoach;
import com.codex.sessionchronicle.core.UnderwaterLog;
import com.google.gson.Gson;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** The words the underwater sections use. */
public class UnderwaterViewTest
{
	private final Gson gson = new Gson();

	@Test
	public void rewardTimeSaysWhosePace()
	{
		UnderwaterCoach c = UnderwaterCoach.of(new UnderwaterLog(), null, false, 0, 0, 70, 70, Underwater.Reward.MERFOLK_TRIDENT);
		assertEquals("About 2h 0m at the wiki's pace", UnderwaterView.RewardRow.eta(c));
		c = UnderwaterCoach.of(new UnderwaterLog(), null, false, 400, 0, 70, 70, Underwater.Reward.MERFOLK_TRIDENT);
		assertEquals("Mairin sells it underwater.", UnderwaterView.RewardRow.eta(c));
		// Two dives of twenty minutes at 240 an hour: the rest of 400 is 320 tears, 1h 20m, four dives of 80.
		UnderwaterLog log = UnderwaterLog.read(gson, "{\"dives\":[{\"ticks\":2000,\"tears\":80},{\"ticks\":2000,\"tears\":80}],"
			+ "\"total\":{\"ticks\":4000,\"tears\":160,\"count\":2}}");
		c = UnderwaterCoach.of(log, null, false, 80, 0, 70, 70, Underwater.Reward.MERFOLK_TRIDENT);
		assertEquals("About 1h 20m at your pace, 4 dives", UnderwaterView.RewardRow.eta(c));
	}

	@Test
	public void glisteningTearsCarried()
	{
		UnderwaterLog log = new UnderwaterLog();
		assertEquals("No glistening tears carried", UnderwaterView.PaceRow.carried(UnderwaterCoach.of(log, null, false, 0, 0, 99, 99, null))[0]);
		// 100 at 99 for both skills: 17,640 Agility and 64,680 Thieving.
		String[] l = UnderwaterView.PaceRow.carried(UnderwaterCoach.of(log, null, false, 0, 100, 99, 99, null));
		assertEquals("100 glistening tears carried", l[0]);
		assertEquals("Worth 18k Agility, 65k Thieving", l[1]);
		log.handIn(1, 0, 970);
		l = UnderwaterView.PaceRow.carried(UnderwaterCoach.of(log, null, false, 0, 10, 99, 99, null));
		assertEquals("Worth 9,702 Thieving", l[1]);
		l = UnderwaterView.PaceRow.carried(UnderwaterCoach.of(log, null, false, 0, 701, 99, 99, null));
		assertEquals("Over 700: Mairin discards the rest", l[1]);
	}

	@Test
	public void whatWasSearched()
	{
		UnderwaterLog log = UnderwaterLog.read(gson, "{\"dives\":[{\"clams\":3,\"chests\":1,\"others\":2,\"tears\":1}]}");
		assertEquals("3 clams, 1 chest, 2 more searched", UnderwaterView.DiveRow.searched(log.getDives().get(0)));
		assertEquals("Nothing searched yet", UnderwaterView.DiveRow.searched(new UnderwaterLog.Dive()));
	}
}
