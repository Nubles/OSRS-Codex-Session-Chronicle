package com.codex.sessionchronicle.core;

import static org.junit.Assert.assertEquals;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;
import org.junit.Test;

/** Food, potions, runes and ammunition used, read from what is carried tick by tick. */
public class SupplyWatchTest
{
	private static final int SHARK = 385;
	private static final int PRAYER_4 = 2434;
	private static final int PRAYER_3 = 139;
	private static final int DEATH_RUNE = 560;
	private static final int RUNE_ARROW = 892;
	private static final int ARROW_SHAFT = 52;

	private static final IntPredicate SPENDABLE = id -> id == DEATH_RUNE || id == RUNE_ARROW;
	private static final IntToLongFunction PRICE = id ->
	{
		switch (id)
		{
			case SHARK:
				return 800;
			case PRAYER_4:
				return 10_000;
			case PRAYER_3:
				return 7_500;
			case DEATH_RUNE:
				return 200;
			case RUNE_ARROW:
				return 60;
			default:
				return 5;
		}
	};

	private static Map<Integer, Integer> carry(int... pairs)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put(pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void foodAndDosesCountOnlyWhenEatenOrDrunk()
	{
		SupplyWatch w = new SupplyWatch();
		w.read(1, carry(SHARK, 10, PRAYER_4, 2), false, SPENDABLE, PRICE);
		w.ate(2);
		assertEquals(800, w.read(3, carry(SHARK, 9, PRAYER_4, 2), false, SPENDABLE, PRICE));
		// A dose drunk leaves the potion worth a dose less, not gone.
		w.ate(10);
		assertEquals(2_500, w.read(11, carry(SHARK, 9, PRAYER_4, 1, PRAYER_3, 1), false, SPENDABLE, PRICE));
		// A shark gone with no Eat (handed to someone, say) was not used.
		assertEquals(0, w.read(30, carry(SHARK, 8, PRAYER_4, 1, PRAYER_3, 1), false, SPENDABLE, PRICE));
	}

	@Test
	public void runesAndAmmoCountWheneverTheyGoDown()
	{
		SupplyWatch w = new SupplyWatch();
		w.read(1, carry(DEATH_RUNE, 1000, RUNE_ARROW, 500, ARROW_SHAFT, 100), false, SPENDABLE, PRICE);
		assertEquals(2 * 200 + 3 * 60, w.read(2, carry(DEATH_RUNE, 998, RUNE_ARROW, 497, ARROW_SHAFT, 100), false, SPENDABLE, PRICE));
		// Picking arrows back up is no negative cost.
		assertEquals(0, w.read(3, carry(DEATH_RUNE, 998, RUNE_ARROW, 499, ARROW_SHAFT, 100), false, SPENDABLE, PRICE));
		// Fletching shafts is skilling, not supplies.
		assertEquals(0, w.read(4, carry(DEATH_RUNE, 998, RUNE_ARROW, 499, ARROW_SHAFT, 85), false, SPENDABLE, PRICE));
	}

	@Test
	public void banksAndDropsAreNotUse()
	{
		SupplyWatch w = new SupplyWatch();
		w.read(1, carry(DEATH_RUNE, 1000), false, SPENDABLE, PRICE);
		assertEquals(0, w.read(2, carry(), true, SPENDABLE, PRICE));
		w.read(3, carry(DEATH_RUNE, 500), false, SPENDABLE, PRICE);
		w.quiet(4);
		assertEquals(0, w.read(5, carry(), false, SPENDABLE, PRICE));
		w.reset();
		assertEquals(0, w.read(20, carry(), false, SPENDABLE, PRICE));
	}

	@Test
	public void profitIsLootLessSupplies()
	{
		Chronicle c = new Chronicle();
		c.begin(0, "Zezima", 15 * 60_000L);
		c.used(1_500);
		Session s = c.current();
		s.loot.put(1, new Session.Drop(1, "Dragon bones", 2, 2_000));
		assertEquals(1_500, s.getSupplies());
		assertEquals(2_500, s.profit());
		assertEquals(2_500, Summary.of(s, Summary.State.LIVE).getProfit());
	}
}
