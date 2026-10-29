package com.codex.sessionchronicle.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;

/**
 * What the session spent of what it carried: food eaten and potions drunk, and runes, ammunition and
 * cannonballs used. It compares what is carried (inventory, worn items and the rune pouch) tick by
 * tick. Food and potions count only straight after the player chose Eat or Drink, priced as the
 * value that went (a dose, not the whole potion). Runes and ammunition count whenever they go down,
 * except while a bank, shop or trade is open or just after dropping something, when items leave
 * without being used. Deaths are left out: what is lost waits at the grave.
 */
public final class SupplyWatch
{
	/** Ticks after choosing Eat or Drink in which the item is still being consumed. */
	static final int EAT_TICKS = 2;
	/** Ticks after dropping, depositing or storing something in which nothing counts as used. */
	static final int QUIET_TICKS = 3;

	private Map<Integer, Integer> last;
	private int eatUntil = -1;
	private int quietUntil = -1;

	/** The player chose Eat or Drink on this tick. */
	public void ate(int tick)
	{
		eatUntil = tick + EAT_TICKS;
	}

	/** Items are about to leave without being used (dropped, deposited, stored, given away). */
	public void quiet(int tick)
	{
		quietUntil = tick + QUIET_TICKS;
	}

	/** Forget the last reading, after logging in, hopping or a death. */
	public void reset()
	{
		last = null;
	}

	/**
	 * Reads what is carried now and returns the coins' worth used since the last reading.
	 *
	 * @param carried item id to quantity, everything carried
	 * @param busy a bank, shop, trade or exchange is open
	 * @param spendable runes, ammunition and cannonballs: used up whenever they go down
	 * @param price coins each, by item id
	 */
	public long read(int tick, Map<Integer, Integer> carried, boolean busy, IntPredicate spendable, IntToLongFunction price)
	{
		Map<Integer, Integer> before = last;
		last = new HashMap<>(carried);
		if (before == null || busy || tick <= quietUntil)
		{
			return 0;
		}
		Set<Integer> ids = new HashSet<>(before.keySet());
		ids.addAll(carried.keySet());
		long used = 0;
		long eaten = 0;
		for (int id : ids)
		{
			long change = (long) carried.getOrDefault(id, 0) - before.getOrDefault(id, 0);
			if (change == 0)
			{
				continue;
			}
			if (spendable.test(id))
			{
				used += Math.max(0, -change) * Math.max(0, price.applyAsLong(id));
			}
			else
			{
				// A dose drunk leaves the potion one dose down: what went is the difference in value.
				eaten -= change * Math.max(0, price.applyAsLong(id));
			}
		}
		if (tick <= eatUntil && eaten > 0)
		{
			used += eaten;
		}
		return used;
	}
}
