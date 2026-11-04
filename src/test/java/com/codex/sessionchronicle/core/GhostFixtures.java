package com.codex.sessionchronicle.core;

import java.util.Collections;

/** Races for the ui tests, built through the chronicle as the plugin builds them. */
public final class GhostFixtures
{
	private GhostFixtures()
	{
	}

	/** Ten minutes of Mining at so much a minute, raced against an hour at 1,000 a minute filed before tracks. */
	public static Ghost mining(long perMinute)
	{
		long t0 = 1_790_000_000_000L;
		Session old = PaceTest.skill(t0, "Mining", 60_000, 3_600_000L);
		old.track = null;
		long[] minutes = new long[10];
		java.util.Arrays.fill(minutes, perMinute);
		return Ghost.of(GhostTest.played(t0 + 86_400_000L, "Mining", minutes), Collections.singletonList(old));
	}
}
