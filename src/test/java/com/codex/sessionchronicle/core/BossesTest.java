package com.codex.sessionchronicle.core;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BossesTest
{
	@Test
	public void bossesAsTheJournalNamesThemFindTheirHiscoresIcon()
	{
		Set<String> exact = new HashSet<>();
		Set<String> keys = new HashSet<>();
		for (HiscoreSkill s : HiscoreSkill.values())
		{
			if (s.getType() == HiscoreSkillType.BOSS)
			{
				exact.add(s.getName().toLowerCase(Locale.ROOT));
				keys.add(Bosses.key(s.getName()));
			}
		}
		// Matched by the exact name these once kept the gold diamond.
		assertFalse(exact.contains("barrows"));
		assertFalse(exact.contains("the nightmare"));
		for (String journal : new String[]{"Barrows", "Barrows chest", "The Nightmare", "Dusk", "Lunar Chest", "Tombs of Amascut: Expert Mode",
			"Chambers of Xeric Challenge Mode", "Theatre of Blood: Hard Mode", "The Corrupted Gauntlet", "Vorkath", "Hueycoatl"})
		{
			assertTrue(journal, keys.contains(Bosses.key(journal)));
		}
		// Anything else stays a monster.
		assertFalse(keys.contains(Bosses.key("Goblin")));
		assertFalse(keys.contains(Bosses.key(null)));
	}
}
