package com.codex.sessionchronicle.core;

import java.time.LocalDateTime;
import java.time.ZoneId;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** The panel's midnight wake-up: always the next local midnight, on the days the clocks change as well. */
public class MidnightTest
{
	private static final ZoneId LONDON = ZoneId.of("Europe/London");
	private static final long HOUR = 3_600_000L;

	private static long at(int year, int month, int day, int hour, int minute)
	{
		return LocalDateTime.of(year, month, day, hour, minute).atZone(LONDON).toInstant().toEpochMilli();
	}

	@Test
	public void wakesAtTheNextMidnight()
	{
		assertEquals(90 * 60_000L, Midnight.untilNext(at(2026, 10, 19, 22, 30), LONDON));
	}

	@Test
	public void aDayOfTwentyFiveHoursStillWakesAtMidnight()
	{
		// The clocks go back at 02:00 on 25 October 2026: from the midnight before, the day has 25 hours.
		long start = at(2026, 10, 25, 0, 0);
		assertEquals(25 * HOUR, Midnight.untilNext(start, LONDON));
		assertEquals(Midnight.day(start, LONDON) + 1, Midnight.day(start + 25 * HOUR, LONDON));
	}

	@Test
	public void aDayOfTwentyThreeHoursStillWakesAtMidnight()
	{
		// The clocks go forward at 01:00 on 29 March 2026.
		long start = at(2026, 3, 29, 0, 0);
		assertEquals(23 * HOUR, Midnight.untilNext(start, LONDON));
	}

	@Test
	public void exactlyAtMidnightWaitsForTheNextOne()
	{
		assertEquals(24 * HOUR, Midnight.untilNext(at(2026, 10, 20, 0, 0), LONDON));
	}

	@Test
	public void aZoneWhoseMidnightIsSkippedWakesAtTheDaysFirstHour()
	{
		// Santiago skips from midnight to 01:00 when its clocks go forward (6 September 2026).
		ZoneId santiago = ZoneId.of("America/Santiago");
		long evening = LocalDateTime.of(2026, 9, 5, 23, 0).atZone(santiago).toInstant().toEpochMilli();
		long wake = evening + Midnight.untilNext(evening, santiago);
		assertEquals(Midnight.day(evening, santiago) + 1, Midnight.day(wake, santiago));
		assertEquals(HOUR, Midnight.untilNext(evening, santiago));
	}
}
