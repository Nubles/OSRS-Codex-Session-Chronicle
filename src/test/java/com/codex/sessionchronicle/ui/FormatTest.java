package com.codex.sessionchronicle.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FormatTest
{
	@Test
	public void amountsFollowTheSuiteRule()
	{
		// In full below 10,000; whole k below a million; M to one decimal; B to two.
		assertEquals("8,240", Format.amount(8_240));
		assertEquals("10k", Format.amount(10_000));
		assertEquals("41k", Format.amount(41_200));
		assertEquals("96k", Format.amount(95_700));
		assertEquals("412k", Format.amount(412_000));
		assertEquals("1.2M", Format.amount(1_240_000));
		assertEquals("2.4M", Format.amount(2_370_000));
		assertEquals("26.0M", Format.amount(26_000_000));
		assertEquals("98.7M", Format.amount(98_700_000));
		assertEquals("812.4M", Format.amount(812_400_000));
		assertEquals("2.10B", Format.amount(2_100_000_000L));
		// Rounding that reaches the next unit is written in it.
		assertEquals("100k", Format.amount(99_960));
		assertEquals("1.0M", Format.amount(999_600));
		assertEquals("999k", Format.amount(999_499));
		assertEquals("1.00B", Format.amount(999_960_000));
		assertEquals("+880k", Format.signedCoins(880_000));
		// Experience an hour follows it too.
		assertEquals("1.2M/hr", Format.rate(1_240_000));
		assertEquals("96k/hr", Format.rate(95_700));
	}

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
