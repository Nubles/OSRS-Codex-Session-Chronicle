package com.codex.sessionchronicle.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class ChatParserTest
{
	@Test
	public void readsKillCounts()
	{
		ChatParser.KillCount kc = ChatParser.killCount("Your Vorkath kill count is: 1,412.");
		assertEquals("Vorkath", kc.getName());
		assertEquals(1412, kc.getCount());

		kc = ChatParser.killCount("Your completed Theatre of Blood count is: 25.");
		assertEquals("Theatre of Blood", kc.getName());
		assertEquals(25, kc.getCount());

		kc = ChatParser.killCount("Your Barrows chest count is: 300.");
		assertEquals("Barrows chest", kc.getName());

		kc = ChatParser.killCount("Your Chambers of Xeric count is: 7.");
		assertEquals("Chambers of Xeric", kc.getName());

		assertNull(ChatParser.killCount("You have a funny feeling like you're being followed."));
	}

	@Test
	public void readsMoments()
	{
		Moment m = ChatParser.moment("New item added to your collection log: Draconic visage", 1, null);
		assertEquals(Moment.Kind.COLLECTION, m.getKind());
		assertEquals("Draconic visage", m.getTitle());

		m = ChatParser.moment("You have a funny feeling like you're being followed.", 1, null);
		assertEquals(Moment.Kind.PET, m.getKind());

		m = ChatParser.moment("Congratulations, you've completed a quest: Dragon Slayer II", 1, null);
		assertEquals(Moment.Kind.QUEST, m.getKind());
		assertEquals("Dragon Slayer II", m.getTitle());

		m = ChatParser.moment("Congratulations! You have completed all of the hard tasks in the Varrock area. Speak to Toby to claim your reward.", 1, null);
		assertEquals(Moment.Kind.DIARY, m.getKind());
		assertEquals("Varrock hard diary", m.getTitle());

		m = ChatParser.moment("Congratulations, you've completed an elite combat task: Faithless Crypt Run (5 points).", 1, null);
		assertEquals(Moment.Kind.TASK, m.getKind());
		assertEquals("Faithless Crypt Run", m.getTitle());
		assertEquals("Elite combat task", m.getDetail());

		m = ChatParser.moment("You have completed 12 medium Treasure Trails.", 1, null);
		assertEquals(Moment.Kind.CLUE, m.getKind());
		assertEquals("Medium clue scroll", m.getTitle());

		m = ChatParser.moment("Fight duration: 1:04.20 (new personal best)", 1, "Vorkath");
		assertEquals(Moment.Kind.BEST, m.getKind());
		assertEquals("Vorkath in 1:04.20", m.getTitle());

		m = ChatParser.moment("Theatre of Blood total completion time: 18:30 (new personal best)", 1, null);
		assertEquals("Theatre of Blood in 18:30", m.getTitle());

		assertNull(ChatParser.moment("Welcome to Old School RuneScape.", 1, null));
	}
}
