package com.codex.sessionchronicle.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** When the local day turns over, from the zone's own calendar so clock changes are kept. */
public final class Midnight
{
	/** Waking a moment after midnight, so the new day is certainly the one read. */
	public static final long SLACK_MS = 1_000;

	private Midnight()
	{
	}

	/** Milliseconds from a moment to the start of the next local day; at least 1. */
	public static long untilNext(long now, ZoneId zone)
	{
		ZonedDateTime next = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone);
		return Math.max(1, next.toInstant().toEpochMilli() - now);
	}

	/** The local day of a moment, as an epoch day. */
	public static long day(long now, ZoneId zone)
	{
		return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay();
	}
}
