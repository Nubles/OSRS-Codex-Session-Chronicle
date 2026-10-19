package com.codex.sessionchronicle.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FormatTest
{
	@Test
	public void figuresSideBySideCarryTheSamePlaces()
	{
		// The week card's worth line once read "810.9M" at one end and "897M" at the other.
		assertEquals("897.0M", Format.coins(897_000_000L));
		assertEquals("810.9M", Format.coins(810_900_000L));
		assertEquals("+2.2M", Format.xp(2_240_000));
		assertEquals("9.9M", Format.coins(9_930_000));
		assertEquals("180k", Format.coins(180_400));
		assertEquals("41k", Format.coins(41_200));
		assertEquals("8,240", Format.coins(8_240));
		assertEquals("1.0M", Format.coins(999_600));
		assertEquals("1.00B", Format.coins(999_960_000L));
		assertEquals("1.53B", Format.coins(1_534_000_000L));
		assertEquals("-450k", Format.signedCoins(-450_000));
	}

	@Test
	public void stacksUseTheSuitesLowercaseK()
	{
		assertEquals("450k", Format.stack(450_000));
		assertEquals("99999", Format.stack(99_999));
		assertEquals("12M", Format.stack(12_000_000));
	}
}
